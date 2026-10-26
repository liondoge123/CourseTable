package com.coursetable.app

import android.graphics.RectF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real finger taps on the liquid Dock and the real render clock, without a Compose test clock. */
class NativeDockRetargetTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var screen: ComponentActivity
    private lateinit var navigation: RootNavigationState
    private val dock = AtomicReference<RectF>()
    private var requests = 0
    private var interruptions = 0
    private var animationScale = 1f

    @Test fun repeatedNativeDockTapsKeepPagesContinuousAtDifferentCadences() {
        activity.scenario.onActivity { activity ->
            screen = activity
            activity.setContent {
                val root = rememberRootNavigationState(RootDestination.TIMETABLE)
                val scope = rememberCoroutineScope()
                SideEffect {
                    navigation = root
                    animationScale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                }
                CourseTableTheme {
                    LiquidBackdropHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            RootNavigationHost(root, true) { destination ->
                                Box(Modifier.fillMaxSize().background(
                                    listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)[destination.ordinal]))
                            }
                            LiquidNavigationTabs(root.destination.ordinal, { index ->
                                val before = root.transition.pages.associate {
                                    it.destination to (it.anchor - root.transition.position)
                                }
                                if (root.isTransitioning) interruptions++
                                root.navigateTo(RootDestination.entries[index])
                                requests++
                                root.transition.pages.forEach { page ->
                                    before[page.destination]?.let { offset ->
                                        assertEquals("A native tap must preserve every mounted page's position",
                                            offset, page.anchor - root.transition.position, 0.00001f)
                                    }
                                }
                            }, 4, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                                .width(280.dp).height(54.dp).onGloballyPositioned {
                                    val rect = it.boundsInWindow()
                                    dock.set(RectF(rect.left, rect.top, rect.right, rect.bottom))
                                }) { _, _, _ ->
                                repeat(4) { Box(Modifier.weight(1f).fillMaxHeight()) }
                            }
                        }
                    }
                }
            }
        }
        await("Dock is laid out") { dock.get() != null }
        SystemClock.sleep(300)
        var frames = 0
        var movingFrames = 0
        var peakSpeed = 0f
        var motionSamples = 0
        var failure: Throwable? = null
        val observer = object : Choreographer.FrameCallback {
            override fun doFrame(time: Long) {
                try {
                    val state = navigation.transition
                    val anchors = state.pages.map { it.anchor }.sorted()
                    anchors.zipWithNext().forEach { (left, right) ->
                        assertEquals("Requested pages must tile the viewport without gaps", 1f, right - left, 0f)
                    }
                    frames++
                    if (navigation.isTransitioning) movingFrames++
                    Choreographer.getInstance().postFrameCallback(this)
                } catch (error: Throwable) { failure = error }
            }
        }
        onMain {
            // Use the velocity evaluated for the same animation frame, rather
            // than differentiating model values against a separately queued
            // draw/layout callback. Rendering cadence is measured independently.
            NavigationMotionDiagnostics.listener = { name, values, _ ->
                if (name == "page.motion") {
                    val speed = abs(values[1]) * animationScale
                    peakSpeed = maxOf(peakSpeed, speed)
                    motionSamples++
                    if (speed >= 11f) failure = AssertionError("Motion speed spike: $speed widths/s")
                }
            }
            Choreographer.getInstance().postFrameCallback(observer)
        }
        try {
            for (cadence in listOf(32L, 80L, 160L)) {
                for (index in listOf(1, 2, 3, 2, 0, 3, 0)) {
                    tap(index)
                    SystemClock.sleep(cadence)
                }
                await("The latest native tap must land on the timetable") {
                    !navigation.isTransitioning && navigation.settledDestination == RootDestination.TIMETABLE
                }
            }
            onMain {
                failure?.let { throw it }
                assertEquals("All native taps must reach the selection callback", 21, requests)
                assertTrue("Actually interrupt running transitions", interruptions >= 9)
                assertTrue("Observe real frames during page motion", movingFrames >= 20)
                assertTrue("Sample the running spring on real animation frames", motionSamples >= 20)
                assertTrue(frames > movingFrames)
                android.util.Log.i("NativeDockRetarget", "requests=$requests interruptions=$interruptions frames=$frames moving=$movingFrames samples=$motionSamples peakMotionSpeed=$peakSpeed")
            }
        } finally {
            onMain {
                Choreographer.getInstance().removeFrameCallback(observer)
                NavigationMotionDiagnostics.listener = null
            }
        }
    }

    private fun tap(index: Int) {
        val rect = dock.get()
        val x = rect.left + rect.width() * (index + 0.5f) / 4f
        val y = rect.centerY()
        val down = SystemClock.uptimeMillis()
        fun event(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f }),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { onMain { assertTrue(screen.dispatchTouchEvent(event)) } } finally { event.recycle() }
        }
        event(MotionEvent.ACTION_DOWN)
        try { SystemClock.sleep(16); event(MotionEvent.ACTION_UP) }
        catch (error: Throwable) { event(MotionEvent.ACTION_CANCEL); throw error }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (true) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            assertTrue(message, SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(16)
        }
    }
}
