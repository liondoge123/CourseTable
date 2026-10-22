package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseDetailDialog
import com.coursetable.app.ui.liquid.LiquidAmbientBackground
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.glassBackdropSource
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CourseDetailDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun verify(mode: ThemeMode, largeText: Boolean = false) {
        var deletions = 0
        var edits = 0
        val course = Course(name = "模拟电子技术基础与综合实验", teacher = "张老师", location = "第一教学楼 A301",
            dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.5f else 1f)) {
                CourseTableTheme(themeMode = mode) {
                    LiquidBackdropHost(if (largeText) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                        CourseDetailDialog(course, onDismiss = {}, onEdit = { edits++ }, onDelete = { deletions++ })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("课程详情的更多操作").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除当前时段").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        capture("preview-${mode.key}")
        compose.onNodeWithText("编辑").performClick()
        compose.runOnIdle { assertEquals(1, edits); assertEquals(0, deletions) }
        compose.onNodeWithContentDescription("删除当前时段").performClick()
        compose.onNodeWithText("删除此时段？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deletions) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, deletions) }
        compose.onNodeWithContentDescription("删除当前时段").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle { assertEquals(1, deletions) }
    }

    private fun capture(name: String) {
        val session = InstrumentationRegistry.getArguments().getString("previewActionEvidenceSession") ?: return
        require(session.matches(Regex("[a-zA-Z0-9-]+")))
        require(name.matches(Regex("[a-z-]+")))
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/coursetable-testing/$session/$name.png")).use { it.readBytes() }
    }

    @Test fun lightPreviewHasDirectQuietDeleteAndConfirmation() = verify(ThemeMode.LIGHT)
    @Test fun darkLargeTextPreviewKeepsEditPrimaryAndDeleteConfirmed() = verify(ThemeMode.DARK, largeText = true)
}
