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
        )
    }

    suspend fun notesForCategory(categoryId: String): List<NoteCard> = withContext(Dispatchers.IO) {
        db.notesForCategory(categoryId)
    }

    suspend fun syncDemoNotes(): SyncResult = withContext(Dispatchers.IO) {
        db.syncNotes(sampleNotes())
    }

    suspend fun syncFromJson(json: String): SyncResult = withContext(Dispatchers.IO) {
        db.syncNotes(parseNotes(json))
    }

    suspend fun acceptSuggestion(id: String, name: String) = withContext(Dispatchers.IO) {
        db.acceptSuggestion(id, name)
    }

    suspend fun dismissSuggestion(id: String) = withContext(Dispatchers.IO) {
        db.dismissSuggestion(id)
    }

    suspend fun clearMetadataCache() = withContext(Dispatchers.IO) {
        db.clearMetadataCache()
    }

    suspend fun clearAllLocalData() = withContext(Dispatchers.IO) {
        db.clearAllLocalData()
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

    private fun parseNotes(json: String): List<SyncedNote> {
        val root = JSONObject(json)
        val notesArray = when {
            root.has("notes") -> root.getJSONArray("notes")
            root.optJSONObject("data")?.has("notes") == true -> root.getJSONObject("data").getJSONArray("notes")
            else -> JSONArray()
        }

        return buildList {
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
                        coverUrl = source.optJSONObject("cover")?.let { cover ->
                            cover.optString("url_default")
                                .ifBlank { cover.optString("url") }
                                .ifBlank { cover.optString("url_pre") }
                        }
                            ?: item.optJSONObject("cover")?.let { cover ->
                                cover.optString("url_default")
                                    .ifBlank { cover.optString("url") }
                                    .ifBlank { cover.optString("url_pre") }
                            }
                            ?: source.optJSONArray("image_list")?.optJSONObject(0)?.optString("url")
                            ?: item.optJSONArray("image_list")?.optJSONObject(0)?.optString("url")
                            ?: item.optString("coverUrl"),
                        noteUrl = item.optString("noteUrl").ifBlank { "https://www.xiaohongshu.com/explore/$id" },
                    ),
                )
            }
        }
    }

    private fun sampleNotes(): List<SyncedNote> {
        return emptyList()
    }
}

data class HomeData(
    val categories: List<CategorySummary>,
    val suggestions: List<PendingCategorySuggestion>,
)
