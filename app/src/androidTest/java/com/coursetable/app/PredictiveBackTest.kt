package com.coursetable.app

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PredictiveBackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun progress(value: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        rule.runOnIdle {
            val event = BackEventCompat(30f, 250f, value, edge)
            if (value == 0f) rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(event)
            else rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(event)
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

    @Test fun progressTracksBothEdgesAndCancellationPreservesBusinessState() {
        lateinit var state: PredictiveBackState
        var writes = 0
        rule.setContent {
            state = rememberPredictiveBack(onBack = { writes++ })
            Box(Modifier.fillMaxSize().predictiveBackTransform(state, BackPresentation.PAGE))
        }
        progress(0f)
        progress(0.45f)
        rule.runOnIdle { assertEquals(0.45f, state.progress, 0.001f); assertEquals(0, writes) }
        cancel()
        rule.runOnIdle { assertEquals(0f, state.progress, 0.001f); assertFalse(state.busy); assertEquals(0, writes) }
        progress(0f, BackEventCompat.EDGE_RIGHT)
        progress(0.8f, BackEventCompat.EDGE_RIGHT)
        rule.runOnIdle { assertEquals(-1f, state.direction, 0f) }
        commit()
        rule.runOnIdle { assertEquals(1, writes); assertTrue(state.completed) }
        // A callback still mounted during its owner's teardown must not commit a second time.
        commit()
        rule.runOnIdle { assertEquals(1, writes) }
    }

    @Test fun rejectedBackRestoresBeforeConfirmationAndNeverWrites() {
        lateinit var state: PredictiveBackState
        var confirmations = 0
        var writes = 0
        rule.setContent {
            state = rememberPredictiveBack(canCommit = { false }, onRejected = {
                assertEquals(0f, state.progress, 0f)
                confirmations++
            }, onBack = { writes++ })
        }
        progress(0f); progress(0.7f); cancel()
        rule.runOnIdle { assertEquals(0, confirmations) }
        progress(0f); progress(0.7f); commit()
        rule.runOnIdle { assertEquals(1, confirmations); assertEquals(0, writes); assertFalse(state.completed) }
    }

    @Test fun retainedParentKeepsStateAndRootReleasesBackAfterClosingAndReopening() {
        var open by mutableStateOf(false)
        var closeCount = 0
        var parentCompositions = 0
        rule.setContent {
            LiquidBackdropHost {
                PredictivePageTransition(open, onBack = { closeCount++; open = false }) { child ->
                    if (child) Text("子页面", Modifier.testTag("child"))
                    else {
                        val retained = remember { ++parentCompositions }
                        Text("父页面 $retained", Modifier.testTag("parent"))
                    }
                }
            }
        }
        rule.runOnIdle { assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks()); open = true }
        rule.waitForIdle()
        rule.onNodeWithTag("parent").assertDoesNotExist()
        progress(0f); progress(0.6f); cancel()
        rule.onNodeWithTag("child").assertIsDisplayed()
        progress(0f); progress(0.6f); commit()
        rule.onNodeWithTag("parent").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(1, closeCount)
            assertEquals(1, parentCompositions)
            assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
            open = true
        }
        rule.waitForIdle()
        progress(0f); progress(0.4f); commit()
        rule.runOnIdle { assertEquals(2, closeCount); assertEquals(1, parentCompositions) }
    }

    @Test fun nestedDialogClosesBeforeSheetAndPage() {
        var sheet by mutableStateOf(true)
        var dialog by mutableStateOf(true)
        var sheetClosed = 0
        var dialogClosed = 0
        var pageClosed = 0
        rule.setContent {
            LiquidBackdropHost {
                rememberPredictiveBack(onBack = { pageClosed++ })
                if (sheet) ModalBottomSheet(onDismissRequest = { sheetClosed++; sheet = false }) {
                    Text("面板")
                    if (dialog) AlertDialog(onDismissRequest = { dialogClosed++; dialog = false },
                        title = { Text("确认框") }, confirmButton = {})
                }
            }
        }
        rule.waitForIdle()
        progress(0f); progress(0.5f); cancel()
        rule.runOnIdle { assertEquals(0, dialogClosed); assertEquals(0, sheetClosed) }
        progress(0f); progress(0.5f); commit()
        rule.runOnIdle { assertEquals(1, dialogClosed); assertEquals(0, sheetClosed); assertEquals(0, pageClosed) }
        progress(0f); progress(0.7f); commit()
        rule.runOnIdle { assertEquals(1, sheetClosed); assertEquals(0, pageClosed) }
    }

    @Test fun legacySheetGuardRunsOnlyAtCommitAndBlocksDismissal() {
        var guards = 0
        var closes = 0
        rule.setContent {
            LiquidBackdropHost {
                ModalBottomSheet(onDismissRequest = { closes++ }, canDismiss = { guards++; false }) { Text("未保存") }
            }
        }
        progress(0f); progress(0.8f)
        rule.runOnIdle { assertEquals(0, guards) }
        cancel()
        rule.runOnIdle { assertEquals(0, guards) }
        progress(0f); progress(0.8f); commit()
        rule.runOnIdle { assertEquals(1, guards); assertEquals(0, closes) }
    }

    @Test fun buttonBackWithoutProgressUsesOrdinaryClose() {
        var closes = 0
        lateinit var state: PredictiveBackState
        rule.setContent { state = rememberPredictiveBack(onBack = { closes++ }) }
        commit()
        rule.runOnIdle { assertEquals(1, closes); assertFalse(state.completed); assertEquals(0f, state.progress, 0f) }
    }

    @Test fun pageRevealsRetainedParentOnBothEdgesAndRestoresItsPixelsOnCancel() {
        rule.setContent {
            PredictivePageTransition(true, onBack = {}) { child ->
                Box(Modifier.fillMaxSize().background(if (child) Color.Red else Color.Blue))
            }
        }
        fun edgePixel(right: Boolean): Color {
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            return pixels[if (right) pixels.width - 2 else 1, pixels.height / 2]
        }
        progress(0f); progress(0.5f)
        assertTrue(edgePixel(false).blue > 0.8f)
        cancel()
        assertTrue(edgePixel(false).red > 0.99f)
        progress(0f, BackEventCompat.EDGE_RIGHT); progress(0.5f, BackEventCompat.EDGE_RIGHT)
        assertTrue(edgePixel(true).blue > 0.8f)
        cancel()
        assertTrue(edgePixel(true).red > 0.99f)
    }

    private fun assertParentDimmedDuringPreview(mode: ThemeMode) {
        var open by mutableStateOf(true)
        rule.setContent {
            CourseTableTheme(themeMode = mode) {
                PredictivePageTransition(open, onBack = { open = false }) { child ->
                    Box(Modifier.fillMaxSize().background(if (child) Color.Red else Color.White))
                }
            }
        }
        fun backgroundPixel(edge: Int): Color {
            val pixels = rule.onRoot().captureToImage().toPixelMap()
            return pixels[if (edge == BackEventCompat.EDGE_LEFT) 1 else pixels.width - 2, pixels.height / 2]
        }
        for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
            progress(0f, edge)
            for (value in listOf(0.25f, 0.95f)) {
                progress(value, edge)
                val pixel = backgroundPixel(edge)
                assertTrue("Retained parent must remain dim in $mode at progress $value: $pixel", pixel.green < 0.98f)
                assertTrue("Scrim must reveal the parent rather than black it out", pixel.green > 0.7f)
            }
            cancel()
            assertTrue(backgroundPixel(edge).red > 0.99f)
            assertTrue(backgroundPixel(edge).green < 0.01f)
        }
        progress(0f); progress(0.6f); commit()
        assertTrue("Completed return must remove the dim layer", backgroundPixel(BackEventCompat.EDGE_LEFT).green > 0.99f)
    }

    @Test fun lightPagePreviewDimsParentOnBothEdgesAndRemovesScrimOnCommit() = assertParentDimmedDuringPreview(ThemeMode.LIGHT)
    @Test fun darkPagePreviewDimsParentOnBothEdgesAndRemovesScrimOnCommit() = assertParentDimmedDuringPreview(ThemeMode.DARK)

    @Test fun keyboardClosesBeforeTheEditingLayer() {
        var keyboardVisible = false
        var closes = 0
        rule.runOnIdle {
            rule.activity.enableEdgeToEdge()
            rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        rule.setContent {
            val visible = rememberImeVisible()
            SideEffect { keyboardVisible = visible }
            rememberPredictiveBack(onBack = { closes++ })
            BasicTextField("课程名称", onValueChange = {}, modifier = Modifier.testTag("keyboard-field"))
        }
        rule.onNodeWithTag("keyboard-field").performClick()
        rule.waitUntil(5_000) { keyboardVisible }
        rule.runOnIdle { assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks()) }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
        rule.waitUntil(5_000) { !keyboardVisible }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, closes); assertTrue(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks()) }
        commit()
        rule.runOnIdle { assertEquals(1, closes) }
    }
}
