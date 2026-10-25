package com.coursetable.app

import android.content.Intent
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.coursetable.app.importer.CandidateCourse
import com.coursetable.app.ui.ImportFlowState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class ExternalImportBackTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun anIncomingReviewCancelsAndReturnsWithoutASecondPageExit() {
        val context = rule.activity
        val fixture = File.createTempFile("predictive-import-", ".ics", context.cacheDir)
        lateinit var rootDraft: ImportFlowState
        rule.runOnIdle {
            rootDraft = ViewModelProvider(context).get("root-import", ImportFlowState::class.java)
            rootDraft.reviewCandidates.value = listOf(CandidateCourse("保留的导入草稿", dayOfWeek = 1,
                startSection = 1, duration = 2, startWeek = 1, endWeek = 16, weekType = 0))
            rootDraft.reviewDirty.value = true
        }
        try {
            fixture.writeText("""
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                SUMMARY:预测返回测试课程
                DTSTART:20261026T080000
                DTEND:20261026T094000
                END:VEVENT
                END:VCALENDAR
            """.trimIndent())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.testfiles", fixture)
            rule.runOnIdle {
                context.startActivity(Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("import-review-page").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("external-import-page").assertExists()
            rule.runOnIdle {
                context.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, 0))
                context.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 300f, .7f, 0))
            }
            rule.waitForIdle()
            rule.runOnIdle { context.onBackPressedDispatcher.dispatchOnBackCancelled() }
            rule.waitForIdle()
            rule.onNodeWithTag("import-review-page").assertIsDisplayed()
            rule.onNodeWithText("预测返回测试课程").assertExists()
            rule.runOnIdle {
                assertEquals("保留的导入草稿", rootDraft.reviewCandidates.value.single().name)
                assertTrue(rootDraft.reviewDirty.value)
            }

            rule.mainClock.autoAdvance = false
            rule.runOnIdle {
                context.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, 0))
                context.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 300f, .7f, 0))
            }
            rule.waitForIdle()
            rule.runOnIdle { context.onBackPressedDispatcher.onBackPressed() }
            // Includes predictive completion + composition frames, but not a second 320ms exit.
            rule.mainClock.advanceTimeBy(260)
            rule.waitForIdle()
            rule.onNodeWithTag("external-import-page").assertDoesNotExist()
            rule.onNodeWithTag("root-pager").assertExists()
            rule.runOnIdle {
                assertEquals("保留的导入草稿", rootDraft.reviewCandidates.value.single().name)
                assertTrue(rootDraft.reviewDirty.value)
            }
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
        } finally {
            fixture.delete()
        }
    }
}
