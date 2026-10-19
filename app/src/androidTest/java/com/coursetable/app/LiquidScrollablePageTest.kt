package com.coursetable.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Course
import com.coursetable.app.ui.AboutScreen
import com.coursetable.app.ui.CourseManageScreen
import com.coursetable.app.ui.SettingsScreen
import com.coursetable.app.ui.TimetableScreen
import com.coursetable.app.ui.TimetableViewModel
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiquidScrollablePageTest {
    @get:Rule val compose = createComposeRule()
    private fun show(dark: Boolean = false, static: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            val activity = LocalActivity.current as ComponentActivity
            SideEffect { activity.enableEdgeToEdge() }
            CourseTableTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    if (static) CompositionLocalProvider(LocalGlassCapability provides GlassCapability.STATIC) { content() }
                    else content()
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun viewportReachesStatusBarAndScrolledRowsPassUnderIt() {
        lateinit var state: LazyListState
        var statusTop = 0f
        show {
            val density = LocalDensity.current
            val top = WindowInsets.systemBars.getTop(density)
            SideEffect { statusTop = top.toFloat() }
            state = rememberLazyListState()
            LiquidScrollablePage { insets ->
                LazyColumn(Modifier.fillMaxSize().testTag("edge-list"), state, contentPadding = insets.pagePadding()) {
                    items(40) { index -> Text("记录 $index", Modifier.fillMaxWidth().height(64.dp).testTag("edge-row-$index")) }
                }
            }
        }
        compose.waitUntil(5_000) { statusTop > 0 }
        assertEquals(0f, compose.onNodeWithTag("edge-list").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertTrue(compose.onNodeWithTag("edge-row-0").fetchSemanticsNode().boundsInRoot.top >= statusTop)
        compose.runOnIdle { runBlocking { state.scrollToItem(8, (statusTop + 60).toInt()) } }
        compose.onNodeWithTag("edge-row-8").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("edge-row-8").fetchSemanticsNode().boundsInRoot.top < statusTop)
    }

    @Test fun fixedToolbarStaysVisibleAndFadeDoesNotConsumeTaps() {
        var actions = 0
        var backs = 0
        show(dark = true) {
            LiquidScrollablePage(topBar = { LiquidPageTopBar("固定标题", { backs++ }) }) { insets ->
                LazyColumn(Modifier.fillMaxSize().testTag("toolbar-list"), contentPadding = insets.pagePadding(vertical = 0.dp)) {
                    item { Button({ actions++ }, Modifier.fillMaxWidth().height(48.dp)) { Text("执行操作") } }
                    items(35) { Text("滚动记录 $it", Modifier.height(64.dp).clickable { actions++ }) }
                }
            }
        }
        val before = compose.onNodeWithText("固定标题").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("执行操作").performTouchInput { click(Offset(center.x, 4.dp.toPx())) }
        compose.runOnIdle { assertEquals(1, actions) }
        compose.onNodeWithTag("toolbar-list").performTouchInput { swipeUp() }
        val after = compose.onNodeWithText("固定标题").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onNodeWithText("固定标题").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, actions) }
        compose.onNodeWithContentDescription("返回").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun courseLargeTitleScrollsWithItsList() {
        val courses = (1..40).map { Course(id = it.toLong(), courseId = it.toLong(), name = "课程 $it", dayOfWeek = 1,
            startSection = 1, duration = 2, startWeek = 1, endWeek = 18) }
        show { CourseManageScreen(courses, 18, 12, {}, {}, {}, rootMode = true) }
        compose.onNodeWithText("课程（40 门）").assertIsDisplayed()
        compose.onNodeWithTag("courses-list").performTouchInput { swipeUp() }
        compose.onNodeWithText("课程（40 门）").assertIsNotDisplayed()
    }

    @Test fun settingsLargeTitleScrollsAndStaticFallbackRemainsUsable() {
        show(static = true) { SettingsScreen() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-scroll").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithTag("settings-scroll").performTouchInput { swipeUp() }
        compose.onNodeWithText("设置").assertIsNotDisplayed()
        assertEquals(0f, compose.onNodeWithTag("settings-scroll").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun aboutBackButtonRemainsAvailableAfterScrolling() {
        var backs = 0
        show { AboutScreen("test", { backs++ }) }
        compose.onNodeWithText("Apache-2.0 License", substring = true).performScrollTo()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun timetableRestoresFloatingWeekControlsAndScrollingWeekdays() {
        show {
            val app = LocalContext.current.applicationContext as CourseApp
            val vm = androidx.lifecycle.viewmodel.compose.viewModel { TimetableViewModel(app) }
            // Match MainActivity's floating dock inset so the full grid can scroll.
            TimetableScreen(vm, bottomContentPadding = 88.dp)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("timetable-weekdays").fetchSemanticsNodes().isNotEmpty() }
        val before = compose.onNodeWithTag("timetable-weekdays").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("timetable-week-controls").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("timetable-scroll").performTouchInput { swipeUp() }
        assertTrue(compose.onNodeWithTag("timetable-weekdays").fetchSemanticsNode().boundsInRoot.top < before.top)
        assertEquals(controls, compose.onNodeWithTag("timetable-week-controls").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("下一周").assertIsDisplayed()
    }

    @Test fun topMaterialDoesNotLeakSharpStripesAtTheWindowEdge() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 31)
        var statusTop = 0
        show {
            val density = LocalDensity.current
            val top = WindowInsets.statusBars.getTop(density)
            SideEffect { statusTop = top }
            LiquidScrollablePage {
                Canvas(Modifier.fillMaxSize()) {
                    val stripe = 3.dp.toPx()
                    repeat((size.width / stripe).toInt() + 1) { index ->
                        drawRect(if (index % 2 == 0) Color.White else Color.Black,
                            Offset(index * stripe, 0f), Size(stripe, size.height))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.waitUntil(5_000) { statusTop > 0 }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val samples = (pixels.width * 2 / 5 until pixels.width * 3 / 5).map { pixels[it, 8].red }
        assertTrue("Sharp content leaked through the glass at the screen edge", samples.max() - samples.min() < .25f)
        val below = (pixels.width * 2 / 5 until pixels.width * 3 / 5).map { pixels[it, statusTop + 8].red }
        assertTrue("Status blur extended into the page", below.max() - below.min() > .85f)
    }

    @Test fun statusBlurDoesNotGrowWithTheFixedHeader() {
        var statusTop = 0f
        show {
            val density = LocalDensity.current
            val top = WindowInsets.statusBars.getTop(density)
            SideEffect { statusTop = top.toFloat() }
            LiquidScrollablePage(topBar = {
                Box(Modifier.fillMaxWidth().height(120.dp).testTag("tall-header")) { Text("清晰顶栏") }
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    val stripe = 3.dp.toPx()
                    repeat((size.width / stripe).toInt() + 1) { index ->
                        drawRect(if (index % 2 == 0) Color.White else Color.Black,
                            Offset(index * stripe, 0f), Size(stripe, size.height))
                    }
                }
            }
        }
        compose.waitUntil(5_000) { statusTop > 0 }
        val edge = compose.onNodeWithTag("liquid-top-edge", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("tall-header").fetchSemanticsNode().boundsInRoot
        assertEquals(statusTop, edge.bottom, 1f)
        assertTrue("Blur overlaps app header", edge.bottom <= header.top + 1f)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val headerSamples = (pixels.width * 2 / 5 until pixels.width * 3 / 5).map { pixels[it, header.center.y.toInt()].red }
        assertTrue("Scrolling rows remain visible through the fixed header", headerSamples.max() - headerSamples.min() < .1f)
    }

    @Test fun strokesCrossingStatusEdgesDoNotInflateTheDarkBlur() = assertStableStatusEdges(dark = false)

    @Test fun brightStrokesCrossingStatusEdgesDoNotInflateTheLightBlur() = assertStableStatusEdges(dark = true)

    private fun assertStableStatusEdges(dark: Boolean) {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 31)
        var strokeTop by mutableFloatStateOf(-1000f)
        var statusTop = 0
        var strokeHeight = 0f
        show(dark = dark) {
            val density = LocalDensity.current
            val top = WindowInsets.statusBars.getTop(density)
            SideEffect { statusTop = top; strokeHeight = with(density) { 6.dp.toPx() } }
            LiquidScrollablePage {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(if (dark) Color.Black else Color.White)
                    drawRect(if (dark) Color.White else Color.Black,
                        Offset(size.width * .3f, strokeTop), Size(size.width * .4f, strokeHeight))
                }
            }
        }
        compose.waitUntil(5_000) { statusTop > 0 && strokeHeight > 0 }
        fun sample(position: Float, y: Int): Float {
            compose.runOnIdle { strokeTop = position }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            return (pixels.width * 9 / 20 until pixels.width * 11 / 20).map { pixels[it, y].red }.average().toFloat()
        }
        // A one-pixel stroke entering/leaving the sample must not become an extended black edge.
        val topFrames = (-2..2).map { sample(-strokeHeight + it, 3) }
        val bottomFrames = (-2..2).map { sample(statusTop + it.toFloat(), statusTop / 2) }
        val topJump = topFrames.zipWithNext { a, b -> kotlin.math.abs(a - b) }.max()
        val bottomJump = bottomFrames.zipWithNext { a, b -> kotlin.math.abs(a - b) }.max()
        android.util.Log.i("StatusBlurRegression", "dark=$dark topJump=$topJump bottomJump=$bottomJump")
        assertTrue("Status edge amplified a 1px stroke: top=$topJump bottom=$bottomJump", topJump < .08f && bottomJump < .08f)
    }
}
