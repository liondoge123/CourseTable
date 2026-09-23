package com.coursetable.app.ui.liquid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AnchoredMenuPositionTest {
    @Test
    fun startAlignedMenuOpensBelowAnchor() {
        val position = calculateAnchoredMenuPosition(
            rootSize = IntSize(1080, 1920),
            menuSize = IntSize(440, 360),
            anchorBounds = Rect(36f, 80f, 240f, 152f),
            alignment = DropdownMenuAlignment.START,
            safeMarginPx = 24,
            gapPx = 16
        )

        assertEquals(IntOffset(36, 168), position)
    }

    @Test
    fun endAlignedMenuStaysInsideRightEdge() {
        val position = calculateAnchoredMenuPosition(
            rootSize = IntSize(1080, 1920),
            menuSize = IntSize(440, 360),
            anchorBounds = Rect(900f, 80f, 1068f, 152f),
            alignment = DropdownMenuAlignment.END,
            safeMarginPx = 24,
            gapPx = 16
        )

        assertEquals(IntOffset(616, 168), position)
    }

    @Test
    fun menuOpensAboveWhenBottomSpaceIsInsufficient() {
        val position = calculateAnchoredMenuPosition(
            rootSize = IntSize(1080, 1920),
            menuSize = IntSize(440, 480),
            anchorBounds = Rect(800f, 1700f, 1040f, 1772f),
            alignment = DropdownMenuAlignment.END,
            safeMarginPx = 24,
            gapPx = 16
        )

        assertEquals(IntOffset(600, 1204), position)
    }
}
