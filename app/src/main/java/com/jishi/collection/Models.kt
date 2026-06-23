package com.jishi.collection

enum class NoteStatus {
    ACTIVE,
    REMOVED,
    UNAVAILABLE,
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

