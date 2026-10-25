package com.coursetable.app

import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.Bundle
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import com.coursetable.app.ui.liquid.NavigationMotionDiagnostics
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Diagnostic only: actual MainActivity, native input, real render clock, no FPS assertions. */
class RootNavigationFrameProfileTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val frames = ConcurrentLinkedQueue<Frame>()
    private val trials = mutableListOf<Trial>()
    private val motion = ConcurrentLinkedQueue<Motion>()
    private lateinit var window: Window
    private var refreshHz = 0f
    private var coordinateScaleX = 1f
    private var coordinateScaleY = 1f
    private var nativeWindowWidth = 0
    private var nativeWindowHeight = 0
    private var lastVerifiedPage: String? = null

    private data class Frame(val time: Long, val total: Long, val deadline: Long,
        val layout: Long, val draw: Long, val unknown: Long, val gpu: Long, val lost: Int)
    private data class Trial(val source: String, val target: String, val action: String,
        val start: Long, val up: Long, val end: Long, val temperature: String)
    private data class Motion(val name: String, val values: List<Float>, val time: Long)
    private data class Input(val down: Long, val up: Long)

    @Test fun compareDockClicksAndSwipesOnTheSameAdjacentPages() {
        assumeTrue("Requires Android 12 frame deadline metrics", Build.VERSION.SDK_INT >= 31)
        assumeTrue("Opt in with -e profileNavigationFrames true",
            InstrumentationRegistry.getArguments().getString("profileNavigationFrames") == "true")
        val dockOnly = InstrumentationRegistry.getArguments().getString("frameProfileMode") == "dock_only"
        val rounds = InstrumentationRegistry.getArguments().getString("frameProfileRounds")?.toInt() ?: 4
        require(rounds in 1..50)
        val collector = HandlerThread("navigation-frame-metrics").apply { start() }
        val handler = Handler(collector.looper)
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            frames.add(Frame(metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.DEADLINE),
                metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION), metrics.getMetric(FrameMetrics.DRAW_DURATION),
                metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION), metrics.getMetric(FrameMetrics.GPU_DURATION), dropped))
        }
        activity.scenario.onActivity {
            NavigationMotionDiagnostics.listener = { name, values, time -> motion.add(Motion(name, values, time)) }
            window = it.window
            refreshHz = it.display?.refreshRate ?: 60f
            window.addOnFrameMetricsAvailableListener(listener, handler)
        }
        try {
            instrumentation.waitForIdleSync()
            // Accessibility can expose the new content while the system splash still
            // covers it. A successful injectInputEvent is not an application receipt.
            await("Dock painted and window focused") {
                var focused = false
                activity.scenario.onActivity { focused = it.hasWindowFocus() }
                if (!focused) false else {
                    val bounds = tabBounds("课程")
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    if (bitmap == null) false else try {
                        var darkest = 255
                        var lightest = 0
                        for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height)) {
                            for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width)) {
                                val pixel = bitmap.getPixel(x, y)
                                val light = (((pixel shr 16) and 255) + ((pixel shr 8) and 255) + (pixel and 255)) / 3
                                darkest = minOf(darkest, light)
                                lightest = maxOf(lightest, light)
                            }
                        }
                        lightest - darkest > 40
                    } finally { bitmap.recycle() }
                }
            }
            awaitSettled(0L, dock = false)
            val visited = mutableSetOf("课表")
            for (round in 0 until rounds) {
                val order = if (dockOnly) listOf("dock") else if (round % 2 == 0) listOf("dock", "swipe") else listOf("swipe", "dock")
                for (action in order) {
                    // A round trip covers all four directions without inserting
                    // unmeasured reset animations between adjacent trials.
                    for ((source, target) in listOf("课程" to "导入", "导入" to "设置",
                        "设置" to "导入", "导入" to "课程")) {
                            resetTo(source)
                            visited += source
                            val temperature = if (target in visited) "warm" else "first-entry"
                            val input = if (action == "dock") tapTab(target) else swipe(source, target)
                            awaitSettled(input.down, action == "dock")
                            val end = System.nanoTime()
                            awaitPage(target)
                            trials += Trial(source, target, action, input.down, input.up, end, temperature)
                            visited += target
                    }
                }
            }
            SystemClock.sleep(120)
            val flushed = CountDownLatch(1)
            handler.post { flushed.countDown() }
            assertTrue(flushed.await(3, TimeUnit.SECONDS))
            val report = JSONObject().put("sdk", Build.VERSION.SDK_INT).put("device", Build.MODEL)
                .put("hardware", Build.HARDWARE).put("refreshHz", refreshHz)
                .put("label", InstrumentationRegistry.getArguments().getString("frameProfileLabel") ?: "current")
                .put("window", "down-to-settled").put("inputReadyCheck", "focused-painted-acknowledged")
                .put("coordinateScaleX", coordinateScaleX).put("coordinateScaleY", coordinateScaleY)
                .put("nativeWindowWidth", nativeWindowWidth).put("nativeWindowHeight", nativeWindowHeight)
                .put("rounds", rounds).put("trials", JSONArray())
            for (trial in trials) {
                val samples = frames.filter { it.time in trial.start..trial.end }
                val events = motion.filter { it.time in trial.start..trial.end }
                val pageStart = events.firstOrNull { it.name == "page.request" }?.time
                val targetIndex = listOf("课表", "课程", "导入", "设置").indexOf(trial.target).toFloat()
                val row = summarize(listOf(samples))
                    .put("source", trial.source).put("target", trial.target).put("action", trial.action)
                    .put("temperature", trial.temperature).put("durationMs", (trial.end - trial.start) / 1e6)
                    .put("downNanos", trial.start).put("upNanos", trial.up).put("endNanos", trial.end)
                    .put("pageStartNanos", pageStart)
                    .put("lensTargetRegressions", if (pageStart == null) 0 else events.count {
                        it.name == "lens" && it.time >= pageStart && it.values[1] != targetIndex
                    })
                    .put("lensReleaseNanos", events.firstOrNull { it.name == "lens.release" }?.time)
                    .put("motion", JSONArray(events.map {
                        JSONObject().put("name", it.name).put("timeNanos", it.time).put("values", JSONArray(it.values))
                    }))
                    .put("rawFrames", JSONArray(samples.map {
                        JSONObject().put("timeNanos", it.time).put("totalNanos", it.total)
                            .put("deadlineNanos", it.deadline).put("layoutNanos", it.layout)
                            .put("drawNanos", it.draw).put("unknownNanos", it.unknown)
                            .put("gpuNanos", it.gpu).put("lostCallbacks", it.lost)
                    }))
                // Stream each raw trial separately to stay below Binder's transaction limit.
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("stream", "\nNAVIGATION_FRAME_TRIAL=" + row.toString() + "\n")
                })
                row.remove("motion")
                row.remove("rawFrames")
                report.getJSONArray("trials").put(row)
            }
            report.put("groups", JSONArray())
            for (pair in trials.map { it.source to it.target }.distinct()) for (action in listOf("dock", "swipe")) {
                val selected = trials.filter { it.source == pair.first && it.target == pair.second && it.action == action }
                if (selected.isEmpty()) continue
                val samples = selected.map { trial ->
                    frames.filter { it.time in trial.start..trial.end }
                }
                report.getJSONArray("groups").put(summarize(samples).put("source", pair.first)
                    .put("target", pair.second).put("action", action).put("runs", selected.size))
            }
            instrumentation.sendStatus(2, Bundle().apply {
                putString("stream", "\nNAVIGATION_FRAME_PROFILE=" + report.toString() + "\n")
            })
        } finally {
            activity.scenario.onActivity { window.removeOnFrameMetricsAvailableListener(listener) }
            activity.scenario.onActivity { NavigationMotionDiagnostics.listener = null }
            collector.quitSafely()
        }
    }

    private fun summarize(trialFrames: List<List<Frame>>): JSONObject {
        val samples = trialFrames.flatten()
        // Never connect separate trials, and retain long gaps instead of discarding stalls.
        val intervals = trialFrames.flatMap { trial -> trial.sortedBy { it.time }.zipWithNext()
            .map { (a, b) -> (b.time - a.time) / 1e6 }.filter { it > 0 } }
        fun percentile(values: List<Double>, fraction: Double): Double =
            if (values.isEmpty()) 0.0 else values.sorted()[(ceil(values.size * fraction).toInt() - 1).coerceAtLeast(0)]
        fun p95(metric: (Frame) -> Long) = percentile(samples.map { metric(it) / 1e6 }, 0.95)
        return JSONObject().put("frames", samples.size)
            .put("intervalP50Ms", percentile(intervals, 0.5)).put("intervalP95Ms", percentile(intervals, 0.95))
            .put("totalP50Ms", percentile(samples.map { it.total / 1e6 }, 0.5)).put("totalP95Ms", p95 { it.total })
            .put("deadlineP50Ms", percentile(samples.filter { it.deadline > 0 }.map { it.deadline / 1e6 }, 0.5))
            .put("layoutP95Ms", p95 { it.layout }).put("drawP95Ms", p95 { it.draw })
            .put("unknownP95Ms", p95 { it.unknown }).put("gpuP95Ms", p95 { it.gpu })
            .put("lateFrames", samples.count { it.deadline > 0 && it.total > it.deadline })
            .put("longIntervals", intervals.count { it > 1.5 * 1000 / refreshHz })
            .put("intervalCount", intervals.size)
            .put("lostMetricCallbacks", samples.sumOf { it.lost })
    }

    private fun resetTo(label: String) {
        if (lastVerifiedPage == label) return
        if (isPageActive(label)) return
        val input = tapTab(label)
        awaitPage(label)
        awaitSettled(input.down)
    }

    private fun tapTab(label: String): Input {
        val bounds = tabBounds(label)
        val accessibilityBounds = Rect().also {
            instrumentation.uiAutomation.rootInActiveWindow!!.getBoundsInScreen(it)
        }
        var nativeX = 0f
        var nativeY = 0f
        activity.scenario.onActivity {
            val view = it.window.decorView
            nativeWindowWidth = view.width
            nativeWindowHeight = view.height
            val origin = IntArray(2).also(view::getLocationOnScreen)
            coordinateScaleX = view.width.toFloat() / accessibilityBounds.width()
            coordinateScaleY = view.height.toFloat() / accessibilityBounds.height()
            nativeX = origin[0] + (bounds.exactCenterX() - accessibilityBounds.left) * coordinateScaleX
            nativeY = origin[1] + (bounds.exactCenterY() - accessibilityBounds.top) * coordinateScaleY
        }
        NavigationMotionDiagnostics.event("test.tap", listOf(nativeX, nativeY, coordinateScaleX, coordinateScaleY))
        val down = SystemClock.uptimeMillis()
        val start = System.nanoTime()
        finger(MotionEvent.ACTION_DOWN, down, nativeX, nativeY)
        SystemClock.sleep(48)
        val up = System.nanoTime()
        finger(MotionEvent.ACTION_UP, down, nativeX, nativeY)
        return Input(start, up)
    }

    private fun swipe(source: String, target: String): Input {
        var width = 0
        var height = 0
        activity.scenario.onActivity { width = it.window.decorView.width; height = it.window.decorView.height }
        val order = listOf("课程", "导入", "设置")
        val left = order.indexOf(target) > order.indexOf(source)
        val first = width * if (left) 0.90f else 0.10f
        val last = width * if (left) 0.10f else 0.90f
        val y = height * 0.48f
        val down = SystemClock.uptimeMillis()
        val start = System.nanoTime()
        finger(MotionEvent.ACTION_DOWN, down, first, y)
        for (step in 1..16) {
            SystemClock.sleep(16)
            finger(MotionEvent.ACTION_MOVE, down, first + (last - first) * step / 16f, y)
        }
        val up = System.nanoTime()
        finger(MotionEvent.ACTION_UP, down, last, y)
        return Input(start, up)
    }

    private fun awaitSettled(start: Long, dock: Boolean = true) = await("Page, lens and companion fully settled") {
        val events = motion.toList()
        val page = events.lastOrNull { it.name == "page" }?.values
        val lens = events.lastOrNull { it.name == "lens" }?.values
        val companion = events.lastOrNull { it.name == "metaball" }?.values
        val quiet = System.nanoTime() - (events.lastOrNull()?.time ?: start) > 100_000_000L
        val request = events.lastOrNull { it.name == "page.request" && it.time >= start }
        val pageUpdate = events.lastOrNull { it.name == "page" }
        val acknowledged = if (dock) events.any { it.name == "dock.up" && it.time >= start }
            else pageUpdate != null && pageUpdate.time >= start
        val pageAcknowledged = request == null || (pageUpdate != null && pageUpdate.time >= request.time)
        acknowledged && pageAcknowledged && System.nanoTime() - start > 200_000_000L && quiet && page?.get(1) == 0f &&
            lens != null && kotlin.math.abs(lens[0] - lens[1]) < 0.001f && lens[2] < 0.001f &&
            kotlin.math.abs(lens[3] - 1f) < 0.001f && kotlin.math.abs(lens[4] - 1f) < 0.001f &&
            companion != null && kotlin.math.abs(companion[0] - companion[1]) < 0.001f
    }

    private fun finger(action: Int, down: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x; this.y = y
                pressure = if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) 0f else 1f
                size = 1f
            }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        // Wait for dispatch completion, not merely for acceptance into the system queue.
        // In particular, opening-window animations can otherwise swallow the first tap.
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }

    private fun tabBounds(label: String): Rect {
        var result: Rect? = null
        await("Dock tab $label") {
            val candidates = nodes().filter { it.first.text?.toString() == label && it.first.isVisibleToUser }
            val match = candidates.maxByOrNull { it.second.centerY() }
            result = match?.let { (leaf, bounds) ->
                // Hit the actual tab control rather than the bottom edge of its
                // text child (which may be recorded in a separate glass layer).
                var control: AccessibilityNodeInfo? = leaf
                while (control != null && !control.isClickable) control = control.parent
                control?.let { Rect().also(it::getBoundsInScreen) } ?: bounds
            }
            result?.isEmpty == false
        }
        return result!!
    }

    private fun awaitPage(label: String) {
        await("Fully active page $label") { isPageActive(label) }
        lastVerifiedPage = label
    }

    private fun isPageActive(label: String): Boolean = nodes().any { (node, _) ->
            val text = node.text?.toString().orEmpty()
            node.isVisibleToUser && when (label) {
                "课程" -> text.startsWith("课程（")
                "导入" -> text == "课程数据导入"
                else -> text == "学期与课表"
            }
    }

    private fun nodes(): List<Pair<AccessibilityNodeInfo, Rect>> {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        val found = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        fun visit(node: AccessibilityNodeInfo) {
            val rect = Rect().also { node.getBoundsInScreen(it) }
            found += node to rect
            repeat(node.childCount) { node.getChild(it)?.let(::visit) }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        return found
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) {
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("stream", "\nNAVIGATION_FAILURE=" + JSONObject()
                        .put("message", message)
                        .put("motion", JSONArray(motion.toList().takeLast(100).map {
                            JSONObject().put("name", it.name).put("values", JSONArray(it.values)).put("time", it.time)
                        })).toString() + "\n")
                })
                fail(message)
            }
            SystemClock.sleep(20)
        }
    }
}
