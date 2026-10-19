package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.PdfParseResult
import com.coursetable.app.ui.ImportScreen
import com.coursetable.app.ui.PasteImportScreen
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PasteImportTest {
    @get:Rule val compose = createComposeRule()
    private val json = """{"courses":[{"name":"高等数学Ⅱ","teacher":"张三","location":"A301","dayOfWeek":1,"startSection":1,"duration":2,"weeks":[4,9,10,14,15,16]}]}"""

    @Test fun pasteEntryOpensExistingReviewAndKeepsDraftWhenReturning() {
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    ImportScreen()
                }
            }
        }
        compose.onNodeWithText("粘贴导入").performScrollTo().performClick()
        compose.onNodeWithTag("paste-import-content").performTextReplacement(json)
        compose.onNodeWithTag("paste-import-parse").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("导入确认").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("高等数学Ⅱ").assertExists()
        compose.onNodeWithText("粘贴课表").assertExists()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithTag("paste-import-content").assertTextContains("高等数学Ⅱ", substring = true)
    }

    @Test fun malformedInputShowsErrorAndCanBeCorrectedWithoutLosingText() {
        var parsed: PdfParseResult? = null
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    PasteImportScreen(AppSettings(), { parsed = it }, {})
                }
            }
        }
        compose.onNodeWithTag("paste-import-content").performTextReplacement("{\"courses\":")
        compose.onNodeWithTag("paste-import-parse").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("paste-import-error").fetchSemanticsNodes().isNotEmpty() }
        assertNull(parsed)
        compose.onNodeWithTag("paste-import-content").performScrollTo().performTextReplacement(json)
        compose.onNodeWithTag("paste-import-parse").performScrollTo().performClick()
        compose.waitUntil(5_000) { parsed != null }
        assertEquals("高等数学Ⅱ", parsed!!.candidates.single().name)
        compose.onNodeWithTag("paste-import-error").assertDoesNotExist()
    }
}
