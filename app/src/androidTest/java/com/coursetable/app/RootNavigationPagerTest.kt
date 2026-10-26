package com.coursetable.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.coursetable.app.data.Course
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class RootNavigationPagerTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var navigation: RootNavigationState
    private lateinit var scope: CoroutineScope
    private var enabled by mutableStateOf(true)
    private var dialog by mutableStateOf(false)
    private var direction by mutableStateOf(LayoutDirection.Ltr)
    private val visiblePages = mutableSetOf<RootDestination>()
    private val bounds = mutableMapOf<RootDestination, Rect>()
    private val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
    private val observedSelections = mutableListOf<RootDestination>()

    @Composable
    private fun SelectionObserver(navigation: RootNavigationState) {
        val selected = navigation.destination
        SideEffect { observedSelections += selected }
    }

    private fun show(initial: RootDestination = RootDestination.IMPORT, courseRows: Boolean = false) {
        compose.setContent {
          CompositionLocalProvider(LocalLayoutDirection provides direction) {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                    navigation = rememberRootNavigationState(initial)
                    SelectionObserver(navigation)
                    scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        RootNavigationHost(navigation, enabled) { page ->
                            DisposableEffect(page) {
                                visiblePages.add(page)
                                onDispose { visiblePages.remove(page) }
                            }
                            if (page == RootDestination.COURSES && courseRows) {
                                CourseManageScreen(listOf(Course(id = 1, name = "手势测试课程", dayOfWeek = 1,
                                    startSection = 1, duration = 2, startWeek = 1, endWeek = 16)),
                                    18, 12, {}, {}, {}, rootMode = true)
                            } else Box(Modifier.fillMaxSize().background(colors[page.ordinal])
                                .onGloballyPositioned {
                                    val origin = it.localToRoot(Offset.Zero)
                                    bounds[page] = Rect(origin.x, origin.y,
                                        origin.x + it.size.width, origin.y + it.size.height)
                                }
                                .testTag("page-${page.name}")) {
                                var value by rememberSaveable { mutableIntStateOf(0) }
                                Text("${page.name}: $value", Modifier.testTag("value-${page.name}").clickable { value++ })
                            }
                        }
                    }
                    if (dialog) AlertDialog({ dialog = false }, title = { Text("测试弹窗") }, confirmButton = {
                        val dismiss = LocalDialogDismissController.current
                        TextButton({ dismiss?.dismiss() }) { Text("关闭弹窗") }
                    })
                }
            }
          }
        }
        compose.waitForIdle()
    }

    private fun selected(destination: RootDestination) = compose.runOnIdle {
        assertFalse(navigation.isTransitioning)
        assertEquals(destination, navigation.settledDestination)
        assertEquals(destination, navigation.destination)
        assertEquals(navigation.transition.currentState, navigation.transition.targetState)
    }

    @Test fun clickingADistantTabRendersOnlyTheSourceAndTargetPages() {
        show(RootDestination.TIMETABLE)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        var sawBoth = false
        repeat(24) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertTrue("Only requested pages may be composed: $visiblePages",
                    visiblePages.all { it == RootDestination.TIMETABLE || it == RootDestination.SETTINGS })
                sawBoth = sawBoth || visiblePages.size == 2
                assertEquals(RootDestination.SETTINGS, navigation.destination)
            }
        }
        assertTrue(sawBoth)
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun dockClickUsesTheFullWidthSwipeWithoutFading() {
        show(RootDestination.TIMETABLE)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle {
            val source = bounds.getValue(RootDestination.TIMETABLE)
            val target = bounds.getValue(RootDestination.SETTINGS)
            assertTrue("The source must move across the screen: $source", source.left in -source.width..-source.width * 0.25f)
            assertEquals("The pages must meet without a gap or overlap", source.right, target.left, 1.1f)
        }
        val pixels = compose.onNodeWithTag("root-pager").captureToImage().toPixelMap()
        for (x in 8 until pixels.width - 8 step 16) {
            val color = pixels[x, pixels.height / 2]
            assertTrue("Pages must remain opaque, with no fade or color blending: $color",
                color == Color.Red || color == Color.Yellow)
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun dockClicksFollowTheSameLayoutDirectionAsFingerSwipes() {
        show(RootDestination.COURSES)
        compose.runOnIdle { direction = LayoutDirection.Rtl }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle {
            val source = bounds.getValue(RootDestination.COURSES)
            val target = bounds.getValue(RootDestination.SETTINGS)
            assertTrue("RTL must slide the source right: $source", source.left > source.width * 0.25f)
            assertEquals(target.right, source.left, 1.1f)
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun dockTransitionHasNoLogicalPauseInTheMiddle() {
        show(RootDestination.TIMETABLE)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        val positions = mutableListOf<Float>()
        repeat(24) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                positions += if (navigation.settledDestination == RootDestination.SETTINGS) 1f
                    else navigation.transition.fraction
            }
        }
        assertTrue(positions.any { it in 0.25f..0.75f })
        for ((before, after) in positions.zipWithNext()) {
            assertTrue("Motion must not reverse: $positions", after >= before)
            if (before in 0.05f..0.95f) assertTrue("No paused middle tick: $positions", after > before)
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun distantClicksInBothDirectionsKeepTheNormalSwipeOrder() {
        show(RootDestination.SETTINGS)
        compose.runOnIdle { navigation.navigateTo(RootDestination.COURSES) }
        selected(RootDestination.COURSES)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.IMPORT)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeRight() }
        selected(RootDestination.COURSES)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeRight() }
        selected(RootDestination.TIMETABLE)
    }

    @Test fun renderedFramesOfADistantClickContainNoIntermediatePageColors() {
        show(RootDestination.TIMETABLE)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        repeat(5) {
            compose.mainClock.advanceTimeBy(48)
            val pixels = compose.onNodeWithTag("root-pager").captureToImage().toPixelMap()
            for (x in 8 until pixels.width - 8 step 16) {
                val color = pixels[x, pixels.height / 2]
                assertTrue("No green/blue intermediate pages in frame $it at $x: $color",
                    color.blue < 0.02f && color.red >= color.green - 0.02f)
            }
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun directClicksKeepEachDestinationsSavedUiState() {
        show(RootDestination.COURSES)
        compose.onNodeWithTag("value-COURSES").performClick()
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.onNodeWithTag("value-SETTINGS").performClick()
        compose.runOnIdle { navigation.navigateTo(RootDestination.COURSES) }
        compose.onNodeWithTag("value-COURSES").assertTextEquals("COURSES: 1")
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.onNodeWithTag("value-SETTINGS").assertTextEquals("SETTINGS: 1")
    }

    @Test fun rapidTabClicksKeepOnlyTheLatestRequest() {
        show(RootDestination.TIMETABLE)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            navigation.navigateTo(RootDestination.COURSES)
            navigation.navigateTo(RootDestination.IMPORT)
        }
        repeat(32) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertEquals(RootDestination.IMPORT, navigation.destination) }
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.IMPORT)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
    }

    @Test fun aThirdTabKeepsVisiblePagesInPlaceAndDoesNotMultiplyTheirSpeed() {
        show(RootDestination.TIMETABLE)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.COURSES) }
        compose.mainClock.advanceTimeBy(80)
        var before = 0f
        compose.runOnIdle {
            before = navigation.transition.position
            navigation.navigateTo(RootDestination.IMPORT)
            assertEquals("Retarget must preserve the rendered position", before, navigation.transition.position, 0f)
        }
        var previous = before
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                val next = navigation.transition.position
                assertTrue("Retarget must keep moving forward", next >= previous)
                assertTrue("No combined-animation sprint: $previous -> $next", next - previous < 0.16f)
                val source = bounds.getValue(RootDestination.TIMETABLE)
                val first = bounds.getValue(RootDestination.COURSES)
                val last = bounds.getValue(RootDestination.IMPORT)
                assertEquals(source.right, first.left, 1.1f)
                assertEquals(first.right, last.left, 1.1f)
                previous = next
            }
            val pixels = compose.onNodeWithTag("root-pager").captureToImage().toPixelMap()
            for (x in 8 until pixels.width - 8 step 24) {
                assertTrue("The retarget must never expose the background",
                    pixels[x, pixels.height / 2] in listOf(Color.Red, Color.Green, Color.Blue))
            }
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.IMPORT)
    }

    @Test fun reversingAClickBrakesContinuouslyWithoutReplacingTheVisiblePages() {
        show(RootDestination.COURSES)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.mainClock.advanceTimeBy(80)
        var previous = 0f
        compose.runOnIdle {
            previous = navigation.transition.position
            navigation.navigateTo(RootDestination.COURSES)
            assertEquals(previous, navigation.transition.position, 0f)
        }
        val positions = mutableListOf(previous)
        repeat(32) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                val next = navigation.transition.position
                assertTrue("Reversal must remain continuous: $previous -> $next", abs(next - previous) < 0.16f)
                positions += next
                previous = next
            }
        }
        assertTrue("Existing momentum must brake before reversing", positions.max() > positions.first())
        compose.mainClock.autoAdvance = true
        selected(RootDestination.COURSES)
    }

    @Test fun clickingBackToAnOutgoingPageDoesNotDuplicateItsSavedStateProvider() {
        show(RootDestination.COURSES)
        compose.onNodeWithTag("value-COURSES").performClick()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { navigation.navigateTo(RootDestination.COURSES) }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.COURSES)
        compose.onNodeWithTag("value-COURSES").assertTextEquals("COURSES: 1")
    }

    private fun pressDuringDockArrival(reverse: Boolean) {
        show(RootDestination.COURSES)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.TIMETABLE) }
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("root-pager").performTouchInput { down(center) }
        if (reverse) compose.onNodeWithTag("root-pager").performTouchInput {
            moveBy(Offset(-width * 0.04f, 0f), delayMillis = 80)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertFalse("A press must not stop a Dock transition", navigation.isTransitioning) }
        compose.onNodeWithTag("root-pager").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.TIMETABLE)
    }

    @Test fun holdingDuringTimetableArrivalCannotInterruptTheDockTransition() = pressDuringDockArrival(false)
    @Test fun aSmallReverseDragDuringTimetableArrivalCannotStrandAPage() = pressDuringDockArrival(true)

    @Test fun aDockRequestDuringAHeldSwipeCanRetargetToAnotherPage() {
        show(RootDestination.COURSES)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("root-pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.25f, 0f), delayMillis = 160)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.onNodeWithTag("root-pager").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun clickingTheVisibleSwipeTargetContinuesWithoutReturningToTheSource() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("root-pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.25f, 0f), delayMillis = 160)
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        var before = 0f
        compose.runOnIdle {
            before = navigation.transition.fraction
            assertTrue(before > 0.15f)
            navigation.navigateTo(RootDestination.SETTINGS)
        }
        compose.onNodeWithTag("root-pager").performTouchInput { up() }
        val positions = mutableListOf(before)
        repeat(18) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                positions += if (navigation.settledDestination == RootDestination.SETTINGS) 1f
                    else navigation.transition.fraction
            }
        }
        positions.zipWithNext().forEach { (previous, next) ->
            assertTrue("Click must continue the swipe without first settling back: $positions", next >= previous)
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun externalImportCanSnapDuringAClickWithoutLeavingOldPages() {
        show(RootDestination.COURSES)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.navigateTo(RootDestination.SETTINGS) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { scope.launch { navigation.scrollTo(RootDestination.IMPORT) } }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.IMPORT)
    }

    @Test fun horizontalSwipesSelectAdjacentPagesAndStopAtTheLastPage() {
        show()
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeRight() }
        selected(RootDestination.IMPORT)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeRight() }
        selected(RootDestination.COURSES)
    }

    @Test fun aShortDragFollowsTheFingerAndCancelsBackToItsPage() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("root-pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.16f, 0f), delayMillis = 160)
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertTrue(navigation.isDragging)
            assertTrue("The page must move before release", navigation.transition.fraction > 0.05f)
            val source = bounds.getValue(RootDestination.IMPORT)
            assertTrue("The drag must follow the finger: $source", -source.left / source.width in 0.10f..0.19f)
        }
        val pixels = compose.onNodeWithTag("root-pager").captureToImage().toPixelMap()
        assertEquals(Color.Blue, pixels[pixels.width / 2, pixels.height / 2])
        assertEquals(Color.Yellow, pixels[(pixels.width * 0.95f).toInt(), pixels.height / 2])
        compose.onNodeWithTag("root-pager").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.IMPORT)
    }

    private fun assertShortSwipeSelection(pages: Float) {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.beginDrag(); navigation.dragBy(pages) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertTrue(navigation.transition.fraction > 0.1f)
            navigation.endDrag(0f, 1f)
            assertEquals("The Dock must keep the source selected on release", RootDestination.IMPORT, navigation.destination)
        }
        repeat(45) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals("Returning frames must not send the lens to the neighboring tab", RootDestination.IMPORT, navigation.destination)
            }
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.IMPORT)
        compose.runOnIdle { assertTrue(observedSelections.all { it == RootDestination.IMPORT }) }
    }

    @Test fun shortForwardSwipeKeepsTheDockOnItsSourceThroughoutSnapBack() = assertShortSwipeSelection(0.25f)

    @Test fun shortBackwardSwipeKeepsTheDockOnItsSourceThroughoutSnapBack() = assertShortSwipeSelection(-0.25f)

    @Test fun aReverseFlingSelectsTheSourceImmediatelyEvenAfterCrossingHalfway() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.beginDrag(); navigation.dragBy(0.65f) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertEquals(RootDestination.SETTINGS, navigation.destination)
            navigation.endDrag(-2f, 1f)
            assertEquals("A reverse release must select its actual landing page", RootDestination.IMPORT, navigation.destination)
        }
        repeat(45) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertEquals(RootDestination.IMPORT, navigation.destination) }
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.IMPORT)
    }

    @Test fun aFastShortSwipeSelectsTheCommittedPageOnRelease() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.beginDrag(); navigation.dragBy(0.25f) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertEquals(RootDestination.IMPORT, navigation.destination)
            navigation.endDrag(2f, 1f)
            assertEquals(RootDestination.SETTINGS, navigation.destination)
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun courseRowSwipesNavigateWithoutDeletingOrOpeningTheEditor() {
        show(RootDestination.COURSES, courseRows = true)
        compose.onNodeWithTag("managed-course-1").performTouchInput { swipeLeft() }
        selected(RootDestination.IMPORT)
        compose.onNodeWithTag("course-editor-name").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除课程").assertDoesNotExist()
        compose.onNodeWithTag("root-pager").performTouchInput { swipeRight() }
        selected(RootDestination.COURSES)
        compose.onNodeWithTag("managed-course-1").performTouchInput { swipeRight() }
        selected(RootDestination.TIMETABLE)
    }

    @Test fun aNewPressDuringSnapBackKeepsTheVisibleSwipeOffset() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("root-pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.28f, 0f), delayMillis = 160)
            cancel()
        }
        compose.mainClock.advanceTimeBy(64)
        var before = 0f
        compose.runOnIdle {
            assertTrue(navigation.isSwiping)
            before = navigation.transition.fraction
            assertTrue(before > 0.02f)
        }
        compose.onNodeWithTag("root-pager").performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertTrue(navigation.isDragging)
            assertEquals("Touch-down must preserve the visible offset", before, navigation.transition.fraction, 0.03f)
        }
        compose.onNodeWithTag("root-pager").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.IMPORT)
    }

    @Test fun releasingASlowSwipeHasAGentleTailInsteadOfFinishingIn180Milliseconds() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.beginDrag(); navigation.dragBy(0.65f) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { navigation.endDrag(0f, 1f) }
        compose.mainClock.advanceTimeBy(180)
        compose.runOnIdle {
            assertTrue("A slow release must retain a visible easing tail", navigation.isTransitioning)
            assertTrue(navigation.transition.fraction in 0.9f..0.99f)
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun fingerDeltasDoNotRecomposeTheSelectedTabUntilTheNearestPageChanges() {
        show()
        val initialCount = observedSelections.size
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.beginDrag() }
        repeat(8) {
            compose.runOnIdle { navigation.dragBy(0.05f) }
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
        }
        compose.runOnIdle {
            assertEquals("Per-delta movement must not invalidate MainScreen's selection", initialCount,
                observedSelections.size)
            navigation.dragBy(0.2f)
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertEquals(RootDestination.SETTINGS, observedSelections.last())
            assertEquals(initialCount + 1, observedSelections.size)
            navigation.endDrag(0f, 1f)
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.SETTINGS)
    }

    @Test fun reversingADragTowardTheTimetableCanReturnToCourses() {
        show(RootDestination.COURSES)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("root-pager").performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveBy(Offset(width * 0.65f, 0f), delayMillis = 160)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(RootDestination.TIMETABLE, navigation.destination) }
        compose.onNodeWithTag("root-pager").performTouchInput {
            moveBy(Offset(-width * 0.65f, 0f), delayMillis = 160)
            cancel()
        }
        compose.mainClock.autoAdvance = true
        selected(RootDestination.COURSES)
    }

    @Test fun timetableAndBlockedPagesDoNotNavigateOnHorizontalDrags() {
        show(RootDestination.TIMETABLE)
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.TIMETABLE)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.TIMETABLE)
    }

    @Test fun blockingAndUnblockingAnOrdinaryPageRestoresItsGesture() {
        show()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.IMPORT)
        compose.runOnIdle { enabled = true }
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
    }

    @Test fun draggingADialogDoesNotMoveThePageBehindIt() {
        show()
        compose.runOnIdle { dialog = true }
        compose.onNodeWithTag("overlay-glass-dialog").performTouchInput { swipeLeft() }
        selected(RootDestination.IMPORT)
        compose.onNodeWithText("关闭弹窗").performClick()
        compose.onNodeWithTag("root-pager").performTouchInput { swipeLeft() }
        selected(RootDestination.SETTINGS)
    }
}
