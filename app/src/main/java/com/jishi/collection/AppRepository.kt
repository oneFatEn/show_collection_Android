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
import java.security.MessageDigest

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = LocalDatabase(context.applicationContext)
    private val preferences = appContext.getSharedPreferences("ai_classification", Context.MODE_PRIVATE)
    private val secureApiKeyStore = SecureApiKeyStore(appContext)

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
        var apiKey = secureApiKeyStore.read()
        if (apiKey.isBlank()) {
            apiKey = preferences.getString(KEY_DEEPSEEK_API_KEY, "").orEmpty()
            if (apiKey.isNotBlank()) {
                secureApiKeyStore.write(apiKey)
                preferences.edit().remove(KEY_DEEPSEEK_API_KEY).apply()
            }
        }
        return AiClassificationSettings(
            deepSeekApiKey = apiKey,
        )
    }

    fun saveAiSettings(settings: AiClassificationSettings) {
        secureApiKeyStore.write(settings.deepSeekApiKey)
        preferences.edit().remove(KEY_DEEPSEEK_API_KEY).apply()
    }

    suspend fun runRuleClassification(noteIds: Set<String>? = null): Int = withContext(Dispatchers.IO) {
        db.rerunRuleClassification(noteIds)
    }

    /**
     * 分类按钮触发：使用固定一级枚举完成结构化分类，再执行受控二级分类门控。
     */
    suspend fun runCoarseClassification(): AiClassificationRunResult = withContext(Dispatchers.IO) {
        val settings = loadAiSettings()
        if (!settings.smartClassificationEnabled) {
            logAi("粗分类跳过：未配置 DeepSeek API Key")
            return@withContext AiClassificationRunResult(skippedReason = "未配置 API Key，已使用固定分类词分类")
        }
        db.ensurePrimaryCategories(ClassificationTaxonomy.primaryCategories)
        val notes = db.aiNotesForCoarseClassification()
        logAi("粗分类开始：待分类笔记=${notes.size}")
        var matched = 0
        if (notes.isNotEmpty()) {
            db.aiNotesForCoarseClassification().chunked(COARSE_BATCH_SIZE).forEach { batch ->
                runCatching {
                    val classifications = classifyWithCache(settings, batch)
                    matched += db.applyClassifications(classifications)
                    db.markPendingClassification(
                        batch.map(AiNote::rednoteId).toSet() - classifications.map(ValidatedClassification::noteId).toSet(),
                        "low_confidence_or_invalid_response",
                    )
                }.onFailure {
                    db.markPendingClassification(batch.map(AiNote::rednoteId).toSet(), "model_request_failed")
                    Log.w(AI_LOG_TAG, "粗分类批次失败：batch=${batch.size}", it)
                }
            }
        }
        val split = runFission(settings)
        logAi("粗分类结束：matched=$matched，split=$split")
        AiClassificationRunResult(matched = matched, splitCategories = split)
    }

    /**
     * 同步后的增量路径与首次分类使用相同的固定枚举和 schema 校验。
     * 只处理本次新增/恢复笔记，不移动已有稳定结果。
     */
    suspend fun runIncrementalClassification(noteIds: Set<String>): AiClassificationRunResult = withContext(Dispatchers.IO) {
        val settings = loadAiSettings()
        if (!settings.smartClassificationEnabled) {
            logAi("增量分类跳过：未配置 DeepSeek API Key")
            return@withContext AiClassificationRunResult(skippedReason = "未配置 DeepSeek API Key，新增笔记已放入待整理")
        }
        db.ensurePrimaryCategories(ClassificationTaxonomy.primaryCategories)
        val notes = db.aiNotesForCoarseClassification(noteIds)
        if (notes.isEmpty()) return@withContext AiClassificationRunResult()
        logAi("增量分类开始：notes=${notes.size}")
        var matched = 0
        notes.chunked(COARSE_BATCH_SIZE).forEach { batch ->
            runCatching {
                val classifications = classifyWithCache(settings, batch)
                matched += db.applyClassifications(classifications)
                db.markPendingClassification(
                    batch.map(AiNote::rednoteId).toSet() - classifications.map(ValidatedClassification::noteId).toSet(),
                    "low_confidence_or_invalid_response",
                )
            }.onFailure {
                db.markPendingClassification(batch.map(AiNote::rednoteId).toSet(), "model_request_failed")
                Log.w(AI_LOG_TAG, "增量分类批次失败：batch=${batch.size}", it)
            }
        }
        logAi("增量分类结束：matched=$matched，pending=${notes.size - matched}")
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

    /** DeepSeek 结构化输出在写库前必须通过固定枚举、置信度和维度校验。 */
    private fun classifyWithCache(
        settings: AiClassificationSettings,
        notes: List<AiNote>,
    ): List<ValidatedClassification> {
        val categoryContext = ClassificationTaxonomy.primaryCategories.joinToString("|") { primary ->
            val secondaries = db.secondaryCategories(primary.id).joinToString(",") { "${it.id}:${it.name}" }
            "${primary.id}[$secondaries]"
        }
        val fingerprints = notes.associate { note -> note.rednoteId to note.inputFingerprint(categoryContext) }
        val cached = notes.mapNotNull { note ->
            db.classificationCache(fingerprints.getValue(note.rednoteId), CLASSIFICATION_RULE_VERSION, DEEPSEEK_MODEL)
                ?.let(::validatedClassificationFromJson)
                ?.takeIf { it.noteId == note.rednoteId }
        }
        val cachedIds = cached.map(ValidatedClassification::noteId).toSet()
        val uncached = notes.filterNot { it.rednoteId in cachedIds }
        val fresh = if (uncached.isEmpty()) {
            emptyList()
        } else {
            requestConstrainedAssignments(settings, uncached)
        }
        fresh.forEach { classification ->
            db.storeClassificationCache(
                inputFingerprint = fingerprints.getValue(classification.noteId),
                ruleVersion = CLASSIFICATION_RULE_VERSION,
                modelVersion = DEEPSEEK_MODEL,
                structuredResult = classification.toJson().toString(),
                status = "valid",
            )
        }
        return cached + fresh
    }

    private fun requestConstrainedAssignments(
        settings: AiClassificationSettings,
        notes: List<AiNote>,
    ): List<ValidatedClassification> {
        val categoryJson = JSONArray()
        ClassificationTaxonomy.primaryCategories.forEach { def ->
            val secondaryJson = JSONArray()
            db.secondaryCategories(def.id).forEach { secondary ->
                secondaryJson.put(JSONObject().put("id", secondary.id).put("name", secondary.name))
            }
            categoryJson.put(
                JSONObject()
                    .put("id", def.id)
                    .put("name", def.name)
                    .put("desc", def.description)
                    .put("allowedDimensions", JSONArray(def.allowedDimensions.toList()))
                    .put("existingSecondaryCategories", secondaryJson),
            )
        }
        val noteJson = JSONArray()
        notes.forEach { note ->
            noteJson.put(
                JSONObject()
                    .put("id", note.rednoteId)
                    .put("title", note.title.take(80))
                    .put("summary", note.desc.take(180)),
            )
        }
        val body = JSONObject()
            .put("model", DEEPSEEK_MODEL)
            .put("temperature", 0.1)
            .put("max_tokens", 4096)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "你是收藏笔记分类助手。一级分类只能从给定 id 中选择；“其他”是有把握不属于十个主题时的正式分类。" +
                                    "置信度不足也必须如实返回，客户端会放入待整理。dimension 只能从对应 allowedDimensions 中选择，也可为空；" +
                                    "existingSecondaryCategoryId 只能选择对应一级下给定的现有二级 id，不确定时返回 null。" +
                                    "标签用于表达地点、对象、场景、动作和意图；好物、礼物不能作为一级分类，应按商品主题分类并加标签。" +
                                    "只输出 json 格式：{\"assignments\":[{\"noteId\":\"\",\"primaryCategoryId\":\"\",\"primaryConfidence\":0.0," +
                                    "\"dimension\":null,\"dimensionValue\":null,\"existingSecondaryCategoryId\":null,\"tags\":[],\"reason\":\"\"}]}",
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
        val json = JSONObject(extractJsonObject(content))
        val validNoteIds = notes.map { it.rednoteId }.toSet()
        val assignmentsJson = json.optJSONArray("assignments") ?: JSONArray()
        val assignments = mutableListOf<ValidatedClassification>()
        val usedNoteIds = mutableSetOf<String>()
        for (index in 0 until assignmentsJson.length()) {
            val item = assignmentsJson.optJSONObject(index) ?: continue
            val noteId = item.optString("noteId")
            if (noteId !in validNoteIds || !usedNoteIds.add(noteId)) continue
            val raw = StructuredClassificationResult(
                noteId = noteId,
                primaryCategoryId = item.optString("primaryCategoryId"),
                primaryConfidence = item.optDouble("primaryConfidence", -1.0),
                dimension = item.optNullableString("dimension"),
                dimensionValue = item.optNullableString("dimensionValue"),
                existingSecondaryCategoryId = item.optNullableString("existingSecondaryCategoryId"),
                tags = item.optJSONArray("tags").toStringList(),
                reason = item.optString("reason"),
            )
            ConstrainedClassificationPolicy.validate(raw)?.let(assignments::add)
        }
        return assignments
    }

    /** 裂变：只对粗分类本身（未裂变过）且超过阈值、有裂变约束的分类做一层细分。 */
    private fun runFission(settings: AiClassificationSettings): Int {
        var splitCount = 0
        db.aiCategoryProfiles()
            .filter { it.id == it.coarseId }
            .forEach { category ->
                val def = ClassificationTaxonomy.byId[category.coarseId] ?: return@forEach
                val parentActiveCount = db.activeNoteCountInPrimary(category.id)
                if (def.fissionHint.isBlank()) return@forEach
                if (category.count < ConstrainedClassificationPolicy.minimumCandidateCount(parentActiveCount)) return@forEach
                logAi("裂变开始：category=${category.name}，count=$parentActiveCount，hint=${def.fissionHint}")
                runCatching {
                    val notes = db.aiNotesInCategory(category.id)
                    val acceptedNames = db.secondaryCategories(category.id).map(Category::name).toMutableList()
                    val groups = requestConstrainedFission(settings, def, notes)
                        .filter { candidate ->
                            val accepted = ConstrainedClassificationPolicy.canCreateSecondary(
                                parent = def,
                                parentActiveCount = parentActiveCount,
                                existingSecondaryNames = acceptedNames,
                                candidate = candidate,
                            )
                            if (accepted) acceptedNames += candidate.name
                            accepted
                        }
                    val created = db.createSecondaryCategories(category.id, groups)
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
        category: PrimaryCategoryDef,
        notes: List<AiNote>,
    ): List<SecondaryCategoryCandidate> {
        val noteJson = JSONArray()
        notes.forEach { note ->
            noteJson.put(
                JSONObject()
                    .put("id", note.rednoteId)
                    .put("title", note.title.take(80))
                    .put("desc", note.desc.take(160)),
            )
        }
        val body = JSONObject()
            .put("model", DEEPSEEK_MODEL)
            .put("temperature", 0.2)
            .put("max_tokens", 4096)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "你是收藏笔记细分助手。分类「${category.name}」只能按 ${category.fissionHint}。" +
                                    "dimension 只能是 ${category.allowedDimensions.joinToString()}。生成不超过 $MAX_FISSION_GROUPS 个短且具体的候选；" +
                                    "把能明确归入某个细分分类的笔记 id 分进去，无法明确细分的笔记不要返回其 id（它们会留在原分类）。" +
                                    "只输出 json 格式：{\"categories\":[{\"name\":\"\",\"dimension\":\"\",\"confidence\":0.0,\"ids\":[\"\"]}]}",
                            ),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", JSONObject().put("category", category.name).put("notes", noteJson).toString()),
                    ),
            )
        val response = postJson(
            url = "$DEFAULT_DEEPSEEK_BASE_URL/chat/completions",
            label = "DeepSeek 约束裂变 category=${category.name}",
            apiKey = settings.deepSeekApiKey,
            body = body,
        )
        val content = responseContent(response)
        val json = JSONObject(extractJsonObject(content))
        val notesById = notes.associateBy(AiNote::rednoteId)
        val categoriesJson = json.optJSONArray("categories") ?: JSONArray()
        val usedIds = mutableSetOf<String>()
        return buildList {
            for (index in 0 until categoriesJson.length()) {
                val item = categoriesJson.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val ids = item.optJSONArray("ids").toStringList()
                    .filter { it in notesById && it !in usedIds }
                    .distinct()
                if (name.isNotBlank() && ids.isNotEmpty()) {
                    usedIds += ids
                    add(
                        SecondaryCategoryCandidate(
                            name = name,
                            dimension = item.optString("dimension"),
                            noteIds = ids,
                            sourceKeys = ids.mapNotNull { notesById[it]?.authorName }.filter(String::isNotBlank),
                            confidence = item.optDouble("confidence", 0.0),
                        ),
                    )
                }
            }
        }.take(MAX_FISSION_GROUPS)
    }

    private fun responseContent(response: JSONObject): String {
        return response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
    }

    private fun postJson(url: String, label: String, apiKey: String, body: JSONObject): JSONObject {
        logAi("请求开始：$label")
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
        logAi("请求结束：$label，code=${connection.responseCode}，cost=${System.currentTimeMillis() - startAt}ms")
        if (connection.responseCode !in 200..299) {
            error("AI 接口请求失败：HTTP ${connection.responseCode}")
        }
        return JSONObject(text)
    }

    private fun AiNote.inputFingerprint(categoryContext: String): String {
        val input = listOf(rednoteId, title, desc, categoryContext).joinToString("\u001F")
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
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

private fun JSONObject.optNullableString(name: String): String? {
    if (isNull(name)) return null
    return optString(name).trim().takeIf(String::isNotBlank)
}

private fun ValidatedClassification.toJson(): JSONObject = JSONObject()
    .put("noteId", noteId)
    .put("primaryCategoryId", primaryCategoryId)
    .put("primaryConfidence", primaryConfidence)
    .put("dimension", dimension)
    .put("dimensionValue", dimensionValue)
    .put("existingSecondaryCategoryId", existingSecondaryCategoryId)
    .put("tags", JSONArray(tags))
    .put("reason", reason)

private fun validatedClassificationFromJson(raw: String): ValidatedClassification? = runCatching {
    val item = JSONObject(raw)
    ConstrainedClassificationPolicy.validate(
        StructuredClassificationResult(
            noteId = item.optString("noteId"),
            primaryCategoryId = item.optString("primaryCategoryId"),
            primaryConfidence = item.optDouble("primaryConfidence", -1.0),
            dimension = item.optNullableString("dimension"),
            dimensionValue = item.optNullableString("dimensionValue"),
            existingSecondaryCategoryId = item.optNullableString("existingSecondaryCategoryId"),
            tags = item.optJSONArray("tags").toStringList(),
            reason = item.optString("reason"),
        ),
    )
}.getOrNull()

private fun extractJsonObject(raw: String): String {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return "{}"
    return raw.substring(start, end + 1)
}

private fun logAi(message: String) {
    Log.d(AI_LOG_TAG, message)
}

private const val DEEPSEEK_MODEL = "deepseek-v4-flash"
private const val CLASSIFICATION_RULE_VERSION = "adr-0001-v1"
private const val AI_LOG_TAG = "JiShiAiClassifier"
private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
private const val COARSE_BATCH_SIZE = 20
private const val MAX_FISSION_GROUPS = 6
