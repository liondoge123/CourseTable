package com.coursetable.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.liquid.DampedDragAnimation
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.LiquidNavigationTabs
import com.coursetable.app.ui.liquid.LiquidAmbientBackground
import com.coursetable.app.ui.liquid.LiquidMetaballNavigationDock
import com.coursetable.app.ui.liquid.GlassCapability
import com.coursetable.app.ui.liquid.LocalGlassCapability
import com.coursetable.app.ui.liquid.NavigationMotionDiagnostics
import com.coursetable.app.ui.liquid.glassBackdropSource
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.CourseTableTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import android.util.Log
import kotlin.math.abs

class LiquidNavigationAnimationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var animation: DampedDragAnimation
    private lateinit var reference: DampedDragAnimation

    private fun showAnimation() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            animation = remember {
                DampedDragAnimation(scope, 0f, 0f..3f, 0.001f, 1f, 78f / 56f)
            }
            reference = remember {
                DampedDragAnimation(scope, 0f, 0f..3f, 0.001f, 1f, 78f / 56f)
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test fun committingTheHeldTargetPreservesTheUninterruptedTrajectory() {
        showAnimation()
        val events = mutableListOf<String>()
        try {
            compose.runOnIdle {
                NavigationMotionDiagnostics.listener = { name, _, _ -> events += name }
                animation.press()
                animation.movePressedToValue(3f)
                reference.press()
                reference.movePressedToValue(3f)
            }
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle { animation.commitToValue(3f) }
            repeat(18) {
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle { assertEquals(reference.value, animation.value, 0.00001f) }
            }
            compose.runOnIdle {
                assertEquals("Pointer-up must not issue another press", 2, events.count { it == "lens.press" })
                assertEquals("Pointer-up must not restart the same position target", 2, events.count { it == "lens.move" })
            }
        } finally {
            compose.runOnIdle { NavigationMotionDiagnostics.listener = null }
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun pressedScaleAndPanelDragDoNotRecomposeTabContentsEveryFrame() {
        var commits = 0
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost {
                    LiquidNavigationTabs(0, {}, 4,
                        modifier = Modifier.width(280.dp).height(54.dp).testTag("dock")) { _, scale, _ ->
                        SideEffect { commits++ }
                        repeat(4) {
                            Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                                scaleX = scale(); scaleY = scale()
                            })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("dock").performTouchInput { down(Offset(width * 0.4f, centerY)) }
        compose.mainClock.advanceTimeByFrame()
        var before = 0
        compose.runOnIdle { before = commits }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertEquals("Scale frames must reuse the tab composition", before, commits) }
        compose.onNodeWithTag("dock").performTouchInput {
            moveTo(Offset(width * 0.7f, centerY), delayMillis = 32)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { before = commits }
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { assertEquals("Panel movement must reuse the tab composition", before, commits) }
        compose.onNodeWithTag("dock").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
    }

    @Test fun delayedParentSelectionCannotPullTheCommittedLensBackToItsSource() {
        var selected by mutableIntStateOf(0)
        val requests = mutableListOf<Int>()
        val samples = mutableListOf<List<Float>>()
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost {
                    LiquidNavigationTabs(selected, { requests += it }, 4,
                        modifier = Modifier.width(280.dp).height(54.dp).testTag("dock")) { _, _, _ ->
                        repeat(4) { Box(Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle {
                NavigationMotionDiagnostics.listener = { name, values, _ -> if (name == "lens") samples += values }
            }
            compose.onNodeWithTag("dock").performTouchInput { down(Offset(width * 0.625f, centerY)) }
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithTag("dock").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(128)
            compose.runOnIdle {
                assertEquals(listOf(2), requests)
                assertEquals("The stale source must not replace the submitted target", 2f, samples.last()[1], 0f)
                selected = 2
            }
            compose.mainClock.advanceTimeBy(128)
            compose.runOnIdle {
                assertTrue("The source must never be reintroduced after a press", samples.filter { it[2] > 0f }.all { it[1] == 2f })
            }
        } finally {
            compose.runOnIdle { NavigationMotionDiagnostics.listener = null }
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun companionMotionKeepsTabsMeasuredAndTheirContentComposed() = checkCompanion(LayoutDirection.Ltr)

    @Test fun rtlCompanionMotionKeepsTheActionHitBoxOnTheRenderedBulb() = checkCompanion(LayoutDirection.Rtl)

    @Test fun narrowCompanionMotionKeepsTheGroupCentered() = checkCompanion(LayoutDirection.Ltr, 300)

    private fun checkCompanion(direction: LayoutDirection, availableWidth: Int = 400) {
        var visible by mutableStateOf(false)
        var tabCommits = 0
        var tabMeasures = 0
        var actions = 0
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost {
                    CompositionLocalProvider(LocalGlassCapability provides GlassCapability.STATIC,
                        LocalLayoutDirection provides direction) {
                        LiquidMetaballNavigationDock(visible, { actions++ },
                            modifier = Modifier.width(availableWidth.dp).testTag("metaball")) { width, _ ->
                            SideEffect { tabCommits++ }
                            Box(Modifier.width(width).height(54.dp).testTag("tabs")
                                .layout { measurable, constraints ->
                                    tabMeasures++
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val hiddenLeft = compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle { visible = true }
        compose.mainClock.advanceTimeBy(80)
        var commits = 0
        var measures = 0
        compose.runOnIdle { commits = tabCommits; measures = tabMeasures }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals("A bridge frame must not recompose tabs", commits, tabCommits)
            assertEquals("A bridge frame must not remeasure tabs", measures, tabMeasures)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val shownLeft = compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot.left
        val density = compose.density.density
        val dockWidth = (availableWidth - 66f).coerceIn(248f, 280f)
        val shift = ((dockWidth + 66f).coerceAtMost(availableWidth.toFloat()) - dockWidth) / 2f
        assertEquals("The full group stays centered", shift * density, hiddenLeft - shownLeft, 1f)
        compose.onNodeWithContentDescription("添加课程").performTouchInput { click() }
        compose.runOnIdle { assertEquals("The action hit box follows the rendered bulb", 1, actions) }
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("添加课程").assertDoesNotExist()
        assertEquals(hiddenLeft, compose.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test fun retargetingAHeldLensKeepsItsSpringMomentum() {
        showAnimation()
        compose.runOnIdle {
            animation.press()
            animation.updateValue(3f)
        }
        compose.mainClock.advanceTimeBy(80)
        var before = 0f
        compose.runOnIdle {
            before = animation.value
            animation.updateValue(2.9f)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            val movement = animation.value - before
            Log.i("LiquidLensMotion", "retargetMovement=$movement before=$before after=${animation.value}")
            assertTrue("A new finger position must not reset spring velocity or stall a frame", movement > 0.01f)
        }
    }

    @Test fun anotherPressKeepsTheOriginalScaleSpringMomentum() {
        showAnimation()
        compose.runOnIdle { animation.press() }
        compose.mainClock.advanceTimeBy(80)
        var before = 0f
        compose.runOnIdle { before = animation.scaleX; animation.press() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertTrue("A repeated press must preserve the scale spring's velocity", animation.scaleX > before + 0.005f)
        }
    }

    @Test fun previousReleaseCannotShrinkANewHeldPress() {
        showAnimation()
        compose.runOnIdle { animation.animateToValue(3f) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            animation.press()
            animation.movePressedToValue(1f)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle {
            assertEquals(1f, animation.value, 0.001f)
            assertEquals(1f, animation.pressProgress, 0.001f)
            assertEquals(78f / 56f, animation.scaleX, 0.001f)
            assertEquals(78f / 56f, animation.scaleY, 0.001f)
            animation.animateToValue(1f)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle {
            assertEquals(0f, animation.pressProgress, 0.001f)
            assertEquals(1f, animation.scaleX, 0.001f)
            assertEquals(1f, animation.scaleY, 0.001f)
        }
    }

    @Test fun pressingDuringThePreviousShrinkAlsoKeepsTheLensExpanded() {
        showAnimation()
        compose.runOnIdle { animation.animateToValue(0f) }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle {
            animation.press()
            animation.movePressedToValue(2f)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle {
            assertEquals(2f, animation.value, 0.001f)
            assertEquals(1f, animation.pressProgress, 0.001f)
            assertEquals(78f / 56f, animation.scaleX, 0.001f)
        }
    }

    @Test fun changingSelectionDuringAHeldDockGestureKeepsItsReleaseAndLatestCallback() {
        var selected by mutableIntStateOf(0)
        var callbackVersion by mutableIntStateOf(0)
        val selections = mutableListOf<Pair<Int, Int>>()
        compose.setContent {
            val version = callbackVersion
            CourseTableTheme {
                LiquidBackdropHost {
                    LiquidNavigationTabs(
                        selectedIndex = selected,
                        onSelected = { selections += version to it; selected = it },
                        tabCount = 4,
                        modifier = Modifier.width(280.dp).height(54.dp).testTag("dock")
                    ) { _, _, _ ->
                        repeat(4) { Box(Modifier.weight(1f).height(48.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("dock").performTouchInput {
            down(Offset(width * 0.625f, centerY))
        }
        compose.runOnIdle { selected = 1; callbackVersion = 1 }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("dock").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(1 to 2), selections)
            assertEquals(2, selected)
        }
    }

    @Test fun heldDockDragMovesTheRenderedLensBeforeSelectingAPage() {
        var selected by mutableIntStateOf(0)
        val selections = mutableListOf<Int>()
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.width(280.dp).height(54.dp)) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    LiquidNavigationTabs(selected, { selected = it; selections += it }, 4,
                        modifier = Modifier.fillMaxSize().testTag("dock")) { color, _, _ ->
                        repeat(4) { index ->
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Text(index.toString(), color = color)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("dock").performTouchInput { down(Offset(width * 0.125f, centerY)) }
        compose.mainClock.advanceTimeBy(160)
        val first = compose.onNodeWithTag("dock").captureToImage().toPixelMap()
        compose.onNodeWithTag("dock").performTouchInput { moveTo(Offset(width * 0.78f, centerY), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(64)
        val dragged = compose.onNodeWithTag("dock").captureToImage().toPixelMap()
        var changed = 0
        for (y in 4 until first.height - 4 step 4) for (x in 4 until first.width - 4 step 4) {
            val a = first[x, y]
            val b = dragged[x, y]
            if (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.06f) changed++
        }
        assertTrue("The held lens must visibly follow the drag", changed > 10)
        compose.runOnIdle { assertTrue("Page selection waits for release", selections.isEmpty()) }
        compose.onNodeWithTag("dock").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(3), selections) }
    }
}
