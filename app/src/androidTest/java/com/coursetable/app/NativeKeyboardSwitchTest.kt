package com.coursetable.app

import android.graphics.Rect
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.rememberImeVisible
import com.coursetable.app.ui.theme.CourseTableTheme
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Uses the actual render clock: a Compose test clock can delay the new input session. */
class NativeKeyboardSwitchTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun switchingFieldsKeepsKeyboardVisibleWithRealRenderClock() {
        val visible = AtomicBoolean(false)
        val tracking = AtomicBoolean(false)
        val closures = AtomicInteger(0)
        activity.scenario.onActivity { screen ->
            screen.enableEdgeToEdge()
            screen.setContent {
                val ime = rememberImeVisible()
                SideEffect {
                    if (visible.getAndSet(ime) && !ime && tracking.get()) closures.incrementAndGet()
                }
                CourseTableTheme {
                    LiquidBackdropHost(Modifier.fillMaxSize().statusBarsPadding()
                        .semantics { testTagsAsResourceId = true }) {
                        CourseEditorDialog(Course(name = "数学", teacher = "老师", location = "教室103",
                            dayOfWeek = 1, startSection = 1, duration = 1, startWeek = 1, endWeek = 16),
                            16, 12, {}, {})
                    }
                }
            }
        }
        tapField("course-editor-teacher")
        await { visible.get() }
        SystemClock.sleep(600)
        tracking.set(true)
        repeat(6) { index ->
            tapField(if (index % 2 == 0) "course-editor-location" else "course-editor-teacher")
            SystemClock.sleep(600)
            assertTrue("Keyboard did not remain visible", visible.get())
        }
        assertEquals("Keyboard closed while switching fields", 0, closures.get())
    }

    private fun tapField(tag: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        var bounds: Rect? = null
        await {
            val node = automation.rootInActiveWindow?.let { findTag(it, tag) }
            if (node != null) bounds = Rect().also { node.getBoundsInScreen(it) }
            bounds != null && !bounds!!.isEmpty
        }
        val target = bounds!!
        val time = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            x = target.exactCenterX(); y = target.exactCenterY(); pressure = 1f; size = 1f
        })
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, 1,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(64)
        }
    }

    private fun findTag(node: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName == tag) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val result = findTag(child, tag)
            if (result != null) return result
        }
        return null
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!condition()) {
            assertTrue("Native UI condition timed out", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(30)
        }
    }
}
