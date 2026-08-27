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

data class AiClassificationSettings(
    val deepSeekApiKey: String = "",
) {
    val aiEnabled: Boolean
        get() = deepSeekApiKey.isNotBlank()

    val smartClassificationEnabled: Boolean
        get() = deepSeekApiKey.isNotBlank()

}

data class Category(
    val id: String,
    val name: String,
    val type: String,
    val sortOrder: Int,
    val coarseId: String = "",
    val level: Int = 1,
    val parentId: String? = null,
    val systemKey: String = "",
    val fissionDimension: String = "",
    val source: String = type,
    val status: String = "active",
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

data class AiNote(
    val rednoteId: String,
    val title: String,
    val desc: String,
    val aiKeywords: String,
    val categoryId: String?,
    val categoryName: String?,
    val authorName: String = "",
)

data class AiCategoryProfile(
    val id: String,
    val name: String,
    val count: Int,
    val sampleText: String,
    val coarseId: String = "",
)

data class AiClassificationRunResult(
    val matched: Int = 0,
    val splitCategories: Int = 0,
    val seededCategories: Int = 0,
    val skippedReason: String? = null,
)

data class StructuredClassificationResult(
    val noteId: String,
    val primaryCategoryId: String,
    val primaryConfidence: Double,
    val dimension: String?,
    val dimensionValue: String?,
    val existingSecondaryCategoryId: String?,
    val tags: List<String>,
    val reason: String,
)

data class ValidatedClassification(
    val noteId: String,
    val primaryCategoryId: String,
    val primaryConfidence: Double,
    val dimension: String?,
    val dimensionValue: String?,
    val existingSecondaryCategoryId: String?,
    val tags: List<String>,
    val reason: String,
)

data class SecondaryCategoryCandidate(
    val name: String,
    val dimension: String,
    val noteIds: List<String>,
    val sourceKeys: List<String>,
    val confidence: Double,
)

const val DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com"
