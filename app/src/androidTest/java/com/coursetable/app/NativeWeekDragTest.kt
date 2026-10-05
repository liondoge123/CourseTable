package com.coursetable.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.CourseWeeksDialog
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.LiquidTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** A held finger and edge scrolling use the real render clock, without Compose idle waits. */
class NativeWeekDragTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)
    @Test fun stationaryLongPressStaysUncheckedAfterNativeFingerRelease() {
        activity.scenario.onActivity { screen ->
            screen.setContent {
                CourseTableTheme {
                    LiquidBackdropHost(Modifier.fillMaxSize().background(LiquidTheme.colorScheme.background)
                        .semantics { testTagsAsResourceId = true }) {
                        CourseWeeksDialog(18, listOf(1, 18), {}, {})
                    }
                }
            }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val oldFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        var target: Rect? = null
        await {
            automation.rootInActiveWindow?.let { findTag(it, "course-week-1") }?.let {
                target = Rect().also { bounds -> it.getBoundsInScreen(bounds) }
            }
            target?.isEmpty == false
        }
        SystemClock.sleep(700)
        automation.rootInActiveWindow?.let { findTag(it, "course-week-1") }?.getBoundsInScreen(target!!)
        val downTime = SystemClock.uptimeMillis()
        fun event(action: Int) {
            val motion = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { x = target!!.exactCenterX(); y = target!!.exactCenterY(); pressure = 1f; size = 1f }),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(automation.injectInputEvent(motion, true)) } finally { motion.recycle() }
        }
        var held = false
        try {
            event(MotionEvent.ACTION_DOWN); held = true
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 150)
            await { automation.rootInActiveWindow?.let { findTag(it, "course-week-1") }?.isChecked == false }
            event(MotionEvent.ACTION_UP); held = false
            repeat(10) {
                SystemClock.sleep(30)
                assertEquals(false, automation.rootInActiveWindow?.let { findTag(it, "course-week-1") }?.isChecked)
            }
        } finally {
            if (held) event(MotionEvent.ACTION_CANCEL)
            automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
        }
    }

    @Test fun heldFingerAtEdgeSelectsNewWeeksWhileGridScrolls() {
        val saved = AtomicReference<List<Int>?>(null)
        activity.scenario.onActivity { screen ->
            screen.setContent {
                var open by remember { mutableStateOf(true) }
                CourseTableTheme {
                    LiquidBackdropHost(Modifier.fillMaxSize().background(LiquidTheme.colorScheme.background)
                        .semantics { testTagsAsResourceId = true }) {
                        if (open) CourseWeeksDialog(60, listOf(60), { saved.set(it) }, { open = false })
                    }
                }
            }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val oldFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        fun bounds(tag: String): Rect {
            var result: Rect? = null
            await {
                automation.rootInActiveWindow?.let { findTag(it, tag) }?.let { node ->
                    result = Rect().also { node.getBoundsInScreen(it) }
                }
                result?.isEmpty == false
            }
            return result!!
        }
        var down = false
        var x = 0f
        var y = 0f
        val downTime = SystemClock.uptimeMillis()
        fun event(action: Int) {
            val motion = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f }),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(automation.injectInputEvent(motion, true)) } finally { motion.recycle() }
        }
        try {
            // Wait for the dialog entrance transform before using screen-space finger coordinates.
            SystemClock.sleep(700)
            val origin = bounds("course-week-1")
            val grid = bounds("course-weeks-grid")
            android.util.Log.i("NativeWeekDragTest", "origin=$origin grid=$grid")
            x = origin.exactCenterX(); y = origin.exactCenterY()
            event(MotionEvent.ACTION_DOWN); down = true
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 100)
            val startY = y
            repeat(12) { step ->
                y = startY + (grid.bottom - 2f - startY) * ((step + 1f) / 12)
                event(MotionEvent.ACTION_MOVE)
                SystemClock.sleep(16)
            }
            SystemClock.sleep(3000)
            InstrumentationRegistry.getArguments().getString("weeksEvidenceSession")?.let { session ->
                require(session.matches(Regex("[a-zA-Z0-9-]+")))
                val fd = automation.executeShellCommand("screencap -p /data/local/tmp/coursetable-testing/$session/native-held-drag.png")
                ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
            }
            event(MotionEvent.ACTION_UP); down = false
            val confirm = bounds("course-weeks-confirm")
            x = confirm.exactCenterX(); y = confirm.exactCenterY()
            event(MotionEvent.ACTION_DOWN); down = true
            SystemClock.sleep(64)
            event(MotionEvent.ACTION_UP); down = false
            await { saved.get() != null }
            assertTrue("Edge drag must paint beyond the original viewport: ${saved.get()}", saved.get()!!.any { it in 50..59 })
            assertTrue(saved.get()!!.contains(1))
        } finally {
            if (down) event(MotionEvent.ACTION_CANCEL)
            automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
        }
    }
    private fun findTag(node: AccessibilityNodeInfo, tag: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName == tag) return node
        repeat(node.childCount) { index -> node.getChild(index)?.let { findTag(it, tag)?.let { found -> return found } } }
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
