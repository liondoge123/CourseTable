package com.coursetable.app

import android.graphics.RectF
import android.os.SystemClock
import android.os.Looper
import android.view.InputDevice
import android.view.Choreographer
import android.view.MotionEvent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.RootDestination
import com.coursetable.app.ui.RootNavigationHost
import com.coursetable.app.ui.RootNavigationState
import com.coursetable.app.ui.TimetableScreen
import com.coursetable.app.ui.TimetableViewModel
import com.coursetable.app.ui.rememberRootNavigationState
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.LocalBackLayerActive
import com.coursetable.app.ui.theme.CourseTableTheme
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native finger events and the real render clock, including the actual week pager. */
class NativeRootNavigationTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    private val navigation = AtomicReference<RootNavigationState>()
    private val bounds = AtomicReference<RectF>()
    private val screen = AtomicReference<ComponentActivity>()
    private val timetable = AtomicReference<TimetableViewModel>()
    private val timetableActive = AtomicBoolean(false)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun show() {
        activity.scenario.onActivity { screen ->
            this.screen.set(screen)
            screen.setContent {
                val vm: TimetableViewModel = viewModel(factory = viewModelFactory {
                    initializer { TimetableViewModel(screen.application as CourseApp) }
                })
                val root = rememberRootNavigationState(RootDestination.COURSES)
                SideEffect { navigation.set(root); timetable.set(vm) }
                CourseTableTheme {
                    LiquidBackdropHost(Modifier.fillMaxSize().onGloballyPositioned {
                        val rect = it.boundsInWindow()
                        bounds.set(RectF(rect.left, rect.top, rect.right, rect.bottom))
                    }) {
                        RootNavigationHost(root, true) { destination ->
                            if (destination == RootDestination.TIMETABLE) {
                                val active = LocalBackLayerActive.current
                                SideEffect { timetableActive.set(active) }
                                TimetableScreen(vm)
                            } else Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        await("Initial layout") { navigation.get() != null && bounds.get()?.isEmpty == false }
        // Measure the real timetable before timing the arrival. Its first
        // composition can otherwise consume the complete animation on an emulator.
        onMain { navigation.get().navigateTo(RootDestination.TIMETABLE) }
        await("Warm timetable layout") { settled(RootDestination.TIMETABLE) }
        onMain { navigation.get().navigateTo(RootDestination.COURSES) }
        await("Warm courses layout") { settled(RootDestination.COURSES) }
    }

    @Test fun holdingAnArrivingTimetableSettlesAfterNativeFingerRelease() =
        interruptArrival(reverse = false, fling = false)

    @Test fun aSmallNativeReverseDragCannotStrandAnArrivingTimetable() =
        interruptArrival(reverse = true, fling = false)

    @Test fun pressingDuringASwipeToTimetableCannotStrandTheRootPager() =
        interruptArrival(reverse = true, fling = true)

    @Test fun aSettledTimetableStillHandsHorizontalSwipesToItsWeekPager() {
        show()
        onMain { navigation.get().navigateTo(RootDestination.TIMETABLE) }
        await("Timetable aligned and input active") {
            settled(RootDestination.TIMETABLE) && timetableActive.get()
        }
        val before = onMain { timetable.get().displayedWeek() }
        val forward = onMain { before < timetable.get().latestSettings.totalWeeks }
        val rect = bounds.get()
        val downTime = SystemClock.uptimeMillis()
        var x = rect.left + rect.width() * if (forward) 0.80f else 0.20f
        val y = rect.top + rect.height() * 0.55f
        var held = false
        try {
            fingerEvent(MotionEvent.ACTION_DOWN, downTime, x, y); held = true
            repeat(6) {
                SystemClock.sleep(16)
                x += rect.width() * if (forward) -0.10f else 0.10f
                fingerEvent(MotionEvent.ACTION_MOVE, downTime, x, y)
            }
            fingerEvent(MotionEvent.ACTION_UP, downTime, x, y); held = false
            await("Week swipe must still work") {
                onMain { timetable.get().displayedWeek() == before + if (forward) 1 else -1 }
            }
            assertTrue(settled(RootDestination.TIMETABLE))
        } finally {
            if (held) fingerEvent(MotionEvent.ACTION_CANCEL, downTime, x, y)
        }
    }

    private fun interruptArrival(reverse: Boolean, fling: Boolean) {
        show()
        var completed = 0
        var attempts = 0
        while (completed < 3 && attempts++ < 9) {
            onMain { navigation.get().navigateTo(RootDestination.COURSES) }
            await("Reset to courses") { settled(RootDestination.COURSES) }
            val rect = bounds.get()
            var downTime = SystemClock.uptimeMillis()
            var x = rect.left + rect.width() * 0.35f
            val y = rect.top + rect.height() * 0.55f
            var held = false
            var finishedBeforePress = false
            var frameError: Throwable? = null
            fun event(action: Int) {
                fingerEvent(action, downTime, x, y)
            }
            // Observe each real render frame on the UI thread. Polling from the
            // instrumentation thread can miss the shorter eased arrival entirely.
            val arrivalObserver = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                  try {
                    val root = navigation.get()
                    val arriving = root.transition.targetState == RootDestination.TIMETABLE &&
                        root.transition.fraction in 0.08f..0.92f && root.isTransitioning
                    if (arriving) {
                        downTime = SystemClock.uptimeMillis()
                        event(MotionEvent.ACTION_DOWN)
                        held = true
                    } else if (!root.isTransitioning && root.destination == RootDestination.TIMETABLE) {
                        finishedBeforePress = true
                    } else {
                        Choreographer.getInstance().postFrameCallback(this)
                    }
                  } catch (error: Throwable) {
                    frameError = error
                  }
                }
            }
            try {
                if (fling) {
                    x = rect.left + rect.width() * 0.15f
                    event(MotionEvent.ACTION_DOWN); held = true
                    repeat(5) {
                        SystemClock.sleep(16)
                        x += rect.width() * 0.13f
                        event(MotionEvent.ACTION_MOVE)
                    }
                    event(MotionEvent.ACTION_UP); held = false
                } else {
                    onMain { navigation.get().navigateTo(RootDestination.TIMETABLE) }
                }
                x = rect.left + rect.width() * 0.35f
                onMain { Choreographer.getInstance().postFrameCallback(arrivalObserver) }
                await("Timetable is visible but not yet aligned") {
                    onMain { held || finishedBeforePress || frameError != null }
                }
                frameError?.let { throw it }
                if (finishedBeforePress) {
                    // A busy emulator may skip the entire injection window. Retry
                    // only a fully aligned page; stranded half pages still fail.
                    Log.i("NativeRootNavigation", "Arrival completed before injection; retrying attempt $attempts")
                    continue
                }
                SystemClock.sleep(240)
                if (reverse) {
                    x -= rect.width() * 0.04f
                    event(MotionEvent.ACTION_MOVE)
                    SystemClock.sleep(80)
                }
                event(MotionEvent.ACTION_UP); held = false
                await("Release must align one complete root page; ${onMain { debugState() }}") {
                    onMain {
                        !navigation.get().isTransitioning
                    }
                }
                completed++
            } finally {
                onMain { Choreographer.getInstance().removeFrameCallback(arrivalObserver) }
                if (held) event(MotionEvent.ACTION_CANCEL)
            }
        }
        assertEquals("Must actually interrupt three arrivals", 3, completed)
    }

    private fun fingerEvent(action: Int, downTime: Long, x: Float, y: Float) {
        val motion = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply {
                id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER
            }),
            arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x; this.y = y; pressure = 1f; size = 1f
            }), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        // Real Android finger events through the Activity's View hierarchy, with
        // the real render clock rather than Compose's simulated test clock.
        try { onMain { assertTrue(screen.get().dispatchTouchEvent(motion)) } }
        finally { motion.recycle() }
    }

    private fun settled(destination: RootDestination): Boolean = onMain {
        val root = navigation.get()
        !root.isTransitioning && root.destination == destination && root.settledDestination == destination
    }

    private fun debugState(): String = navigation.get().let {
        "page=${it.destination}, fraction=${it.transition.fraction}, transitioning=${it.isTransitioning}, active=${timetableActive.get()}, week=${timetable.get().displayedWeek()}"
    }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 4000
        while (!condition()) {
            assertTrue("$message; ${onMain { debugState() }}", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(8)
        }
    }
}
