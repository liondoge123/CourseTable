package com.coursetable.app

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.data.Timetable
import com.coursetable.app.ui.TimetableSelector
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.LiquidTheme
import com.coursetable.app.ui.theme.ThemeMode
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real render clock and native finger events, suitable for recording the actual animation speed. */
class NativeMenuMotionTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun timetableCapsuleCanCancelAndSwitchInBothThemes() {
        val source = AtomicReference<Rect?>()
        val viewport = AtomicReference<Rect?>()
        val scale = AtomicReference(1f)
        val switches = AtomicInteger()
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val active = mutableStateOf(1L)
        activity.scenario.onActivity { screen ->
            screen.enableEdgeToEdge()
            scale.set(screen.resources.displayMetrics.density)
            screen.setContent {
                CourseTableTheme(themeMode = mode.value) {
                    LiquidBackdropHost(Modifier.fillMaxSize().onGloballyPositioned { viewport.set(it.boundsInWindow()) }) {
                        LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                        Box(Modifier.fillMaxSize().padding(top = 96.dp, start = 24.dp)) {
                            TimetableSelector(listOf(Timetable(1L, "默认课表"), Timetable(2L, "另一课表")), active.value,
                                onSwitch = { active.value = it; switches.incrementAndGet() },
                                modifier = Modifier.onGloballyPositioned { source.set(it.boundsInWindow()) })
                        }
                    }
                }
            }
        }
        await { source.get()?.isEmpty == false && viewport.get()?.isEmpty == false }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEachIndexed { index, theme ->
            activity.scenario.onActivity { mode.value = theme; active.value = 1L }
            SystemClock.sleep(500)
            var trigger = source.get()!!
            tap(trigger.center.x, trigger.center.y)
            SystemClock.sleep(1100)
            val window = viewport.get()!!
            tap(window.center.x, window.height * 0.75f)
            SystemClock.sleep(800)
            assertEquals(index, switches.get())
            trigger = source.get()!!
            tap(trigger.center.x, trigger.center.y)
            SystemClock.sleep(1000)
            tap(trigger.left + 48f * scale.get(), trigger.top + 76f * scale.get())
            await { switches.get() == index + 1 }
            SystemClock.sleep(600)
            assertEquals(2L, active.value)
        }
    }

    @Test fun choiceAndActionMenusOpenAndCloseWithTheRealRenderClock() {
        val source = AtomicReference<Rect?>()
        val viewport = AtomicReference<Rect?>()
        val closed = AtomicInteger()
        val choice = mutableStateOf(true)
        activity.scenario.onActivity { screen ->
            screen.enableEdgeToEdge()
            screen.setContent {
                var expanded by remember { mutableStateOf(false) }
                var triggerHidden by remember { mutableStateOf(false) }
                val menuSource = rememberMenuSource()
                var anchor by remember { mutableStateOf<Rect?>(null) }
                CourseTableTheme(themeMode = ThemeMode.LIGHT) {
                    LiquidBackdropHost(Modifier.fillMaxSize().onGloballyPositioned { viewport.set(it.boundsInWindow()) }) {
                        LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                        Box(Modifier.fillMaxSize().padding(top = 96.dp, end = 24.dp), contentAlignment = Alignment.TopEnd) {
                            IconButton({ expanded = true }, Modifier
                                .graphicsLayer { alpha = if (triggerHidden) 0f else 1f }.onGloballyPositioned {
                                anchor = it.boundsInWindow(); source.set(anchor)
                            }) { Icon(Icons.Filled.MoreHorizontal, "更多操作", Modifier.captureMenuSource(menuSource, triggerHidden)) }
                        }
                        DropdownMenu(expanded, { expanded = false }, anchor, DropdownMenuAlignment.END,
                            sourceContent = menuSource,
                            onSourceCovered = { triggerHidden = true },
                            onClosed = { triggerHidden = false; closed.incrementAndGet() }) {
                            val labels = if (choice.value) listOf("默认课表", "111") else listOf("重命名", "删除课表")
                            labels.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (!choice.value && index == 1) LiquidTheme.colorScheme.error else LiquidTheme.colorScheme.onSurface) },
                                    onClick = { expanded = false }, selected = if (choice.value) index == 0 else null
                                )
                            }
                        }
                    }
                }
            }
        }
        await { source.get()?.isEmpty == false && viewport.get()?.isEmpty == false }
        SystemClock.sleep(400)
        repeat(2) { index ->
            activity.scenario.onActivity { choice.value = index == 0 }
            val trigger = source.get()!!
            tap(trigger.center.x, trigger.center.y)
            SystemClock.sleep(1000)
            val window = viewport.get()!!
            tap(window.left + window.width * 0.15f, window.top + window.height * 0.75f)
            await { closed.get() == index + 1 }
            SystemClock.sleep(500)
        }
        assertEquals(2, closed.get())
    }

    private fun tap(x: Float, y: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val time = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, 1, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(64)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!condition()) {
            assertTrue("Native menu condition timed out", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(30)
        }
    }
}
