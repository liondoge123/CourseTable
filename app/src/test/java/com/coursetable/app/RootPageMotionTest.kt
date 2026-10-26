package com.coursetable.app

import androidx.compose.animation.core.getVelocityFromNanos
import com.coursetable.app.ui.RootPageMotion
import com.coursetable.app.ui.RootPageTransition
import com.coursetable.app.ui.RootDestination
import org.junit.Assert.*
import org.junit.Test

class RootPageMotionTest {
    @Test fun successiveTargetsAndReversalsKeepTheInheritedSpeedBounded() {
        for (cadence in listOf(32_000_000L, 80_000_000L, 160_000_000L)) {
            val strip = RootPageTransition(RootDestination.TIMETABLE)
            var velocity = 0f
            for (index in listOf(1, 2, 3, 2, 0, 3, 0)) {
                val destination = RootDestination.entries[index]
                val target = strip.target(destination)
                val motion = RootPageMotion.animation(strip.position, target, velocity,
                    strip.pages.minOf { it.anchor }, strip.pages.maxOf { it.anchor })
                var time = 0L
                while (time <= minOf(cadence, motion.durationNanos)) {
                    assertTrue("cadence=$cadence target=$destination time=$time",
                        kotlin.math.abs(motion.getVelocityFromNanos(time)) < 10f)
                    time += 1_000_000L
                }
                if (motion.durationNanos <= cadence) {
                    strip.snapTo(destination)
                    velocity = 0f
                } else {
                    strip.moveTo(motion.getValueFromNanos(cadence))
                    velocity = motion.getVelocityFromNanos(cadence)
                }
            }
        }
    }

    @Test fun longerRetargetsDoNotMultiplyTheScreenSpaceSpeed() {
        for (distance in listOf(1f, 2f, 3f)) {
            val motion = RootPageMotion.animation(0f, distance, 0f, 0f, distance)
            var peak = 0f
            var time = 0L
            while (time <= motion.durationNanos) {
                peak = maxOf(peak, motion.getVelocityFromNanos(time))
                time += 1_000_000L
            }
            assertTrue("distance=$distance peak=$peak", peak in 7f..7.5f)
        }
    }

    @Test fun reversingARepeatedClickKeepsVelocityAndBrakesInsideThePageStrip() {
        val outbound = RootPageMotion.animation(0f, 1f, 0f)
        val position = outbound.getValueFromNanos(80_000_000L)
        val velocity = outbound.getVelocityFromNanos(80_000_000L)
        val returning = RootPageMotion.animation(position, 0f, velocity)
        assertEquals(position, returning.getValueFromNanos(0), 0f)
        assertEquals(velocity, returning.getVelocityFromNanos(0), 0.001f)
        assertTrue(returning.getValueFromNanos(8_000_000L) > position)
        var time = 0L
        while (time <= returning.durationNanos) {
            assertTrue(returning.getValueFromNanos(time) in 0f..1f)
            time += 1_000_000L
        }
    }

    @Test fun pageTailFinishesOnceTheResidualMovementIsVisuallySmall() {
        val motion = RootPageMotion.animation(0f, 1f, 0f)
        assertTrue(motion.durationNanos < 450_000_000L)
        assertTrue(motion.getValueFromNanos(260_000_000L) > 0.96f)
    }

    @Test fun aPageDoesNotRushToTheEndInTheOld180Milliseconds() {
        val motion = RootPageMotion.animation(0f, 1f, 0f)
        assertTrue(motion.getValueFromNanos(180_000_000L) in 0.8f..0.92f)
        assertTrue(motion.getValueFromNanos(350_000_000L) > 0.98f)
        assertTrue(motion.durationNanos in 350_000_000L..650_000_000L)
    }

    @Test fun settlingStartsWithTheFingerVelocityInsteadOfZero() {
        val motion = RootPageMotion.animation(0.25f, 1f, 2f)
        assertEquals(2f, motion.getVelocityFromNanos(0), 0.0001f)
        val resting = RootPageMotion.animation(0.25f, 1f, 0f)
        assertTrue(motion.getValueFromNanos(16_000_000L) > resting.getValueFromNanos(16_000_000L))
    }

    @Test fun slowAndFastReleasesHaveDifferentTrajectories() {
        val slow = RootPageMotion.animation(0.35f, 1f, 0.2f)
        val fast = RootPageMotion.animation(0.35f, 1f, 3f)
        assertTrue(fast.getValueFromNanos(80_000_000L) > slow.getValueFromNanos(80_000_000L))
        assertTrue(fast.durationNanos < slow.durationNanos)
    }

    @Test fun ordinaryReleaseCanBrakeBeforeReturningToTheOriginalPage() {
        val motion = RootPageMotion.animation(0.25f, 0f, 0.5f)
        assertEquals(0.5f, motion.getVelocityFromNanos(0), 0.0001f)
        assertTrue(motion.getValueFromNanos(1_000_000L) > 0.25f)
        assertEquals(0f, motion.getValueFromNanos(motion.durationNanos), 0.001f)
    }

    @Test fun fastReleasesCannotOvershootEitherPageBoundary() {
        for (start in listOf(0.01f, 0.2f, 0.49f, 0.51f, 0.8f, 0.99f)) {
            for (target in listOf(0f, 1f)) for (velocity in listOf(-20f, -3f, 0f, 3f, 20f)) {
                val motion = RootPageMotion.animation(start, target, velocity)
                var time = 0L
                while (time <= motion.durationNanos) {
                    val value = motion.getValueFromNanos(time)
                    assertTrue("start=$start target=$target velocity=$velocity time=$time value=$value",
                        value in -0.0001f..1.0001f)
                    time += 5_000_000L
                }
            }
        }
    }

    @Test fun anAlignedPageRemainsStillEvenWithResidualVelocity() {
        for (position in listOf(0f, 1f)) {
            val motion = RootPageMotion.animation(position, position, 5f)
            assertEquals(position, motion.getValueFromNanos(0), 0f)
            assertEquals(0f, motion.getVelocityFromNanos(0), 0f)
        }
    }
}
