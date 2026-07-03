package com.jishi.collection

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = LocalDatabase(context.applicationContext)
    private val preferences = appContext.getSharedPreferences("ai_classification", Context.MODE_PRIVATE)

    suspend fun loadHome(): HomeData = withContext(Dispatchers.IO) {
        HomeData(
            categories = db.categorySummaries(),
            suggestions = db.pendingSuggestions(),
            searchableNotes = db.searchableNotes(),
        )
    }

    suspend fun notesForCategory(categoryId: String): List<NoteCard> = withContext(Dispatchers.IO) {
        db.notesForCategory(categoryId)
    }

    // 同步只负责入库，不做任何分类
    suspend fun syncDemoNotes(): SyncResult = withContext(Dispatchers.IO) {
        db.syncNotes(sampleNotes())
    }

    suspend fun syncFromJson(json: String): SyncResult = withContext(Dispatchers.IO) {
        val payload = parseSyncPayload(json)
        db.syncNotes(notes = payload.notes, pageState = payload.pageState)
    }

    suspend fun completeSync(json: String): SyncCompleteResult = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        val success = root.optBoolean("success", false)
        val accountUserId = root.optString("userId").ifBlank { "current" }
        val message = root.optString("message").ifBlank {
            if (success) "同步完成" else "同步失败"
        }
        if (success) {
            val idsArray = root.optJSONArray("ids") ?: JSONArray()
            val ids = buildSet {
                for (index in 0 until idsArray.length()) {
                    val id = idsArray.optString(index)
                    if (id.isNotBlank()) add(id)
                }
            }
            val removed = db.reconcileCompletedSync(accountUserId, ids)
            SyncCompleteResult(success = true, message = message, total = ids.size, removed = removed)
        } else {
            db.failSync(accountUserId, message)
            SyncCompleteResult(success = false, message = message, total = 0)
        }
    }

    fun loadAiSettings(): AiClassificationSettings {
        val toleranceName = preferences.getString(KEY_TOLERANCE, AiClassificationTolerance.BALANCED.name).orEmpty()
        val tolerance = AiClassificationTolerance.entries.firstOrNull { it.name == toleranceName }
            ?: AiClassificationTolerance.BALANCED
        return AiClassificationSettings(
            deepSeekApiKey = preferences.getString(KEY_DEEPSEEK_API_KEY, "").orEmpty(),
            embeddingApiKey = preferences.getString(KEY_EMBEDDING_API_KEY, "").orEmpty(),
            embeddingBaseUrl = preferences.getString(KEY_EMBEDDING_BASE_URL, DEFAULT_EMBEDDING_BASE_URL).orEmpty()
                .ifBlank { DEFAULT_EMBEDDING_BASE_URL },
            tolerance = tolerance,
            customSplitThreshold = preferences.getInt(KEY_SPLIT_THRESHOLD, tolerance.splitThreshold),
            customMatchThreshold = Double.fromBits(
                preferences.getLong(KEY_MATCH_THRESHOLD, tolerance.matchThreshold.toBits()),
            ),
        )
    }

    fun saveAiSettings(settings: AiClassificationSettings) {
        preferences.edit()
            .putString(KEY_DEEPSEEK_API_KEY, settings.deepSeekApiKey.trim())
            .putString(KEY_EMBEDDING_API_KEY, settings.embeddingApiKey.trim())
            .putString(KEY_EMBEDDING_BASE_URL, settings.embeddingBaseUrl.trim().ifBlank { DEFAULT_EMBEDDING_BASE_URL })
            .putString(KEY_TOLERANCE, settings.tolerance.name)
            .putInt(KEY_SPLIT_THRESHOLD, settings.customSplitThreshold.coerceIn(10, 200))
            .putLong(KEY_MATCH_THRESHOLD, settings.customMatchThreshold.coerceIn(0.1, 0.95).toBits())
            .apply()
    }

    suspend fun runRuleClassification(noteIds: Set<String>? = null): Int = withContext(Dispatchers.IO) {
        db.rerunRuleClassification(noteIds)
    }

    /**
     * 分类按钮触发：把待整理/未分类笔记按固定粗分类归类，然后对超过阈值且有裂变约束的粗分类做裂变。
     */
    suspend fun runCoarseClassification(): AiClassificationRunResult = withContext(Dispatchers.IO) {
        val settings = loadAiSettings()
        if (!settings.smartClassificationEnabled) {
            logAi("粗分类跳过：未配置 DeepSeek API Key")
            return@withContext AiClassificationRunResult(skippedReason = "未配置 API Key，已使用固定分类词分类")
        }
        db.ensureCoarseCategories(CoarseTaxonomy.categories)
        val notes = db.aiNotesForCoarseClassification()
        logAi("粗分类开始：待分类笔记=${notes.size}")
        var matched = 0
        if (notes.isNotEmpty()) {
            ensureKeywordCache(settings, notes)
            db.aiNotesForCoarseClassification().chunked(COARSE_BATCH_SIZE).forEach { batch ->
                runCatching {
                    val assignments = requestCoarseAssignments(settings, batch)
                    matched += db.assignNotesToCategories(assignments)
                }.onFailure {
                    Log.w(AI_LOG_TAG, "粗分类批次失败：batch=${batch.size}", it)
                }
            }
        }
        val split = runFission(settings)
        logAi("粗分类结束：matched=$matched，split=$split")
        AiClassificationRunResult(matched = matched, splitCategories = split)
    }

    /**
     * 同步按钮的增量路径：已有分类时，把新增笔记的摘要与现有分类做 embedding 相似度匹配。
     * 低于阈值的笔记留在待整理，等下次点击分类按钮。
     */
    suspend fun runIncrementalEmbeddingMatch(noteIds: Set<String>): AiClassificationRunResult = withContext(Dispatchers.IO) {
        val settings = loadAiSettings()
        if (!settings.embeddingEnabled) {
            logAi("增量匹配跳过：未配置 Embedding Key")
            return@withContext AiClassificationRunResult(skippedReason = "未配置 Embedding Key，新增笔记已放入待整理")
        }
        val categories = db.aiCategoryProfiles()
        if (categories.isEmpty()) {
            logAi("增量匹配跳过：暂无分类")
            return@withContext AiClassificationRunResult(skippedReason = "暂无分类，请先点击“分类”整理收藏")
        }
        val notes = db.aiNotesForCoarseClassification(noteIds)
        if (notes.isEmpty()) return@withContext AiClassificationRunResult()
        logAi("增量匹配开始：notes=${notes.size}，categories=${categories.size}")
        var matched = 0
        notes.chunked(EMBEDDING_NOTE_BATCH).forEach { batch ->
            runCatching {
                matched += matchNotesByEmbedding(settings, batch, categories)
            }.onFailure {
                Log.w(AI_LOG_TAG, "增量匹配批次失败：batch=${batch.size}", it)
            }
        }
        logAi("增量匹配结束：matched=$matched，unmatched=${notes.size - matched}")
        AiClassificationRunResult(matched = matched)
    }

    suspend fun acceptSuggestion(id: String, name: String) = withContext(Dispatchers.IO) {
        db.acceptSuggestion(id, name)
    }

    suspend fun dismissSuggestion(id: String) = withContext(Dispatchers.IO) {
        db.dismissSuggestion(id)
    }

    suspend fun renameCategory(categoryId: String, name: String) = withContext(Dispatchers.IO) {
        db.renameCategory(categoryId, name)
    }

    suspend fun clearMetadataCache() = withContext(Dispatchers.IO) {
        db.clearMetadataCache()
    }

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        db.clearAllLocalData()
    }

    suspend fun clearInvalidNotes(): Int = withContext(Dispatchers.IO) {
        db.clearInvalidNotes()
    }

    fun hasRednoteLoginState(): Boolean {
        val hosts = listOf(
            "https://www.xiaohongshu.com",
            "https://edith.xiaohongshu.com",
            "https://www.rednote.com",
        )
        return hosts
            .asSequence()
            .mapNotNull { CookieManager.getInstance().getCookie(it) }
            .flatMap { it.split(";").asSequence() }
            .map { it.trim() }
            .any { cookie ->
                AUTH_COOKIE_NAMES.any { name ->
                    cookie.startsWith("$name=") && cookie.length > "$name=".length
                }
            }
    }

    fun clearWebLoginState() {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().removeSessionCookies {
                CookieManager.getInstance().flush()
            }
        }
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(appContext).clearHttpAuthUsernamePassword()
        WebViewDatabase.getInstance(appContext).clearFormData()
    }

    private fun parseSyncPayload(json: String): SyncPayload {
        val root = JSONObject(json)
        val sync = root.optJSONObject("sync")
        val pageState = sync?.let {
            SyncPageState(
                accountUserId = it.optString("accountUserId").ifBlank { "current" },
                cursor = it.optString("cursor"),
                hasMore = it.optBoolean("hasMore", false),
            )
        }
        val notesArray = when {
            root.has("notes") -> root.getJSONArray("notes")
            root.optJSONObject("data")?.has("notes") == true -> root.getJSONObject("data").getJSONArray("notes")
            else -> JSONArray()
        }

        val notes = buildList {
            for (index in 0 until notesArray.length()) {
                val item = notesArray.getJSONObject(index)
                val noteCard = item.optJSONObject("note_card")
                val source = noteCard ?: item
                val id = item.optString("id")
                    .ifBlank { item.optString("note_id") }
                    .ifBlank { source.optString("note_id") }
                    .ifBlank { item.optString("rednoteId") }
                if (id.isBlank()) continue
                add(
                    SyncedNote(
                        rednoteId = id,
                        title = source.optString("display_title")
                            .ifBlank { source.optString("title") }
                            .ifBlank { item.optString("display_title") }
                            .ifBlank { "未命名收藏" },
                        desc = source.optString("desc")
                            .ifBlank { source.optString("display_title") }
                            .ifBlank { item.optString("desc") },
                        authorName = source.optJSONObject("user")?.optString("nickname")
                            ?: item.optJSONObject("user")?.optString("nickname")
                            ?: item.optString("authorName"),
                        coverUrl = extractCoverUrl(source)
                            .ifBlank { extractCoverUrl(item) }
                            .ifBlank { normalizeImageUrl(item.optString("coverUrl")) },
                        noteUrl = item.optString("noteUrl").ifBlank { "https://www.xiaohongshu.com/explore/$id" },
                    ),
                )
            }
        }
        return SyncPayload(notes, pageState)
    }

    private fun extractCoverUrl(source: JSONObject): String {
        return source.optJSONObject("cover")?.let(::extractImageUrl).orEmpty()
            .ifBlank { source.optJSONArray("image_list")?.optJSONObject(0)?.let(::extractImageUrl).orEmpty() }
            .ifBlank { source.optJSONArray("images_list")?.optJSONObject(0)?.let(::extractImageUrl).orEmpty() }
            .ifBlank { source.optJSONArray("images")?.optJSONObject(0)?.let(::extractImageUrl).orEmpty() }
    }

    private fun extractImageUrl(image: JSONObject): String {
        val directUrl = directImageUrl(image)
        if (directUrl.isNotBlank()) return directUrl

        val infoList = image.optJSONArray("info_list")
            ?: image.optJSONArray("url_list")
            ?: image.optJSONArray("urls")
        if (infoList != null) {
            for (index in 0 until infoList.length()) {
                val candidate = when (val item = infoList.opt(index)) {
                    is JSONObject -> directImageUrl(item)
                    is String -> normalizeImageUrl(item)
                    else -> ""
                }
                if (candidate.isNotBlank()) return candidate
            }
        }

        return ""
    }

    private fun directImageUrl(image: JSONObject): String {
        return listOf(
            "url_default",
            "url_pre",
            "url",
            "original",
            "thumbnail",
            "trace_url",
            "file_id",
        ).firstNotNullOfOrNull { key ->
            normalizeImageUrl(image.optString(key)).takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun normalizeImageUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") -> value.replaceFirst("http://", "https://")
            value.startsWith("https://") -> value
            else -> ""
        }
    }

    private fun sampleNotes(): List<SyncedNote> {
        return emptyList()
    }

    /** 粗分类：把一批笔记分到固定分类体系中。 */
    private fun requestCoarseAssignments(
        settings: AiClassificationSettings,
        notes: List<AiNote>,
    ): Map<String, Pair<String, Double>> {
        val categoryJson = JSONArray()
        CoarseTaxonomy.categories.forEach { def ->
            categoryJson.put(
                JSONObject()
                    .put("id", def.id)
                    .put("name", def.name)
                    .put("desc", def.description),
            )
        }
        val noteJson = JSONArray()
        notes.forEach { note ->
            noteJson.put(
                JSONObject()
                    .put("id", note.rednoteId)
                    .put("title", note.title.take(80))
                    .put("keywords", note.aiKeywords.ifBlank { note.desc.take(120) }),
            )
        }
        val body = JSONObject()
            .put("model", DEEPSEEK_MODEL)
            .put("temperature", 0.1)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "你是小红书收藏笔记分类助手。请把每条笔记分到给定的固定分类之一（返回分类 id），" +
                                    "实在无法判断的分到“其他”。只输出 JSON：{\"assignments\":[{\"noteId\":\"\",\"categoryId\":\"\"}]}",
                            ),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", JSONObject().put("categories", categoryJson).put("notes", noteJson).toString()),
                    ),
            )
        val response = postJson(
            url = "$DEFAULT_DEEPSEEK_BASE_URL/chat/completions",
            label = "DeepSeek 粗分类 notes=${notes.size}",
            apiKey = settings.deepSeekApiKey,
            body = body,
        )
        val content = responseContent(response)
        logAi("DeepSeek 粗分类原始返回：notes=${notes.size}，content=${content.take(1200)}")
        val json = JSONObject(extractJsonObject(content))
        val validNoteIds = notes.map { it.rednoteId }.toSet()
        val assignmentsJson = json.optJSONArray("assignments") ?: JSONArray()
        val assignments = mutableMapOf<String, Pair<String, Double>>()
        for (index in 0 until assignmentsJson.length()) {
            val item = assignmentsJson.optJSONObject(index) ?: continue
            val noteId = item.optString("noteId")
            val categoryId = item.optString("categoryId")
            if (noteId in validNoteIds && CoarseTaxonomy.byId.containsKey(categoryId)) {
                assignments[noteId] = categoryId to COARSE_ASSIGN_CONFIDENCE
            }
        }
        return assignments
    }

    /** 裂变：只对粗分类本身（未裂变过）且超过阈值、有裂变约束的分类做一层细分。 */
    private fun runFission(settings: AiClassificationSettings): Int {
        var splitCount = 0
        db.aiCategoryProfiles()
            .filter { it.id == it.coarseId }
            .forEach { category ->
                val def = CoarseTaxonomy.byId[category.coarseId] ?: return@forEach
                if (def.fissionHint.isBlank()) return@forEach
                if (category.count <= settings.customSplitThreshold) return@forEach
                logAi("裂变开始：category=${category.name}，count=${category.count}，hint=${def.fissionHint}")
                runCatching {
                    ensureKeywordCache(settings, db.aiNotesInCategory(category.id))
                    val notes = db.aiNotesInCategory(category.id)
                    val groups = requestConstrainedFission(settings, category.name, def.fissionHint, notes)
                    val created = db.splitCategoryInto(category.id, groups)
                    splitCount += created
                    logAi("裂变完成：category=${category.name}，细分分类=$created")
                }.onFailure {
                    Log.w(AI_LOG_TAG, "裂变失败：category=${category.name}", it)
                }
            }
        return splitCount
    }

    /** 带约束的裂变请求：把裂变提示词（按地点/商圈/菜系等）拼进 prompt。 */
    private fun requestConstrainedFission(
        settings: AiClassificationSettings,
        categoryName: String,
        fissionHint: String,
        notes: List<AiNote>,
    ): List<AiSeedCategory> {
        val noteJson = JSONArray()
        notes.forEach { note ->
            noteJson.put(
                JSONObject()
                    .put("id", note.rednoteId)
                    .put("title", note.title.take(80))
                    .put("desc", note.desc.take(160))
                    .put("keywords", note.aiKeywords),
            )
        }
        val body = JSONObject()
            .put("model", DEEPSEEK_MODEL)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "你是小红书收藏笔记细分助手。分类「$categoryName」笔记过多，需要裂变为更具体的细分分类。" +
                                    "细分约束：$fissionHint。生成 2 到 $MAX_FISSION_GROUPS 个细分分类，分类名要短且具体，符合约束中的命名方式；" +
                                    "把能明确归入某个细分分类的笔记 id 分进去，无法明确细分的笔记不要返回其 id（它们会留在原分类）。" +
                                    "只输出 JSON：{\"categories\":[{\"name\":\"\",\"ids\":[\"\"]}]}",
                            ),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", JSONObject().put("category", categoryName).put("notes", noteJson).toString()),
                    ),
            )
        val response = postJson(
            url = "$DEFAULT_DEEPSEEK_BASE_URL/chat/completions",
            label = "DeepSeek 约束裂变 category=$categoryName",
            apiKey = settings.deepSeekApiKey,
            body = body,
        )
        val content = responseContent(response)
        logAi("DeepSeek 裂变原始返回：category=$categoryName，content=${content.take(1200)}")
        val json = JSONObject(extractJsonObject(content))
        val validIds = notes.map { it.rednoteId }.toSet()
        val categoriesJson = json.optJSONArray("categories") ?: JSONArray()
        val usedIds = mutableSetOf<String>()
        return buildList {
            for (index in 0 until categoriesJson.length()) {
                val item = categoriesJson.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val ids = item.optJSONArray("ids").toStringList()
                    .filter { it in validIds && it !in usedIds }
                    .distinct()
                if (name.isNotBlank() && ids.isNotEmpty()) {
                    usedIds += ids
                    add(AiSeedCategory(name = name, noteIds = ids))
                }
            }
        }.take(MAX_FISSION_GROUPS)
    }

    /** embedding 增量匹配：笔记摘要 vs 现有分类，命中阈值才归类。 */
    private fun matchNotesByEmbedding(
        settings: AiClassificationSettings,
        notes: List<AiNote>,
        categories: List<AiCategoryProfile>,
    ): Int {
        val categoryInputs = categories.map { "${it.name} ${it.sampleText.take(160)}".trim() }
        val inputs = notes.map { it.summaryEmbeddingText() } + categoryInputs
        val vectors = requestEmbeddings(settings, inputs)
        if (vectors.size != inputs.size) return 0
        val noteVectors = vectors.take(notes.size)
        val categoryVectors = vectors.drop(notes.size)
        val assignments = mutableMapOf<String, Pair<String, Double>>()
        notes.forEachIndexed { noteIndex, note ->
            val best = categories.mapIndexed { categoryIndex, category ->
                category to cosine(noteVectors[noteIndex], categoryVectors[categoryIndex])
            }.maxByOrNull { it.second }
            logAi("增量匹配：note=${note.rednoteId}，best=${best?.first?.name ?: "none"}，confidence=${best?.second ?: 0.0}，threshold=${settings.customMatchThreshold}")
            if (best != null && best.second >= settings.customMatchThreshold) {
                assignments[note.rednoteId] = best.first.id to best.second
            }
        }
        return db.assignNotesToCategories(assignments)
    }

    private fun ensureKeywordCache(settings: AiClassificationSettings, notes: List<AiNote>): Int {
        val missingKeywordNotes = notes.filter { it.aiKeywords.isBlank() }
        if (missingKeywordNotes.isEmpty()) return 0
        var updated = 0
        missingKeywordNotes.chunked(KEYWORD_BATCH_SIZE).forEach { batch ->
            logAi("补齐关键词：batch=${batch.size}，ids=${batch.joinToString { it.rednoteId }.take(300)}")
            updated += db.updateAiKeywords(requestNoteKeywords(settings, batch))
        }
        logAi("关键词补齐完成：missing=${missingKeywordNotes.size}，updated=$updated")
        return updated
    }

    private fun requestEmbeddings(settings: AiClassificationSettings, inputs: List<String>): List<List<Double>> {
        if (!settings.embeddingEnabled) return emptyList()
        logAi("请求 Embedding：inputs=${inputs.size}")
        val body = JSONObject()
            .put("model", EMBEDDING_MODEL)
            .put("input", JSONArray(inputs))
        val response = postJson(
            url = "${settings.embeddingBaseUrl.trimEnd('/')}/embeddings",
            label = "Embedding inputs=${inputs.size}",
            apiKey = settings.embeddingApiKey,
            body = body,
        )
        val data = response.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                val embedding = data.optJSONObject(index)?.optJSONArray("embedding") ?: continue
                add(buildList {
                    for (valueIndex in 0 until embedding.length()) {
                        add(embedding.optDouble(valueIndex))
                    }
                })
            }
        }.also { logAi("Embedding 返回：vectors=${it.size}") }
    }

    private fun requestNoteKeywords(settings: AiClassificationSettings, notes: List<AiNote>): Map<String, String> {
        val noteJson = JSONArray()
        notes.forEach { note ->
            noteJson.put(
                JSONObject()
                    .put("id", note.rednoteId)
                    .put("title", note.title.take(120))
                    .put("desc", note.desc.take(280)),
            )
        }
        val body = JSONObject()
            .put("model", DEEPSEEK_MODEL)
            .put("temperature", 0.1)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "你是收藏笔记分类关键词提取器。根据每条笔记的标题和摘要提取 3 到 8 个适合分类归档的中文关键词或短语，忽略语气词、泛泛评价和无关细节。只输出 JSON：{\"notes\":[{\"id\":\"\",\"keywords\":[\"\"]}]}",
                            ),
                    )
                    .put(JSONObject().put("role", "user").put("content", JSONObject().put("notes", noteJson).toString())),
            )
        val response = postJson(
            url = "$DEFAULT_DEEPSEEK_BASE_URL/chat/completions",
            label = "DeepSeek 关键词 notes=${notes.size}",
            apiKey = settings.deepSeekApiKey,
            body = body,
        )
        val content = responseContent(response)
        logAi("DeepSeek 关键词原始返回：notes=${notes.size}，content=${content.take(1200)}")
        val json = JSONObject(extractJsonObject(content))
        val result = mutableMapOf<String, String>()
        val responseNotes = json.optJSONArray("notes") ?: JSONArray()
        for (index in 0 until responseNotes.length()) {
            val item = responseNotes.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val keywords = item.optJSONArray("keywords").toStringList()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(8)
            if (id.isNotBlank() && keywords.isNotEmpty()) {
                result[id] = keywords.joinToString(" ")
            }
        }
        return result
    }

    private fun responseContent(response: JSONObject): String {
        return response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
    }

    private fun postJson(url: String, label: String, apiKey: String, body: JSONObject): JSONObject {
        logAi("请求开始：$label，url=$url，body=${body.toString().take(1200)}")
        val startAt = System.currentTimeMillis()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Content-Type", "application/json")
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream.bufferedReader().use(BufferedReader::readText)
        logAi("请求结束：$label，code=${connection.responseCode}，cost=${System.currentTimeMillis() - startAt}ms，response=${text.take(1500)}")
        if (connection.responseCode !in 200..299) {
            error("AI 接口请求失败：${connection.responseCode} ${text.take(180)}")
        }
        return JSONObject(text)
    }

    private fun AiNote.summaryEmbeddingText(): String {
        return aiKeywords.ifBlank { "${title.take(80)} ${desc.take(160)}" }.trim()
    }
}

