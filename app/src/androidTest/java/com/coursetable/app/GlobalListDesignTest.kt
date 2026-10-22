package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
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
import com.coursetable.app.data.PeriodTimeScheme
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GlobalListDesignTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val session = InstrumentationRegistry.getArguments().getString("listDesignEvidenceSession") ?: return
        require(session.matches(Regex("[a-zA-Z0-9-]+")))
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        val command = "screencap -p /data/local/tmp/coursetable-testing/$session/$name.png"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    @Test fun pagesKeepTheirContentReadableInBothThemes() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val page = mutableIntStateOf(0)
        val periods = AppSettings.defaultPeriods()
        val scheme = PeriodTimeScheme("visual-day", "日间作息", periods, 45)
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            SideEffect { activity.enableEdgeToEdge() }
            CourseTableTheme(themeMode = mode.value) {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    when (page.intValue) {
                        0 -> SettingsScreen()
                        1 -> ImportScreen()
                        2 -> AboutScreen("preview", {})
                        3 -> EduImportScreen(java.time.LocalDate.of(2026, 9, 1), { _, _ -> }, {})
                        else -> PeriodTimeSchemesScreen(listOf(scheme), periods, 45, scheme.id, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle { mode.value = theme }
            for (scene in 0..4) {
                compose.runOnIdle { page.intValue = scene }
                val title = listOf("设置", "课程数据导入", "关于 CourseTable", "从教务系统导入", "节次时间")[scene]
                compose.onNodeWithText(title).assertIsDisplayed()
                if (scene == 4) {
                    compose.onNodeWithText("日间作息").assertIsDisplayed()
                    compose.onNodeWithTag("period-times-page").assertIsDisplayed()
                    compose.onNodeWithTag("overlay-glass-dialog").assertDoesNotExist()
                }
                capture("page-$scene-${theme.key}")
            }
        }
    }

    @Test fun themeModeChoicesExposeSelectionAndApplyTheTappedMode() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
        val original = runBlocking { app.settingsRepository.settings.first() }
        try {
            compose.setContent { CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) { SettingsScreen() } } }
            compose.onNodeWithText("主题模式").performScrollTo().performClick()
            val current = ThemeMode.fromKey(original.themeMode)
            compose.onNodeWithTag("theme-mode-${current.key}").assertIsSelected().assertHeightIsAtLeast(48.dp)
            capture("theme-mode-picker")
            val next = if (current == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
            compose.onNodeWithTag("theme-mode-${next.key}").performClick()
            compose.waitUntil(5_000) { runBlocking { app.settingsRepository.settings.first().themeMode == next.key } }
            compose.onNodeWithTag("theme-mode-${next.key}").assertDoesNotExist()
        } finally { runBlocking { app.settingsRepository.save(themeMode = original.themeMode) } }
    }

    @Test fun longMenuTitleDoesNotOverlapItsSelectedIndicator() {
        var clicks = 0
        val title = "2026–2027 学年第一学期计算机科学与技术专业课表"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                CourseTableTheme { LiquidBackdropHost(Modifier.width(280.dp)) {
                    DropdownMenuItem(text = { Text(title) }, onClick = { clicks++ }, selected = true)
                } }
            }
        }
        val text = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val indicator = compose.onNodeWithTag("dropdown-selection-indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("A long menu title must leave room for the selection indicator", text.right <= indicator.left)
        compose.onNodeWithText(title).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
