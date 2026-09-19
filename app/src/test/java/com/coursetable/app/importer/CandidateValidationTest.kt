package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import org.junit.Assert.*
import org.junit.Test

class CandidateValidationTest {
    private val valid = CandidateCourse("数学", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18, weekType = 0)
    @Test fun identifiesMissingAndInvalidFieldsWithoutInventingConfidence() {
        assertTrue(valid.reviewIssues(AppSettings()).isEmpty())
        assertEquals(listOf("请对照原图核对课程信息"), valid.copy(needsReview = true).reviewIssues(AppSettings()))
        val issues = valid.copy(name = "", dayOfWeek = 0, duration = 0, startWeek = 19, weekType = 4).reviewIssues(AppSettings())
        assertEquals(5, issues.size)
        assertTrue(issues.contains("请补齐课程名称"))
        assertTrue(valid.copy(startSection = Int.MAX_VALUE).reviewIssues(AppSettings()).contains("请确认节次"))
    }
    @Test fun confirmationNeverBypassesInvalidFieldsAndCopiesRetainIdentity() {
        val uncertain = valid.copy(needsReview = true, draftId = "stable-id")
        assertTrue(uncertain.fieldErrors(AppSettings()).isEmpty())
        assertFalse(uncertain.reviewIssues(AppSettings()).isEmpty())
        val confirmed = uncertain.copy(needsReview = false)
        assertTrue(confirmed.reviewIssues(AppSettings()).isEmpty())
        assertEquals("stable-id", confirmed.draftId)
        assertFalse(confirmed.copy(dayOfWeek = 0).reviewIssues(AppSettings()).isEmpty())
        assertFalse(confirmed.copy(duration = Int.MAX_VALUE).reviewIssues(AppSettings()).isEmpty())
        assertFalse(confirmed.copy(startWeek = 5, endWeek = 2).reviewIssues(AppSettings()).isEmpty())
    }
    @Test fun mergedRecordsRetainEverySource() {
        val c = valid.copy(sourceRegion = "a", sourceRegions = setOf("b"), sourceRecords = listOf(valid.copy(sourceRegion = "c")))
        assertEquals(setOf("a", "b", "c"), c.sourceRegionIds())
        assertEquals(c.sourceRegionIds(), c.copy(name = "修改后").sourceRegionIds())
    }
}
