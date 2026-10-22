package com.coursetable.app.ui.liquid

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AnchoredMenuPositionTest {
    @Test fun centeredTouchMenuKeepsItsTopCenterOnTheTouch() {
        val position = calculateAnchoredMenuPosition(IntSize(1080, 1920), IntSize(440, 360),
            Rect(499f, 100f, 501f, 102f), DropdownMenuAlignment.CENTER, 24,
            overlapAnchor = false, gapPx = 8)
        assertEquals(IntOffset(280, 110), position)
    }

    @Test fun centeredTouchMenuAvoidsBothHorizontalEdges() {
        listOf(10f to 24, 1070f to 616).forEach { (touch, expectedLeft) ->
            val position = calculateAnchoredMenuPosition(IntSize(1080, 1920), IntSize(440, 360),
                Rect(touch, 100f, touch + 1f, 101f), DropdownMenuAlignment.CENTER, 24,
                overlapAnchor = false, gapPx = 8)
            assertEquals(expectedLeft, position.x)
        }
    }

    @Test fun centeredGrowthKeepsTheTopEdgeNearTheTouchThroughoutTheMotion() {
        listOf(0.1f, 0.5f, 0.9f, 1f).forEach { progress ->
            val frame = anchoredMenuMorphFrame(IntOffset(280, 108), Size(441f, 360f),
                Rect(500f, 100f, 501f, 101f), progress, 24f,
                horizontalProgress = progress * 0.8f, verticalProgress = progress * 0.9f,
                anchorToVerticalEdge = true)
            assertEquals(500.5f, 280 + frame.translation.x + 441f * frame.scaleX / 2f, 0.001f)
            assertEquals(100f + 8f * progress, 108 + frame.translation.y, 0.001f)
        }
    }

    @Test fun centeredMenuFlippedAboveGrowsFromItsBottomEdge() {
        val frame = anchoredMenuMorphFrame(IntOffset(280, 1332), Size(441f, 360f),
            Rect(500f, 1700f, 501f, 1701f), 0.4f, 24f, anchorToVerticalEdge = true)
        assertEquals(1697.4f, 1332 + frame.translation.y + 360f * frame.scaleY, 0.001f)
    }

    @Test fun settingsMenuOpensBelowItsValueAndKeepsTheRightEdge() {
        val position = calculateAnchoredMenuPosition(IntSize(1080, 1920), IntSize(440, 360),
            Rect(800f, 120f, 1040f, 200f), DropdownMenuAlignment.END, 24,
            overlapAnchor = false, gapPx = 8)
        assertEquals(IntOffset(600, 208), position)
    }

    @Test fun settingsMenuFlipsAboveItsValueNearTheBottom() {
        val position = calculateAnchoredMenuPosition(IntSize(1080, 1920), IntSize(440, 360),
            Rect(800f, 1750f, 1040f, 1830f), DropdownMenuAlignment.END, 24,
            overlapAnchor = false, gapPx = 8)
        assertEquals(IntOffset(600, 1382), position)
    }

    @Test fun travelCanLeadGrowthWithoutStretchingTheSourceGeometry() {
        val source = Rect(800f, 80f, 848f, 128f)
        val position = IntOffset(672, 80)
        val frame = anchoredMenuMorphFrame(position, Size(176f, 152f), source, 0.4f, 24f,
            horizontalProgress = 0.6f, verticalProgress = 0.8f)
        assertEquals(99.2f, 176f * frame.scaleX, 0.001f)
        assertEquals(89.6f, 152f * frame.scaleY, 0.001f)
        assertEquals(785.6f, position.x + frame.translation.x + 176f * frame.scaleX / 2f, 0.001f)
        assertEquals(145.6f, position.y + frame.translation.y + 152f * frame.scaleY / 2f, 0.001f)
    }

    @Test fun overlapStillRespectsTheTopAndLeftSafeMargin() {
        val position = calculateAnchoredMenuPosition(IntSize(1080, 1920), IntSize(440, 360),
            Rect(0f, 0f, 48f, 48f), DropdownMenuAlignment.START, 24)
        assertEquals(IntOffset(24, 24), position)
    }

    @Test fun partiallyOffscreenTriggerCannotPushTheMenuOutsideTheBottomEdge() {
        val position = calculateAnchoredMenuPosition(IntSize(1080, 1920), IntSize(440, 360),
            Rect(1048f, 1900f, 1096f, 1948f), DropdownMenuAlignment.END, 24)
        assertEquals(IntOffset(616, 1536), position)
    }

    @Test fun collapsedMenuMatchesTheWholeSourceRectangle() {
        val source = Rect(900f, 80f, 1068f, 152f)
        val position = IntOffset(616, 80)
        val frame = anchoredMenuMorphFrame(position, Size(440f, 360f), source, 0f, 24f)
        assertEquals(source.left, position.x + frame.translation.x, 0.001f)
        assertEquals(source.top, position.y + frame.translation.y, 0.001f)
        assertEquals(source.width, 440f * frame.scaleX, 0.001f)
        assertEquals(source.height, 360f * frame.scaleY, 0.001f)
        assertEquals(36f, frame.cornerRadius.x * frame.scaleX, 0.001f)
        assertEquals(36f, frame.cornerRadius.y * frame.scaleY, 0.001f)
    }

    @Test fun expandedMenuKeepsItsFinalBoundsAndRadius() {
        val frame = anchoredMenuMorphFrame(IntOffset(600, 1204), Size(440f, 480f),
            Rect(800f, 1700f, 1040f, 1772f), 1f, 24f)
        assertEquals(1f, frame.scaleX, 0f)
        assertEquals(1f, frame.scaleY, 0f)
        assertEquals(0f, frame.translation.x, 0f)
        assertEquals(0f, frame.translation.y, 0f)
        assertEquals(24f, frame.cornerRadius.x, 0f)
        assertEquals(24f, frame.cornerRadius.y, 0f)
    }

    @Test fun widthHeightAndPositionMorphIndependently() {
        val frame = anchoredMenuMorphFrame(IntOffset(100, 100), Size(160f, 240f),
            Rect(0f, 0f, 48f, 48f), 0.5f, 24f)
        assertEquals(50f, 100f + frame.translation.x, 0.001f)
        assertEquals(50f, 100f + frame.translation.y, 0.001f)
        assertEquals(104f, 160f * frame.scaleX, 0.001f)
        assertEquals(144f, 240f * frame.scaleY, 0.001f)
    }

    @Test
    fun startAlignedMenuOverlapsTheTriggerTopCorner() {
        val position = calculateAnchoredMenuPosition(
            rootSize = IntSize(1080, 1920),
            menuSize = IntSize(440, 360),
            anchorBounds = Rect(36f, 80f, 240f, 152f),
            alignment = DropdownMenuAlignment.START,
            safeMarginPx = 24
        )

        assertEquals(IntOffset(36, 80), position)
    }

    @Test
    fun endAlignedMenuStaysInsideRightEdge() {
        val position = calculateAnchoredMenuPosition(
            rootSize = IntSize(1080, 1920),
            menuSize = IntSize(440, 360),
            anchorBounds = Rect(900f, 80f, 1068f, 152f),
            alignment = DropdownMenuAlignment.END,
            safeMarginPx = 24
        )

        assertEquals(IntOffset(616, 80), position)
    }

    @Test
    fun menuOpensAboveWhenBottomSpaceIsInsufficient() {
        val position = calculateAnchoredMenuPosition(
            rootSize = IntSize(1080, 1920),
            menuSize = IntSize(440, 480),
            anchorBounds = Rect(800f, 1700f, 1040f, 1772f),
            alignment = DropdownMenuAlignment.END,
            safeMarginPx = 24
        )

        assertEquals(IntOffset(600, 1292), position)
    }
}