data class HomeData(
    val categories: List<CategorySummary>,
    val suggestions: List<PendingCategorySuggestion>,
    val searchableNotes: List<SearchableNote>,
)

data class SyncCompleteResult(
    val success: Boolean,
    val message: String,
    val total: Int,
    val removed: Int = 0,
)

private data class SyncPayload(
    val notes: List<SyncedNote>,
    val pageState: SyncPageState?,
)

private val AUTH_COOKIE_NAMES = listOf("web_session")

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun cosine(left: List<Double>, right: List<Double>): Double {
    if (left.isEmpty() || left.size != right.size) return 0.0
    var dot = 0.0
    var leftNorm = 0.0
    var rightNorm = 0.0
    left.indices.forEach { index ->
        dot += left[index] * right[index]
        leftNorm += left[index] * left[index]
        rightNorm += right[index] * right[index]
    }
    if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0
    return dot / (sqrt(leftNorm) * sqrt(rightNorm))
}

private fun extractJsonObject(raw: String): String {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return "{}"
    return raw.substring(start, end + 1)
}

private fun logAi(message: String) {
    Log.d(AI_LOG_TAG, message)
}

private const val EMBEDDING_MODEL = "text-embedding-3-small"
private const val DEEPSEEK_MODEL = "deepseek-v4-flash"
private const val AI_LOG_TAG = "JiShiAiClassifier"
private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
private const val KEY_EMBEDDING_API_KEY = "embedding_api_key"
private const val KEY_EMBEDDING_BASE_URL = "embedding_base_url"
private const val KEY_TOLERANCE = "tolerance"
private const val KEY_SPLIT_THRESHOLD = "split_threshold"
private const val KEY_MATCH_THRESHOLD = "match_threshold"
private const val KEYWORD_BATCH_SIZE = 12
private const val COARSE_BATCH_SIZE = 20
private const val EMBEDDING_NOTE_BATCH = 16
private const val MAX_FISSION_GROUPS = 6
private const val COARSE_ASSIGN_CONFIDENCE = 0.85
