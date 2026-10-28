package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.*
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsChoiceItemTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("uiScreenshotDir") ?: return
        require(directory.matches(Regex("^/data/local/tmp/coursetable-testing/[a-zA-Z0-9-]+$")))
        compose.onRoot().captureToImage()
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p $directory/$name.png")).use { it.readBytes() }
    }

    private fun showTouchChoices(scrollable: Boolean = false) {
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    Column(Modifier.fillMaxSize().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                        Spacer(Modifier.height(96.dp))
                        SettingsChoiceItem("card-align", Icons.Filled.FormatAlignLeft, "课程卡片文字对齐", "center",
                            listOf(SettingsChoice("center", "居中"), SettingsChoice("left", "靠左")), onSelect = {})
                        Spacer(Modifier.height(1000.dp))
                    }
                }
            }
        }
    }

    @Test fun touchPositionMovesTheMenuAndRightEdgeStaysOnScreen() {
        showTouchChoices()
        val row = compose.onNodeWithTag("settings-choice-card-align")
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        val positions = listOf(rowBounds.width * 0.35f, rowBounds.width * 0.6f, 8f * density, rowBounds.width - 8f * density)
        val leftEdges = mutableListOf<Float>()
        val topEdges = mutableListOf<Float>()
        positions.forEachIndexed { index, x ->
            val verticalFraction = listOf(0.25f, 0.5f, 0.75f, 0.5f)[index]
            row.performTouchInput { click(Offset(x, height * verticalFraction)) }
            val menu = compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val touch = Offset(rowBounds.left + x, rowBounds.top + rowBounds.height * verticalFraction)
            if (index < 2) assertEquals("The menu's top center follows the touch", touch.x, menu.center.x, 1f)
            assertTrue("The menu opens just below the touched point", menu.top >= touch.y && menu.top - touch.y < 8f * density)
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("The menu stays within the left safe edge", menu.left >= root.left + 12f * density - 1f)
            assertTrue("The menu stays within the right safe edge", menu.right <= root.right - 12f * density + 1f)
            leftEdges += menu.left
            topEdges += menu.top
            capture("settings-touch-$index")
            compose.onRoot().performTouchInput { click(Offset(width / 2f, height - 1f)) }
        }
        assertTrue("Two different touches cannot use one fixed trailing anchor", leftEdges[1] - leftEdges[0] > 40f * density)
        assertTrue("The menu follows the vertical touch as well", topEdges[1] - topEdges[0] > 10f * density)
    }

    @Test fun centeredMenuKeepsItsTopCenterAtTheTouchWhileOpening() {
        showTouchChoices()
        val row = compose.onNodeWithTag("settings-choice-card-align")
        val touch = row.fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        row.performTouchInput { click(center) }
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.onRoot().captureToImage()
        }
        val widths = mutableListOf<Float>()
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            val menu = compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("Horizontal expansion stays centered on the touch", touch.x, menu.center.x, 1.5f)
            assertTrue("The top edge must not travel down with the menu center", kotlin.math.abs(menu.top - touch.y) < 8f * compose.density.density)
            widths += menu.width
        }
        assertTrue("These assertions must sample actual growing frames", widths.max() - widths.min() > 8f * compose.density.density)
        compose.mainClock.advanceTimeBy(1000)
        capture("settings-centered-open")
        compose.mainClock.autoAdvance = true
    }

    @Test fun draggingDoesNotOpenTheMenuOrLeaveAStaleTouchForAccessibility() {
        showTouchChoices(scrollable = true)
        val row = compose.onNodeWithTag("settings-choice-card-align")
        row.performTouchInput {
            down(center)
            moveBy(Offset(0f, -100f))
            cancel()
        }
        compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).assertDoesNotExist()
        row.performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        val value = compose.onNodeWithTag("settings-choice-value-card-align", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("Accessibility activation uses the value, not an earlier cancelled touch", value.right, menu.right, 1f)
        assertTrue(menu.top >= value.bottom)
    }

    @Test fun trailingValueAndChoiceMenuRemainReadableInBothThemesAtLargeFont() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        var selected by mutableStateOf("center")
        var changes = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                CourseTableTheme(themeMode = mode.value) {
                    LiquidBackdropHost(Modifier.width(320.dp).fillMaxHeight()) {
                        LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                        Column(Modifier.padding(top = 48.dp)) {
                            SettingsChoiceItem("card-align", Icons.Filled.FormatAlignLeft, "课程卡片文字对齐", selected,
                                listOf(SettingsChoice("center", "居中"), SettingsChoice("left", "靠左")),
                                onSelect = { selected = it; changes++ })
                        }
                    }
                }
            }
        }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEachIndexed { index, theme ->
            compose.runOnIdle { mode.value = theme }
            val row = compose.onNodeWithTag("settings-choice-card-align")
            // An accessibility action has no pointer coordinates; use its value fallback.
            row.assertHeightIsAtLeast(58.dp).performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithTag("overlay-glass-dialog").assertDoesNotExist()
            val value = compose.onNodeWithTag("settings-choice-value-card-align", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val menu = compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("The current value stays above the menu", menu.top >= value.bottom)
            assertEquals(value.right, menu.right, 1f)
            compose.onNodeWithTag("card-align-$selected").assertIsSelected()
            compose.onAllNodesWithTag("dropdown-selection-indicator", useUnmergedTree = true).assertCountEquals(1)
            capture("settings-align-menu-${theme.key}")
            val next = if (selected == "center") "left" else "center"
            compose.onNodeWithTag("card-align-$next").assertHeightIsAtLeast(48.dp).performClick()
            compose.waitUntil(3000) { selected == next }
            compose.runOnIdle { assertEquals(index + 1, changes) }
            row.assertIsDisplayed()
            capture("settings-align-row-${theme.key}")
        }
    }

    private fun assertChoiceAppliesWhileTheMenuIsClosing(theme: Boolean) {
        val id = if (theme) "theme-mode" else "card-align"
        val initial = if (theme) "light" else "center"
        val next = if (theme) "dark" else "left"
        var selected by mutableStateOf(initial)
        var changes = 0
        compose.setContent {
            CourseTableTheme(themeMode = if (theme) ThemeMode.fromKey(selected) else ThemeMode.LIGHT) {
                // Keep callback timing independent of the graphics backend.
                CompositionLocalProvider(LocalGlassCapability provides GlassCapability.STATIC) {
                    LiquidBackdropHost(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(top = 96.dp)) {
                            SettingsChoiceItem(id, Icons.Filled.Settings, if (theme) "主题模式" else "课程卡片文字对齐", selected,
                                if (theme) listOf(SettingsChoice("light", "浅色"), SettingsChoice("dark", "深色"))
                                else listOf(SettingsChoice("center", "居中"), SettingsChoice("left", "靠左")),
                                onSelect = { selected = it; changes++ })
                            Text("当前：$selected", Modifier.testTag("applied-choice"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("settings-choice-$id").performClick()
        compose.onNodeWithTag("$id-$next").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("$id-$next").performClick()
        compose.runOnIdle {
            assertEquals("Selection must be committed in the click callback, before any closing frame", next, selected)
            assertEquals(1, changes)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("applied-choice", useUnmergedTree = true).assertTextEquals("当前：$next")
        compose.onNodeWithTag("$id-$next").assertIsSelected()
        // Exiting menu content must not commit another selection on a repeated tap.
        compose.onNodeWithTag("$id-$initial").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(next, selected); assertEquals(1, changes) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test fun alignmentAppliesBeforeTheMenuFinishesShrinking() = assertChoiceAppliesWhileTheMenuIsClosing(false)

    @Test fun themeAppliesBeforeTheMenuFinishesShrinking() = assertChoiceAppliesWhileTheMenuIsClosing(true)

    @Test fun alignmentSettingAppliesOnlyTheChosenValueAndOutsideTapKeepsIt() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
        val original = runBlocking { app.settingsRepository.settings.first() }
        try {
            compose.setContent { CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) { SettingsScreen() } } }
            compose.onNodeWithText("课程卡片设置").performScrollTo().performClick()
            compose.onNodeWithText("课程卡片文字对齐").performScrollTo().performClick()
            val initialKey = if (original.cardAlignLeft) "left" else "center"
            compose.onNodeWithTag("card-align-$initialKey").assertIsSelected()
            compose.onRoot().performTouchInput { click(Offset(width / 2f, height - 1f)) }
            compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).assertDoesNotExist()
            assertEquals(original.cardAlignLeft, runBlocking { app.settingsRepository.settings.first().cardAlignLeft })
            compose.onNodeWithText("课程卡片文字对齐").performClick()
            val nextKey = if (original.cardAlignLeft) "center" else "left"
            compose.onNodeWithTag("card-align-$nextKey").performClick()
            compose.waitUntil(5000) { runBlocking { app.settingsRepository.settings.first().cardAlignLeft != original.cardAlignLeft } }
            capture("settings-align-applied")
        } finally { runBlocking { app.settingsRepository.save(cardAlignLeft = original.cardAlignLeft) } }
    }
}
