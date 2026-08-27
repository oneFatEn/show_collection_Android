package com.jishi.collection

import kotlin.math.ceil

data class PrimaryCategoryDef(
    val id: String,
    val name: String,
    val description: String,
    val allowedDimensions: Set<String>,
    val fissionHint: String,
)

/** ADR-0001 规定的固定一级分类。模型只能返回这里声明的 id。 */
object ClassificationTaxonomy {
    val primaryCategories: List<PrimaryCategoryDef> = listOf(
        PrimaryCategoryDef("food", "美食", "做饭、菜谱、餐厅、咖啡、烘焙及饮食内容", setOf("dish_type", "city"), "只能按菜品类型或探店城市细分"),
        PrimaryCategoryDef("travel", "旅行出行", "目的地、景点、住宿、路线和周边游", setOf("destination"), "只能按目的地细分"),
        PrimaryCategoryDef("style_beauty", "穿搭美妆", "服饰、鞋包、妆容、护肤、发型和美甲", setOf("scene", "product_type"), "只能按场景或品类细分"),
        PrimaryCategoryDef("home_life", "居家生活", "装修、家居、收纳、家务及生活空间", setOf("space", "housework_topic"), "只能按空间或家务主题细分"),
        PrimaryCategoryDef("learning_career", "学习职场", "学习、考试、技能、求职和工作方法", setOf("skill", "exam", "work_task"), "只能按技能、考试或工作任务细分"),
        PrimaryCategoryDef("sports_health", "运动健康", "运动、训练、健康管理、睡眠和养生", setOf("sport_type", "health_goal"), "只能按运动类型或健康目标细分"),
        PrimaryCategoryDef("family_pets", "亲子宠物", "育儿、亲子活动、宠物饲养及宠物健康", setOf("age_stage", "pet_type"), "只能按年龄阶段或宠物类型细分"),
        PrimaryCategoryDef("digital_tech", "数码科技", "数码产品、软件工具、设备使用和科技内容", setOf("product_type", "software_use"), "只能按产品品类或软件用途细分"),
        PrimaryCategoryDef("entertainment_hobbies", "娱乐兴趣", "影视、音乐、游戏、摄影及其他兴趣内容", setOf("content_type", "hobby"), "只能按内容类型或具体爱好细分"),
        PrimaryCategoryDef("emotion_growth", "情感成长", "恋爱、人际关系、心理和自我成长", setOf("relationship_type", "growth_topic"), "只能按关系类型或成长议题细分"),
        PrimaryCategoryDef("other", "其他", "明确不属于以上十个主题的正式分类", emptySet(), ""),
    )

    val byId: Map<String, PrimaryCategoryDef> = primaryCategories.associateBy { it.id }

    val legacyPrimaryMapping: Map<String, String> = mapOf(
        "cat_food" to "food",
        "cat_coarse_cooking" to "food",
        "cat_coarse_food_explore" to "food",
        "cat_travel" to "travel",
        "cat_coarse_travel" to "travel",
        "cat_outfit" to "style_beauty",
        "cat_coarse_outfit" to "style_beauty",
        "cat_coarse_beauty" to "style_beauty",
        "cat_coarse_home" to "home_life",
        "cat_study" to "learning_career",
        "cat_coarse_study" to "learning_career",
        "cat_coarse_career" to "learning_career",
        "cat_coarse_fitness" to "sports_health",
        "cat_coarse_health" to "sports_health",
        "cat_coarse_baby" to "family_pets",
        "cat_coarse_pet" to "family_pets",
        "cat_coarse_digital" to "digital_tech",
        "cat_coarse_entertainment" to "entertainment_hobbies",
        "cat_coarse_photography" to "entertainment_hobbies",
        "cat_coarse_emotion" to "emotion_growth",
        "cat_coarse_other" to "other",
    )
}

object ConstrainedClassificationPolicy {
    const val MIN_PRIMARY_CONFIDENCE = 0.75
    const val MIN_SECONDARY_MATCH_CONFIDENCE = 0.85
    const val MAX_SECONDARY_CATEGORIES = 6
    const val MIN_DISTINCT_SOURCES = 3

    fun minimumCandidateCount(parentActiveCount: Int): Int =
        maxOf(8, minOf(20, ceil(parentActiveCount.coerceAtLeast(0) * 0.1).toInt()))

    fun validate(result: StructuredClassificationResult): ValidatedClassification? {
        val primary = ClassificationTaxonomy.byId[result.primaryCategoryId] ?: return null
        if (result.primaryConfidence !in MIN_PRIMARY_CONFIDENCE..1.0) return null
        val dimension = result.dimension?.trim().orEmpty()
        if (dimension.isNotEmpty() && dimension !in primary.allowedDimensions) return null
        return ValidatedClassification(
            noteId = result.noteId,
            primaryCategoryId = primary.id,
            primaryConfidence = result.primaryConfidence,
            dimension = dimension.ifBlank { null },
            dimensionValue = result.dimensionValue?.trim()?.takeIf(String::isNotBlank),
            existingSecondaryCategoryId = result.existingSecondaryCategoryId?.trim()?.takeIf(String::isNotBlank),
            tags = result.tags.map(::normalizeLabel).filter(String::isNotBlank).distinct().take(12),
            reason = result.reason.trim().take(160),
        )
    }

    fun canCreateSecondary(
        parent: PrimaryCategoryDef,
        parentActiveCount: Int,
        existingSecondaryNames: List<String>,
        candidate: SecondaryCategoryCandidate,
    ): Boolean {
        if (parent.allowedDimensions.isEmpty() || candidate.dimension !in parent.allowedDimensions) return false
        if (existingSecondaryNames.size >= MAX_SECONDARY_CATEGORIES) return false
        if (candidate.noteIds.distinct().size < minimumCandidateCount(parentActiveCount)) return false
        if (candidate.sourceKeys.map(::normalizeLabel).filter(String::isNotBlank).distinct().size < MIN_DISTINCT_SOURCES) return false
        if (candidate.confidence < MIN_SECONDARY_MATCH_CONFIDENCE) return false
        val candidateKey = normalizeLabel(candidate.name)
        if (candidateKey.isBlank()) return false
        return existingSecondaryNames.none { existing ->
            val existingKey = normalizeLabel(existing)
            existingKey == candidateKey || existingKey.contains(candidateKey) || candidateKey.contains(existingKey)
        }
    }

    internal fun normalizeLabel(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[\\s·・_\\-/]+"), "")
        .removeSuffix("推荐")
        .removeSuffix("合集")
}
