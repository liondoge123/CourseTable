package com.coursetable.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/** Smooth, non-bouncing page settling; velocity is measured in screen widths/second. */
internal object RootPageMotion {
    private const val Stiffness = 400f
    val spec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Stiffness,
        visibilityThreshold = 0.001f
    )

    fun animation(start: Float, target: Float, velocity: Float) = TargetBasedAnimation(
        animationSpec = spec,
        typeConverter = Float.VectorConverter,
        initialValue = start,
        targetValue = target,
        initialVelocity = boundedVelocity(start, target, velocity)
    )

    private fun boundedVelocity(start: Float, target: Float, velocity: Float): Float {
        val remaining = target - start
        if (remaining == 0f || !velocity.isFinite()) return 0f
        // A critically damped spring stays inside its endpoint when v <= omega*d.
        // Also bound motion away from the target by the room at the opposite edge.
        // This avoids clipping a fast release into a pause at the viewport boundary.
        val room = if (velocity * remaining >= 0f) abs(remaining)
            else if (velocity > 0f) 1f - start else start
        return velocity.sign * abs(velocity).coerceAtMost(sqrt(Stiffness) * room)
    }
}
