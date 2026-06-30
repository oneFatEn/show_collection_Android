package com.jishi.collection

enum class NoteStatus {
    ACTIVE,
    REMOVED,
    UNAVAILABLE,
}

enum class SyncStatus {
    IDLE,
    RUNNING,
    FAILED,
    COMPLETED,
}

data class Category(
    val id: String,
    val name: String,
    val type: String,
    val sortOrder: Int,
)

data class NoteCard(
    val rednoteId: String,
    val categoryId: String,
    val title: String,
    val desc: String,
    val authorName: String,
    val coverUrl: String,
    val noteUrl: String,
)

data class CategorySummary(
    val id: String,
    val name: String,
    val count: Int,
    val previews: List<String>,
    val isInvalid: Boolean = false,
)

data class SearchableNote(
    val rednoteId: String,
    val categoryId: String,
    val categoryName: String,
    val title: String,
    val desc: String,
    val coverUrl: String,
    val noteUrl: String,
)

data class PendingCategorySuggestion(
    val id: String,
    val rednoteIds: List<String>,
    val suggestedName: String,
    val reason: String,
)

data class SyncedNote(
    val rednoteId: String,
    val title: String,
    val desc: String,
    val authorName: String,
    val coverUrl: String,
    val noteUrl: String,
)
