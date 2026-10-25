package com.coursetable.app

import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** System edge gestures and Choreographer frames, without a Compose test clock. */
class NativePredictiveBackTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val state = AtomicReference<PredictiveBackState>()
    private val mounted = AtomicBoolean(false)
    private val focused = AtomicBoolean(false)
    private val closes = AtomicInteger(0)
    private val rejections = AtomicInteger(0)
    private val parentCompositions = AtomicInteger(0)
    private val frames = CopyOnWriteArrayList<Pair<BackPhase, Float>>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var width = 0f
    private var height = 0f
    private var down = 0L
    private var held = false

    private fun show(canCommit: Boolean = true) {
        activity.scenario.onActivity { screen ->
            screen.enableEdgeToEdge()
            width = screen.resources.displayMetrics.widthPixels.toFloat()
            height = screen.resources.displayMetrics.heightPixels.toFloat()
            screen.setContent {
                var open by remember { mutableStateOf(true) }
                CourseTableTheme {
                    LiquidBackdropHost {
                        PredictivePageTransition(open, onBack = { open = false }, onMountedChanged = { mounted.set(it) }) { child ->
                            if (child) {
                                val back = rememberPredictiveBack(canCommit = { canCommit },
                                    onRejected = { rejections.incrementAndGet() },
                                    onBack = { closes.incrementAndGet(); open = false })
                                SideEffect { state.set(back) }
                                DisposableEffect(back) {
                                    val clock = Choreographer.getInstance()
                                    val callback = object : Choreographer.FrameCallback {
                                        override fun doFrame(frameTimeNanos: Long) {
                                            frames.add(back.phase to back.progress)
                                            focused.set(screen.window.decorView.hasWindowFocus())
                                            clock.postFrameCallback(this)
                                        }
                                    }
                                    clock.postFrameCallback(callback)
                                    onDispose { clock.removeFrameCallback(callback) }
                                }
                                Box(Modifier.fillMaxSize().background(Color.Red).predictiveBackTransform(back, BackPresentation.PAGE))
                            } else {
                                remember { parentCompositions.incrementAndGet() }
                                Box(Modifier.fillMaxSize().background(Color.Blue))
                            }
                        }
                    }
                }
            }
        }
        await("Initial predictive page has window focus") {
            state.get() != null && mounted.get() && focused.get() && frames.size >= 3
        }
        instrumentation.uiAutomation.waitForIdle(100, 5_000)
        activity.scenario.onActivity { screen ->
            width = screen.window.decorView.width.toFloat()
            height = screen.window.decorView.height.toFloat()
        }
        Log.i("NativePredictiveBack", "Ready window ${width}x$height; canCommit=$canCommit")
    }

    @Test fun nativeEdgesCancelAndThenCommitWithoutLosingTheParent() {
        show()
        try {
            for (right in listOf(false, true)) {
                begin(right)
                move(right, .28f, 18)
                await("System must deliver predictive progress (right=$right)") { state.get().progress > .05f }
                move(right, .002f, 18)
                release(right, .002f)
                await("Cancelled system back must restore") { state.get().phase == BackPhase.IDLE }
                assertEquals(0, closes.get())
                assertTrue(mounted.get())
                val idleFrame = frames.size
                await("Cancellation has settled on real frames") { frames.size >= idleFrame + 3 && state.get().phase == BackPhase.IDLE }
            }
            begin(false)
            move(false, .65f, 24)
            release(false, .65f)
            await("Committed system back must unmount the whole child once") { !mounted.get() }
            assertEquals(1, closes.get())
            assertEquals(1, parentCompositions.get())
            assertTrue(frames.any { it.first == BackPhase.TRACKING && it.second > 0f })
            assertTrue(frames.any { it.first == BackPhase.RESTORING })
            assertTrue(frames.any { it.first == BackPhase.COMMITTING })
            assertTrue(frames.all { it.second.isFinite() && it.second in 0f..1f })
        } finally {
            if (held) finger(MotionEvent.ACTION_CANCEL, 1f)
        }
    }

    @Test fun nativeSheetBackRevealsDockBeforeTheSheetIsRemoved() {
        val overlays = AtomicReference<GlassOverlayController>()
        val earlyReturn = AtomicBoolean(false)
        activity.scenario.onActivity { screen ->
            screen.enableEdgeToEdge()
            screen.setContent {
                var open by remember { mutableStateOf(true) }
                CourseTableTheme {
                    LiquidBackdropHost(Modifier.fillMaxSize()) {
                        val controller = checkNotNull(LocalGlassOverlayController.current)
                        SideEffect { overlays.set(controller) }
                        DisposableEffect(controller) {
                            val clock = Choreographer.getInstance()
                            val callback = object : Choreographer.FrameCallback {
                                override fun doFrame(frameTimeNanos: Long) {
                                    if (controller.entries.isNotEmpty() && !controller.hidesDock) earlyReturn.set(true)
                                    clock.postFrameCallback(this)
                                }
                            }
                            clock.postFrameCallback(callback)
                            onDispose { clock.removeFrameCallback(callback) }
                        }
                        Box(Modifier.fillMaxSize().background(Color.Blue))
                        if (open) ModalBottomSheet(onDismissRequest = { closes.incrementAndGet(); open = false }) {
                            Box(Modifier.fillMaxSize().background(Color.Red))
                        }
                    }
                }
            }
        }
        instrumentation.uiAutomation.waitForIdle(100, 5_000)
        activity.scenario.onActivity { screen ->
            width = screen.window.decorView.width.toFloat()
            height = screen.window.decorView.height.toFloat()
        }
        await("Sheet must initially hide Dock") { overlays.get()?.hidesDock == true }
        try {
            begin(false)
            move(false, .65f, 24)
            release(false, .65f)
            await("System back must close sheet") { closes.get() == 1 }
            assertTrue("A real frame must reveal Dock while the outgoing sheet is still mounted", earlyReturn.get())
        } finally {
            if (held) finger(MotionEvent.ACTION_CANCEL, 1f)
        }
    }

    @Test fun aNativeOppositeEdgeGestureTakesOverARejectedBackRestore() {
        show(canCommit = false)
        try {
            begin(false)
            move(false, .28f, 18)
            await("Initial system progress") { state.get().progress > .1f }
            val marker = frames.size
            // Android 14 itself animates a cancelled gesture to zero before delivering
            // onBackCancelled. A rejected native commit exercises the app's nonzero
            // restoration and delayed confirmation with real input/render timing.
            release(false, lastFraction)
            await("A visible restoration frame") {
                frames.drop(marker).any { it.first == BackPhase.RESTORING && it.second > .05f }
            }
            assertEquals(BackPhase.RESTORING, state.get().phase)
            begin(true)
            move(true, .18f, 4)
            await("New edge must take control during restoration") {
                state.get().phase == BackPhase.TRACKING && frames.drop(marker).any { it.first == BackPhase.TRACKING }
            }
            val sequence = frames.drop(marker)
            val restoring = sequence.indexOfFirst { it.first == BackPhase.RESTORING }
            val tracking = sequence.withIndex().firstOrNull {
                it.index > restoring && it.value.first == BackPhase.TRACKING
            }?.index ?: -1
            assertTrue("Both real render phases must be observed", restoring >= 0 && tracking > restoring)
            assertFalse("The page must not reset between gestures", sequence.subList(restoring, tracking).any { it.first == BackPhase.IDLE })
            finger(MotionEvent.ACTION_CANCEL, x(true, lastFraction))
            held = false
            await("Second cancellation restores") { state.get().phase == BackPhase.IDLE }
            assertEquals(0, closes.get())
            assertEquals("Takeover must cancel the pending confirmation", 0, rejections.get())
            assertTrue(mounted.get())
        } finally {
            if (held) finger(MotionEvent.ACTION_CANCEL, 1f)
        }
    }

    private var lastFraction = .002f
    private fun begin(right: Boolean) {
        down = SystemClock.uptimeMillis()
        lastFraction = .002f
        finger(MotionEvent.ACTION_DOWN, x(right, lastFraction))
        held = true
        SystemClock.sleep(16)
    }
    private fun move(right: Boolean, fraction: Float, steps: Int) {
        val start = lastFraction
        for (step in 1..steps) {
            finger(MotionEvent.ACTION_MOVE, x(right, start + (fraction - start) * step / steps))
            SystemClock.sleep(16)
        }
        lastFraction = fraction
    }
    private fun release(right: Boolean, fraction: Float) {
        finger(MotionEvent.ACTION_UP, x(right, fraction))
        held = false
    }
    private fun x(right: Boolean, fraction: Float) = if (right) width * (1f - fraction) else width * fraction
    private fun finger(action: Int, x: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; y = height * .55f; pressure = 1f; size = 1f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (!condition()) {
            assertTrue("$message; phase=${state.get()?.phase}, progress=${state.get()?.progress}, frames=${frames.takeLast(8)}",
                SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(10)
        }
    }
}
