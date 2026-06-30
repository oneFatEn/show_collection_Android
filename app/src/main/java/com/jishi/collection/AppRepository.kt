package com.jishi.collection

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = LocalDatabase(context.applicationContext)

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

    suspend fun syncDemoNotes(): SyncResult = withContext(Dispatchers.IO) {
        db.syncNotes(sampleNotes())
    }

    suspend fun syncFromJson(json: String): SyncResult = withContext(Dispatchers.IO) {
        val payload = parseSyncPayload(json)
        db.syncNotes(payload.notes, payload.pageState)
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
