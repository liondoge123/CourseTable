package com.coursetable.app

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.coursetable.app.ui.liquid.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PredictiveBackContinuityTest {
    private val motionScale = object : MotionDurationScale {
        var factor = 1f
        override val scaleFactor: Float get() = factor
    }
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>(effectContext = motionScale)
    private lateinit var state: PredictiveBackState
    private var closes = 0
    private var rejected = 0

    private fun show(canCommit: Boolean = true) {
        rule.setContent {
            state = rememberPredictiveBack(canCommit = { canCommit }, onRejected = {
                assertEquals(0f, state.progress, 0f)
                rejected++
            }, onBack = { closes++ })
            Box(Modifier.fillMaxSize().background(Color.Red).predictiveBackTransform(state, BackPresentation.PAGE))
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun gesture(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT, start: Boolean = false) {
        rule.runOnIdle {
            val dispatcher = rule.activity.onBackPressedDispatcher
            if (start) dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, edge))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 340f, progress, edge))
        }
        rule.waitForIdle()
    }
    private fun cancel() {
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
    }
    private fun commit() {
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }
    private fun settle() {
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun rejectedCommitRestoresFromItsVisiblePoseBeforeNotifying() {
        show(canCommit = false)
        gesture(.7f, start = true)
        commit()
        rule.runOnIdle {
            assertEquals(BackPhase.RESTORING, state.phase)
            assertEquals(.7f, state.progress, .001f)
            assertEquals(0, rejected)
        }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertTrue(state.progress > 0f); assertEquals(0, rejected) }
        settle()
        rule.runOnIdle { assertEquals(1, rejected); assertEquals(0, closes) }
    }

    @Test fun cancellationAtDifferentDepthsRestoresContinuouslyOnBothEdges() {
        show()
        for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
            for (depth in listOf(.2f, .7f, .95f)) {
                gesture(depth, edge, start = true)
                cancel()
                var previous = depth
                repeat(35) {
                    rule.mainClock.advanceTimeByFrame()
                    rule.runOnIdle {
                        assertTrue("Restore cannot jump outward", state.progress <= previous + .001f)
                        assertTrue(state.progress >= 0f)
                        previous = state.progress
                    }
                }
                settle()
                rule.runOnIdle {
                    assertEquals(0f, state.progress, 0f)
                    assertEquals(0f, state.touchDeltaY, 0f)
                    assertEquals(BackPhase.IDLE, state.phase)
                    assertEquals(0, closes)
                }
                rule.mainClock.autoAdvance = false
            }
        }
    }

    @Test fun anotherGestureCanTakeOverRestorationFromEitherEdgeWithoutAPoseJump() {
        show(canCommit = false)
        for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
            gesture(.75f, start = true)
            commit() // A pending rejected callback must also be cancelled by takeover.
            rule.mainClock.advanceTimeBy(32)
            lateinit var before: Triple<Float, Float, Float>
            rule.runOnIdle { before = Triple(state.progress, state.direction, state.touchDeltaY) }
            gesture(.02f, edge, start = true)
            rule.runOnIdle {
                assertEquals(BackPhase.TRACKING, state.phase)
                assertEquals(before.first, state.progress, .0001f)
                assertEquals(before.second, state.direction, .0001f)
                assertEquals(before.third, state.touchDeltaY, .0001f)
            }
            gesture(.6f, edge)
            rule.runOnIdle { assertTrue(state.progress > before.first); assertEquals(0, rejected) }
            cancel()
            settle()
            rule.runOnIdle { assertEquals(0, rejected); assertEquals(0, closes) }
            rule.mainClock.autoAdvance = false
        }
    }

    @Test fun reverseProgressKeepsItsSignedVelocity() {
        show()
        rule.runOnIdle {
            val session = state.start()!!
            state.update(session, BackEventCompat(0f, 300f, .7f, 0), 1000L)
            state.update(session, BackEventCompat(0f, 300f, .5f, 0), 1020L)
            assertTrue(state.progressVelocity < 0f)
            state.restore(session)
        }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertTrue(state.progress < .5f) }
        settle()
    }

    @Test fun cancellationAndANewStartInTheSameFrameKeepTheVisiblePose() {
        show()
        gesture(.7f, start = true)
        rule.runOnIdle {
            val dispatcher = rule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackCancelled()
            dispatcher.dispatchOnBackStarted(BackEventCompat(1080f, 340f, 0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(1050f, 340f, .02f, BackEventCompat.EDGE_RIGHT))
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(BackPhase.TRACKING, state.phase)
            assertEquals(.7f, state.progress, .0001f)
            assertEquals(1f, state.direction, .0001f)
        }
        cancel()
        settle()
    }

    @Test fun removingAnOwnerCancelsItsPendingConfirmation() {
        var mounted by mutableStateOf(true)
        rule.setContent {
            if (mounted) {
                state = rememberPredictiveBack(canCommit = { false }, onRejected = { rejected++ }, onBack = { closes++ })
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        gesture(.8f, start = true)
        commit()
        rule.runOnIdle { mounted = false }
        rule.mainClock.advanceTimeByFrame()
        settle()
        rule.runOnIdle { assertEquals(0, rejected); assertEquals(0, closes) }
    }

    @Test fun sheetRejectionNotifiesAfterRestorationAndStillWorksForAnOutsideTap() {
        rule.setContent {
            LiquidBackdropHost {
                ModalBottomSheet(onDismissRequest = { closes++ }, canDismiss = { false },
                    onDismissRejected = { rejected++ }) { Text("未保存的课程") }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        gesture(.8f, start = true)
        commit()
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertEquals(0, rejected); assertEquals(0, closes) }
        settle()
        rule.runOnIdle { assertEquals(1, rejected); assertEquals(0, closes) }
        rule.onRoot().performTouchInput { click(Offset(10f, 10f)) }
        rule.runOnIdle { assertEquals(2, rejected); assertEquals(0, closes) }
    }

    @Test fun zeroMotionScaleFinishesAndRestoresWithoutWaiting() {
        motionScale.factor = 0f
        show()
        gesture(.6f, start = true)
        cancel()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.runOnIdle { assertEquals(BackPhase.IDLE, state.phase); assertEquals(0f, state.progress, 0f) }
        gesture(.6f, start = true)
        commit()
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        rule.runOnIdle { assertEquals(1, closes); assertTrue(state.completed) }
        commit()
        rule.runOnIdle { assertEquals(1, closes) }
    }

    @Test fun dockReturnsDuringConfirmedSheetExitButStaysHiddenForCancelledBack() {
        lateinit var overlays: GlassOverlayController
        var open by mutableStateOf(true)
        rule.setContent {
            LiquidBackdropHost(Modifier.fillMaxSize()) {
                overlays = checkNotNull(LocalGlassOverlayController.current)
                if (open) ModalBottomSheet(onDismissRequest = { closes++; open = false }) {
                    Text("sheet")
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        gesture(.6f, start = true)
        cancel()
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { assertTrue(overlays.hidesDock); assertEquals(0, closes) }
        settle()
        rule.mainClock.autoAdvance = false
        gesture(.6f, start = true)
        commit()
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle {
            assertTrue(open)
            assertTrue(overlays.entries.isNotEmpty())
            assertFalse("Dock must return before the committed exit completes", overlays.hidesDock)
            assertEquals(0, closes)
        }
        settle()
        rule.runOnIdle { assertEquals(1, closes); assertFalse(overlays.hidesDock) }
    }

    private fun verifyPageDockReturn(gestureBack: Boolean) {
        lateinit var overlays: GlassOverlayController
        var open by mutableStateOf(true)
        var mounted = true
        rule.setContent {
            LiquidBackdropHost(Modifier.fillMaxSize()) {
                overlays = checkNotNull(LocalGlassOverlayController.current)
                PredictivePageTransition(open, onBack = { open = false },
                    onMountedChanged = { mounted = it }) { child ->
                    if (child) Box(Modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(overlays.hidesDock) }
        rule.mainClock.autoAdvance = false
        if (gestureBack) {
            gesture(.6f, start = true)
            cancel()
            rule.mainClock.advanceTimeBy(32)
            rule.runOnIdle { assertTrue(overlays.hidesDock) }
            settle()
            rule.mainClock.autoAdvance = false
            gesture(.6f, start = true)
            commit()
        } else rule.runOnIdle { open = false }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle {
            assertTrue("Page remains mounted until its exit finishes", mounted)
            assertFalse("Dock returns alongside the page exit", overlays.hidesDock)
        }
        settle()
        rule.runOnIdle { assertFalse(mounted); assertFalse(overlays.hidesDock) }
    }

    @Test fun pageButtonBackRestoresDockBeforeUnmounting() = verifyPageDockReturn(false)
    @Test fun pageGestureBackRestoresDockAtCommit() = verifyPageDockReturn(true)

    @Test fun aDescendantCommitOnlySkipsTheExitOfAPageItActuallyCloses() {
        var open by mutableStateOf(true)
        var nested by mutableStateOf(true)
        var mounted = true
        rule.setContent {
            PredictivePageTransition(open, onBack = { open = false }, onMountedChanged = { mounted = it }) { child ->
                if (child) {
                    rememberPredictiveBack(enabled = nested, onBack = { nested = false })
                    Box(Modifier.fillMaxSize().testTag("retained-child"))
                }
            }
        }
        gesture(.6f, start = true)
        commit()
        rule.runOnIdle { assertTrue(open); assertTrue(mounted); assertFalse(nested) }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { open = false }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertTrue("Unrelated completion cannot skip a later ordinary exit", mounted) }
        rule.onNodeWithTag("retained-child").assertExists()
        settle()
        rule.runOnIdle { assertFalse(mounted) }
    }
}
