package com.coursetable.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.max

/** Smooth, non-bouncing page settling; velocity is measured in screen widths/second. */
internal object RootPageMotion {
    private const val Stiffness = 400f
    private fun spec(distance: Float) = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        // Retargeting can leave several requested pages in flight. A longer
        // distance must not multiply the peak screen-space speed.
        stiffness = Stiffness / (distance * distance),
        visibilityThreshold = 0.004f
    )

    fun animation(
        start: Float,
        target: Float,
        velocity: Float,
        minimum: Float = 0f,
        maximum: Float = 1f
    ) = TargetBasedAnimation(
        animationSpec = spec(max(1f, abs(target - start))),
        typeConverter = Float.VectorConverter,
        initialValue = start,
        targetValue = target,
        initialVelocity = boundedVelocity(start, target, velocity, minimum, maximum)
    )

    private fun boundedVelocity(start: Float, target: Float, velocity: Float, minimum: Float, maximum: Float): Float {
        val remaining = target - start
        if (remaining == 0f || !velocity.isFinite()) return 0f
        // A critically damped spring stays inside its endpoint when v <= omega*d.
        // Also bound motion away from the target by the room at the opposite edge.
        // This avoids clipping a fast release into a pause at the viewport boundary.
        val room = if (velocity * remaining >= 0f) abs(remaining)
            else if (velocity > 0f) maximum - start else start - minimum
        val omega = sqrt(Stiffness) / max(1f, abs(remaining))
        return velocity.sign * abs(velocity).coerceAtMost(omega * room.coerceAtLeast(0f))
    }
}
