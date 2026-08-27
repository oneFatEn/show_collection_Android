package com.jishi.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstrainedClassificationPolicyTest {
    @Test
    fun taxonomyContainsOnlyAcceptedPrimaryCategories() {
        assertEquals(
            setOf(
                "food",
                "travel",
                "style_beauty",
                "home_life",
                "learning_career",
                "sports_health",
                "family_pets",
                "digital_tech",
                "entertainment_hobbies",
                "emotion_growth",
                "other",
            ),
            ClassificationTaxonomy.byId.keys,
        )
    }

    @Test
    fun candidateThresholdUsesTenPercentWithBounds() {
        assertEquals(8, ConstrainedClassificationPolicy.minimumCandidateCount(50))
        assertEquals(12, ConstrainedClassificationPolicy.minimumCandidateCount(120))
        assertEquals(20, ConstrainedClassificationPolicy.minimumCandidateCount(500))
    }

    @Test
    fun invalidPrimaryDimensionAndLowConfidenceGoPending() {
        assertNull(ConstrainedClassificationPolicy.validate(result(primaryCategoryId = "unknown")))
        assertNull(ConstrainedClassificationPolicy.validate(result(primaryConfidence = 0.74)))
        assertNull(ConstrainedClassificationPolicy.validate(result(dimension = "free_form")))
        assertTrue(ConstrainedClassificationPolicy.validate(result()) != null)
    }

    @Test
    fun secondaryCreationRequiresSamplesSourcesDimensionAndConfidence() {
        val parent = ClassificationTaxonomy.byId.getValue("food")
        val valid = candidate(noteCount = 12, sourceCount = 3)

        assertTrue(ConstrainedClassificationPolicy.canCreateSecondary(parent, 120, emptyList(), valid))
        assertFalse(ConstrainedClassificationPolicy.canCreateSecondary(parent, 120, emptyList(), candidate(11, 3)))
        assertFalse(ConstrainedClassificationPolicy.canCreateSecondary(parent, 120, emptyList(), candidate(12, 2)))
        assertFalse(ConstrainedClassificationPolicy.canCreateSecondary(parent, 120, emptyList(), valid.copy(dimension = "scene")))
        assertFalse(ConstrainedClassificationPolicy.canCreateSecondary(parent, 120, emptyList(), valid.copy(confidence = 0.84)))
    }

    @Test
    fun secondaryCreationStopsAtSixAndRejectsNearDuplicateNames() {
        val parent = ClassificationTaxonomy.byId.getValue("food")
        val valid = candidate(noteCount = 12, sourceCount = 3)

        assertTrue(
            ConstrainedClassificationPolicy.canCreateSecondary(
                parent,
                120,
                List(5) { "已有分类$it" },
                valid,
            ),
        )
        assertFalse(
            ConstrainedClassificationPolicy.canCreateSecondary(
                parent,
                120,
                List(6) { "已有分类$it" },
                valid,
            ),
        )
        assertFalse(
            ConstrainedClassificationPolicy.canCreateSecondary(
                parent,
                120,
                listOf("上海探店推荐"),
                valid,
            ),
        )
    }

    @Test
    fun otherNeverCreatesSecondaryCategory() {
        assertFalse(
            ConstrainedClassificationPolicy.canCreateSecondary(
                parent = ClassificationTaxonomy.byId.getValue("other"),
                parentActiveCount = 120,
                existingSecondaryNames = emptyList(),
                candidate = candidate(noteCount = 12, sourceCount = 3),
            ),
        )
    }

    private fun result(
        primaryCategoryId: String = "food",
        primaryConfidence: Double = 0.9,
        dimension: String? = "city",
    ) = StructuredClassificationResult(
        noteId = "note-1",
        primaryCategoryId = primaryCategoryId,
        primaryConfidence = primaryConfidence,
        dimension = dimension,
        dimensionValue = "上海",
        existingSecondaryCategoryId = null,
        tags = listOf("咖啡店", "约会"),
        reason = "主题明确",
    )

    private fun candidate(noteCount: Int, sourceCount: Int) = SecondaryCategoryCandidate(
        name = "上海探店",
        dimension = "city",
        noteIds = List(noteCount) { "note-$it" },
        sourceKeys = List(sourceCount) { "author-$it" },
        confidence = 0.9,
    )
}
