package com.coursetable.app

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.TimetableCourseCardSurface
import com.coursetable.app.ui.TimetableCourseProgress
import com.coursetable.app.ui.liquid.LocalBackLayerActive
import com.coursetable.app.ui.theme.CourseTableTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimetableRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingRootInputOwnershipDoesNotRecomposeCourseCards() {
        var active by mutableStateOf(true)
        var observed = true
        val compositions = AtomicInteger()
        compose.setContent {
            CourseTableTheme {
                CompositionLocalProvider(LocalBackLayerActive provides active) {
                    InputConsumer { observed = it }
                    Column {
                        repeat(6) {
                            Row { repeat(7) { CountedCard(compositions) } }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val before = compositions.get()
        repeat(8) {
            compose.runOnIdle { active = !active }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(active, observed) }
        }
        val extra = compositions.get() - before
        Log.i("TimetableRendering", "ownershipChanges=8, cards=42, extraCardCompositions=$extra")
        assertEquals("Changing input ownership must not invalidate the course-card subtree", 0, extra)
    }

    @Test fun courseProgressAnimatesByDrawingWithoutRemeasuring() {
        var progress by mutableFloatStateOf(0.1f)
        val measures = AtomicInteger()
        compose.setContent {
            TimetableCourseProgress(progress, Color.Red,
                Modifier.size(12.dp, 200.dp).testTag("progress").layout { measurable, constraints ->
                    measures.incrementAndGet()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                })
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val before = measures.get()
        compose.runOnIdle { progress = 0.9f }
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        val middle = compose.onNodeWithTag("progress").captureToImage().toPixelMap()
        assertTrue(middle[middle.width / 2, middle.height / 3].green < 0.02f)
        compose.mainClock.advanceTimeBy(700)
        val end = compose.onNodeWithTag("progress").captureToImage().toPixelMap()
        assertTrue(end[end.width / 2, (end.height * 0.8f).toInt()].green < 0.02f)
        assertEquals("Animation must not remeasure the progress strip", before, measures.get())
        Log.i("TimetableRendering", "progressAnimationExtraMeasures=${measures.get() - before}")
        compose.mainClock.autoAdvance = true
    }
}

@Composable
private fun InputConsumer(onActive: (Boolean) -> Unit) {
    val active = LocalBackLayerActive.current
    SideEffect { onActive(active) }
}

@Composable
private fun CountedCard(count: AtomicInteger) {
    SideEffect { count.incrementAndGet() }
    TimetableCourseCardSurface("通信电路", "老师", "教学楼2102", Color.Cyan, 2,
        modifier = Modifier.size(46.dp, 124.dp), onClick = {})
}
