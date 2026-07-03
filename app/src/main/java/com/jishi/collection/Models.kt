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

enum class AiClassificationTolerance(
    val label: String,
    val splitThreshold: Int,
    val matchThreshold: Double,
) {
    STRICT("严格", 24, 0.68),
    BALANCED("平衡", 36, 0.56),
    LOOSE("宽容", 50, 0.42),
}

data class AiClassificationSettings(
    val deepSeekApiKey: String = "",
    val embeddingApiKey: String = "",
    val embeddingBaseUrl: String = DEFAULT_EMBEDDING_BASE_URL,
    val tolerance: AiClassificationTolerance = AiClassificationTolerance.BALANCED,
    val customSplitThreshold: Int = AiClassificationTolerance.BALANCED.splitThreshold,
    val customMatchThreshold: Double = AiClassificationTolerance.BALANCED.matchThreshold,
) {
    val aiEnabled: Boolean
        get() = deepSeekApiKey.isNotBlank() && embeddingApiKey.isNotBlank()

    val smartClassificationEnabled: Boolean
        get() = deepSeekApiKey.isNotBlank()

    val embeddingEnabled: Boolean
        get() = embeddingApiKey.isNotBlank()
}

data class Category(
    val id: String,
    val name: String,
    val type: String,
    val sortOrder: Int,
    val coarseId: String = "",
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

data class AiSeedCategory(
    val name: String,
    val noteIds: List<String>,
)

const val DEFAULT_EMBEDDING_BASE_URL = "https://api.chatanywhere.tech/v1"
const val DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com"
