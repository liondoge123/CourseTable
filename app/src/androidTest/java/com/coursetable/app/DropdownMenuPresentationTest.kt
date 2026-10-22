package com.coursetable.app

import android.os.ParcelFileDescriptor
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.TimetableSelector
import com.coursetable.app.data.Timetable
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.LiquidTheme
import com.coursetable.app.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DropdownMenuPresentationTest {
    @get:Rule val compose = createComposeRule()
    private var activity: ComponentActivity? = null

    private fun show(mode: State<ThemeMode>, choices: Boolean, externalExpanded: MutableState<Boolean>? = null,
        onClosed: () -> Unit = {}, onActivate: (Int) -> Unit) {
        compose.setContent {
            val localExpanded = remember { mutableStateOf(false) }
            var expanded by (externalExpanded ?: localExpanded)
            var triggerHidden by remember { mutableStateOf(false) }
            val source = rememberMenuSource()
            val screen = LocalActivity.current as ComponentActivity
            SideEffect { activity = screen }
            var anchor by remember { mutableStateOf<Rect?>(null) }
            var selected by remember { mutableIntStateOf(0) }
            CourseTableTheme(themeMode = mode.value) {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    Box(Modifier.fillMaxSize().padding(top = 64.dp, end = 24.dp), contentAlignment = Alignment.TopEnd) {
                        IconButton({ expanded = true }, Modifier.testTag("menu-trigger")
                            .graphicsLayer { alpha = if (triggerHidden) 0f else 1f }.onGloballyPositioned { anchor = it.boundsInRoot() }) {
                            Icon(Icons.Filled.MoreHorizontal, "打开菜单", Modifier.size(20.dp).captureMenuSource(source, triggerHidden),
                                tint = LiquidTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DropdownMenu(expanded, { expanded = false }, anchor, DropdownMenuAlignment.END, sourceContent = source,
                        onSourceCovered = { triggerHidden = true },
                        onClosed = { triggerHidden = false; onClosed() }) {
                        val labels = if (choices) listOf("默认课表", "111") else listOf("重命名", "删除课表")
                        labels.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label, color = if (!choices && index == 1) LiquidTheme.colorScheme.error else LiquidTheme.colorScheme.onSurface) },
                                selected = if (choices) index == selected else null,
                                onClick = { selected = index; onActivate(index); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun menuBounds() = compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun drawInitialMenuFrame() {
        // The preparation stage waits for an Android draw, which the simulated Compose clock
        // alone cannot drive. Flush that draw before sampling motion relative to its start.
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.onRoot().captureToImage()
            if (compose.onAllNodesWithTag("anchored-menu-surface", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()) return
        }
        error("The menu did not start after its initial surface draw")
    }

    @Test fun timetableSwitchWaitsForTheTextTriggerToReturn() {
        var activeId by mutableStateOf(1L)
        var switches = 0
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        TimetableSelector(listOf(Timetable(1L, "旧课表"), Timetable(2L, "新课表")), activeId,
                            onSwitch = { activeId = it; switches++ })
                    }
                }
            }
        }
        compose.onNodeWithText("旧课表").performClick()
        compose.onNodeWithText("新课表").assertIsNotSelected()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("新课表").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { assertEquals(1L, activeId); assertEquals(0, switches) }
        capture("text-trigger-returning")
        compose.mainClock.advanceTimeBy(650)
        compose.runOnIdle { assertEquals(2L, activeId); assertEquals(1, switches) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("新课表").assertIsDisplayed()
        capture("text-trigger-restored")
    }

    @Test fun sourceGlyphBridgesBothEndsOfTheTransition() {
        show(mutableStateOf(ThemeMode.LIGHT), choices = false) {}
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("menu-trigger").performClick()
        drawInitialMenuFrame()
        compose.mainClock.advanceTimeBy(48)
        fun assertSourceInk() {
            val pixels = compose.onNodeWithTag("anchored-menu-source", useUnmergedTree = true).captureToImage().toPixelMap()
            val dot = pixels[pixels.width / 2, pixels.height / 2]
            val blank = pixels[pixels.width / 2, (pixels.height * 0.2f).toInt()]
            assertTrue("The retained source glyph must remain visible during the handoff",
                blank.red + blank.green + blank.blue - dot.red - dot.green - dot.blue > 0.15f)
        }
        assertSourceInk()
        capture("source-handoff-opening")
        compose.mainClock.advanceTimeBy(1100)
        compose.onRoot().performTouchInput { click(Offset(center.x, height - 1f)) }
        compose.mainClock.advanceTimeBy(240)
        assertSourceInk()
        capture("source-handoff-returning")
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
    }

    @Test fun menuSpringsPastItsRestingSizeAndShrinksBeforeClosing() {
        var closed = 0
        show(mutableStateOf(ThemeMode.LIGHT), choices = false, onClosed = { closed++ }) {}
        val triggerBounds = compose.onNodeWithTag("menu-trigger").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("menu-trigger").performClick()
        drawInitialMenuFrame()
        val first = menuBounds()
        compose.mainClock.advanceTimeBy(32)
        capture("motion-open-start")
        val widths = mutableListOf<Float>()
        repeat(36) {
            compose.mainClock.advanceTimeByFrame()
            widths += menuBounds().width
        }
        compose.mainClock.advanceTimeBy(600)
        val settled = menuBounds()
        assertEquals("The menu corner must cover its trigger", triggerBounds.top, settled.top, 1f)
        assertEquals("Right alignment stays attached to the trigger", triggerBounds.right, settled.right, 1f)
        assertTrue("The source center must be inside the menu", settled.contains(triggerBounds.center))
        assertTrue("Menu must grow from its trigger", first.width < settled.width)
        assertTrue("Opening must have a small spring overshoot", widths.max() > settled.width * 1.003f)
        capture("motion-open-settled")
        compose.onRoot().performTouchInput { click(Offset(center.x, height - 1f)) }
        compose.mainClock.advanceTimeBy(80)
        assertEquals("Completion waits for the shrink", 0, closed)
        assertTrue("Dismissal must shrink the menu before removing it", menuBounds().width < settled.width)
        capture("motion-close-middle")
        compose.mainClock.advanceTimeBy(160)
        capture("motion-return-to-trigger")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closed) }
        compose.mainClock.autoAdvance = true
        capture("motion-trigger-restored")
    }

    @Test fun reversingAnUnfinishedCloseKeepsTheMenuAndDoesNotCompleteTheOldDismissal() {
        val expanded = mutableStateOf(false)
        var closed = 0
        show(mutableStateOf(ThemeMode.LIGHT), choices = false, externalExpanded = expanded,
            onClosed = { closed++ }) {}
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded.value = true }
        drawInitialMenuFrame()
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { expanded.value = false }
        compose.mainClock.advanceTimeBy(48)
        val interrupted = menuBounds()
        compose.runOnIdle { expanded.value = true }
        compose.mainClock.advanceTimeByFrame()
        val resumed = menuBounds()
        assertTrue("Reopening must continue from the current pose", kotlin.math.abs(resumed.width - interrupted.width) < interrupted.width * 0.1f)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("重命名").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, closed); expanded.value = false }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, closed) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun cancelledPredictiveBackRestoresTheMenuAndCommittedBackClosesOnce() {
        var closed = 0
        show(mutableStateOf(ThemeMode.LIGHT), choices = false, onClosed = { closed++ }) {}
        compose.onNodeWithTag("menu-trigger").performClick()
        val resting = menuBounds()
        fun beginBack() {
            compose.runOnIdle {
                activity!!.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                activity!!.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 300f, 0.65f, BackEventCompat.EDGE_LEFT))
            }
            compose.waitForIdle()
        }
        beginBack()
        assertTrue(menuBounds().width < resting.width)
        compose.runOnIdle { activity!!.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(resting.width, menuBounds().width, 0.5f)
        compose.runOnIdle { assertEquals(0, closed) }
        beginBack()
        compose.runOnIdle { activity!!.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closed) }
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("uiScreenshotDir") ?: return
        require(Regex("^/data/local/tmp/coursetable-testing/[a-zA-Z0-9-]+$").matches(directory))
        compose.onRoot().captureToImage()
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p $directory/$name.png")
        ).use { it.readBytes() }
    }

    @Test fun choiceMenuKeepsOneSelectionInBothThemes() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val activations = mutableListOf<Int>()
        show(mode, choices = true) { activations += it }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEachIndexed { index, theme ->
            compose.runOnIdle { mode.value = theme }
            compose.onNodeWithTag("menu-trigger").performClick()
            val labels = listOf("默认课表", "111")
            compose.onNodeWithText(labels[index]).assertIsSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            compose.onAllNodesWithTag("dropdown-selection-indicator", useUnmergedTree = true).assertCountEquals(1)
            val before = compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).captureToImage().toPixelMap()
            val bounds = compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("Short menus should stay compact", bounds.right - bounds.left < 184.dp && bounds.bottom - bounds.top <= 105.dp)
            capture("choice-menu-${theme.key}")
            compose.onNodeWithText(labels[1 - index]).assertIsNotSelected().assertHeightIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(index + 1, activations.size) }
            compose.onNodeWithTag("menu-trigger").performClick()
            compose.onNodeWithText(labels[1 - index]).assertIsSelected()
            val after = compose.onNodeWithTag("anchored-menu-surface", useUnmergedTree = true).captureToImage().toPixelMap()
            // Sample blank space inside the first row, away from the label and checkmark.
            val firstColor = before[(before.width * 0.06f).toInt(), (before.height * 0.27f).toInt()]
            val nextColor = after[(after.width * 0.06f).toInt(), (after.height * 0.27f).toInt()]
            val change = maxOf(kotlin.math.abs(firstColor.red - nextColor.red),
                kotlin.math.abs(firstColor.green - nextColor.green), kotlin.math.abs(firstColor.blue - nextColor.blue))
            assertTrue("Selection must not leave a pressed background", change < 0.02f)
            capture("choice-menu-rest-${theme.key}")
            compose.onRoot().performTouchInput { click(Offset(center.x, height - 1f)) }
        }
    }

    @Test fun actionMenuHasOnlyPressedFeedbackAndActivatesOnceInBothThemes() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val activations = mutableListOf<Int>()
        show(mode, choices = false) { activations += it }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEachIndexed { index, theme ->
            compose.runOnIdle { mode.value = theme }
            compose.onNodeWithTag("menu-trigger").performClick()
            compose.onNodeWithText("重命名").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText("删除课表").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            compose.onNodeWithTag("dropdown-selection-indicator", useUnmergedTree = true).assertDoesNotExist()
            capture("action-menu-${theme.key}")
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("重命名").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(250)
            capture("action-menu-pressed-${theme.key}")
            compose.onNodeWithText("重命名").performTouchInput { up() }
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { assertEquals(List(index + 1) { 0 }, activations) }
        }
    }
}
