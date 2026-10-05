package com.coursetable.app

import com.coursetable.app.ui.liquid.backPageTransform
import com.coursetable.app.ui.liquid.backPreviewProgress
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BackPageMotionTest {
    @Test fun previewIsDeceleratedAndMonotonicInsteadOfLinear() {
        assertEquals(0f, backPreviewProgress(0f), 0f)
        assertEquals(1f, backPreviewProgress(1f), 0f)
        assertTrue(backPreviewProgress(0.2f) > 0.2f)
        val samples = (0..100).map { backPreviewProgress(it / 100f) }
        assertTrue(samples.zipWithNext().all { (first, second) -> second >= first })
    }

    @Test fun completePreviewUsesNinetyPercentScaleAndEightDpFarEdgeGap() {
        val page = backPageTransform(1440f, 3168f, 4f, 1f, 1f, 0f, 0f)
        assertEquals(0.9f, page.scale, 0.001f)
        val rightMargin = 1440f * (1f - page.scale) / 2f - page.x
        assertEquals(32f, rightMargin, 0.001f)
    }

    @Test fun bothEdgesStayInsideWindowAcrossAspectRatiosAndProgress() {
        for ((width, height) in listOf(360f to 800f, 800f to 360f, 1440f to 3168f)) {
            for (direction in listOf(-1f, 1f)) {
                for (step in 0..100) {
                    val page = backPageTransform(width, height, 1f, step / 100f, direction, 5000f, 0f)
                    assertTrue(width * (1f - page.scale) / 2f - abs(page.x) >= -0.001f)
                    assertTrue(height * (1f - page.scale) / 2f - abs(page.y) >= -0.001f)
                }
            }
        }
    }

    @Test fun verticalFollowMirrorsDirectionAndIsBounded() {
        val positive = backPageTransform(1440f, 3168f, 4f, 0.7f, 1f, 800f, 0f)
        val negative = backPageTransform(1440f, 3168f, 4f, 0.7f, 1f, -800f, 0f)
        assertTrue(positive.y > 0f)
        assertEquals(positive.y, -negative.y, 0.001f)
        assertTrue(positive.y <= 24f * 4f)
    }

    @Test fun commitContinuesPoseWithoutJumpingOrFlyingAcrossEntireWindow() {
        val preview = backPageTransform(1440f, 3168f, 4f, 0.45f, 1f, 500f, 0f)
        val firstCommitFrame = backPageTransform(1440f, 3168f, 4f, 0.45f, 1f, 500f, 0.0001f)
        assertEquals(preview.scale, firstCommitFrame.scale, 0.001f)
        assertEquals(preview.x, firstCommitFrame.x, 0.1f)
        for (step in 0..100) {
            val page = backPageTransform(1440f, 3168f, 4f, 0.45f, 1f, 500f, step / 100f)
            assertTrue(abs(page.x) < 1440f * 0.1f)
        }
    }

    @Test fun zeroProgressAndInvalidWindowBoundsHaveNoTransform() {
        val rest = backPageTransform(1440f, 3168f, 4f, 0f, -1f, 500f, 0f)
        assertEquals(1f, rest.scale, 0f)
        assertEquals(0f, rest.x, 0f)
        assertEquals(0f, rest.y, 0f)
        assertEquals(rest, backPageTransform(0f, 0f, 4f, 0.5f, -1f, 500f, 0f))
    }
}
