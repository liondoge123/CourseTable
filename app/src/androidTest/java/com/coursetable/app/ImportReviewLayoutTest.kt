package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImportReviewLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val missingWeeks = CandidateCourse("高等数学Ⅱ", teacher = "张老师", location = "A301", dayOfWeek = 1,
        startSection = 1, duration = 2, startWeek = 0, endWeek = 0, weekType = 0, draftId = "missing", sourceRegion = "r",
        needsReview = true, issues = listOf("周次缺失，请选择实际上课周次"))

    private fun session(): VisualImportSession {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "review-layout-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "source.png")
        val bitmap = Bitmap.createBitmap(1000,160,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(244,246,250))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 38f }
            drawText("高等数学Ⅱ    星期一 1-2节    张老师    A301",32f,92f,paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        return VisualImportSession(directory,listOf(ImportPage(0,file,1000,160,emptyList())),
            PdfParseResult(listOf(missingWeeks),emptyList(),listOf(ImportRegion("r",0,0f,0f,1f,1f,TimetableLayout.DETAILS))),"test")
    }

    private fun show(session: VisualImportSession? = null, narrow: Boolean = false, dark: Boolean = false, onDismiss: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current
            var courses by remember { mutableStateOf(listOf(missingWeeks)) }
            CourseTableTheme(themeMode = if(dark) com.coursetable.app.ui.theme.ThemeMode.DARK else com.coursetable.app.ui.theme.ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, if(narrow) 1.25f else density.fontScale)) {
                    LiquidBackdropHost(if(narrow) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                        UnifiedImportReview(session,AppSettings(),courses,"图片课表",emptyList(),{ courses=it },onDismiss,{},false,true,null)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun screenshotPause() {
        if (InstrumentationRegistry.getArguments().getString("captureReviewScreens") == "true") SystemClock.sleep(15_000)
    }

    @Test fun discardDialogUsesClearShortActionsAtLargeFont() {
        var dismissed = false
        show(narrow=true,onDismiss={ dismissed=true })
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("放弃本次导入？").assertIsDisplayed()
        compose.onNodeWithText("放弃修改并继续").assertDoesNotExist()
        compose.onNodeWithText("继续校对").assertIsDisplayed()
        compose.onNodeWithText("放弃导入").assertIsDisplayed()
        screenshotPause()
        compose.onNodeWithText("继续校对").performClick()
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("放弃导入").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun compactSourceLeavesNameVisibleAndOptionsKeepEditingDraft() {
        val session = session()
        try {
            show(session)
            compose.onNodeWithText("高等数学Ⅱ").performClick()
            compose.onNodeWithText("原图对照").assertIsDisplayed()
            compose.onNodeWithTag("course-editor-name").assertIsDisplayed()
            val fragment = compose.onNodeWithTag("import-course-fragment", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val name = compose.onNodeWithTag("course-editor-name").fetchSemanticsNode().boundsInRoot
            assertEquals(name.left, fragment.left, 1f)
            assertEquals(name.right, fragment.right, 1f)
            compose.onNodeWithText("! 请确认周次范围").assertDoesNotExist()
            compose.onNodeWithText("原识别结果：请确认周次范围").assertDoesNotExist()
            screenshotPause()
            compose.onNodeWithTag("course-editor-name").performTextReplacement("校对后的课程")
            compose.onNodeWithTag("review-record-included", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithTag("course-editor-page", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("course-editor-name").assertTextContains("校对后的课程")
            compose.onNodeWithTag("course-editor-weeks").performScrollTo().performClick()
            compose.onNodeWithText("全选").performClick()
            compose.onNodeWithTag("course-weeks-confirm").performClick()
            compose.onNodeWithText("选择上课周次").assertDoesNotExist()
            compose.onNodeWithText("保存校对").assertIsEnabled()
            compose.onNodeWithText("保存校对").performClick()
            compose.onNodeWithText("校对后的课程").assertExists()
        } finally { session.close() }
    }

    @Test fun darkCompactEditorKeepsSourceAndWeeksErrorReachable() {
        val session = session()
        try {
            show(session,dark=true)
            compose.onNodeWithText("高等数学Ⅱ").performClick()
            compose.onNodeWithTag("course-editor-name").assertIsDisplayed()
            compose.onNodeWithTag("course-editor-location").performTextReplacement("")
            val teacher = compose.onNodeWithTag("course-editor-teacher").fetchSemanticsNode().boundsInRoot
            val location = compose.onNodeWithTag("course-editor-location").fetchSemanticsNode().boundsInRoot
            assertTrue(kotlin.math.abs(teacher.width - location.width) <= 1f)
            assertTrue(kotlin.math.abs(teacher.height - location.height) <= 1f)
            assertEquals(teacher.top, location.top, 1f)
            compose.onNodeWithTag("course-editor-weeks").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("选择上课周次").assertDoesNotExist()
            compose.onNodeWithText("请选择实际上课周次后保存").assertDoesNotExist()
            screenshotPause()
        } finally { session.close() }
    }
}
