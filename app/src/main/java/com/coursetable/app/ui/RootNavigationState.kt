package com.coursetable.app.ui

import androidx.compose.animation.core.getVelocityFromNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import com.coursetable.app.ui.liquid.NavigationMotionDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.coroutines.coroutineContext

/** A single screen-space position drives full-width clicks and finger swipes. */
@Stable
internal class RootNavigationState(
    private val savedDestination: MutableState<RootDestination>,
    private val scope: CoroutineScope
) {
    val transition = RootPageTransition(savedDestination.value)
    private var clickTarget by mutableStateOf<RootDestination?>(null)
    private var motionJob: Job? = null
    private var generation = 0
    private var dragSource = savedDestination.value
    private var dragOffset by mutableStateOf(0f)
    private var pageVelocity = 0f
    var isDragging by mutableStateOf(false)
        private set
    var isSwiping by mutableStateOf(false)
        private set

    val isNavigating: Boolean get() = clickTarget != null
    val isTransitioning: Boolean
        get() = isNavigating || isSwiping || transition.currentState != transition.targetState
    val settledDestination: RootDestination get() = transition.currentState
    // Do not recompose MainScreen/the Dock on every finger delta. Only a change
    // of the nearest destination needs to update the selected tab.
    val destination: RootDestination by derivedStateOf {
        clickTarget ?: if (isDragging) {
            RootDestination.entries[(dragSource.ordinal + dragOffset).roundToInt()]
        } else {
            // A returning swipe retains the neighboring animation target. The
            // Dock must use the committed landing page instead.
            savedDestination.value
        }
    }

    fun navigateTo(destination: RootDestination) {
        if (clickTarget == destination || (!isTransitioning && settledDestination == destination)) return
        NavigationMotionDiagnostics.event("page.request")
        val request = ++generation
        val initialVelocity = if (isDragging) 0f else pageVelocity
        clickTarget = destination
        savedDestination.value = destination
        isDragging = false
        motionJob?.cancel()
        // Keep every already visible page in the same coordinates. Reversals
        // reuse its existing tile; a new destination enters beside the strip.
        // No independent outgoing/incoming animations can add their speeds.
        transition.target(destination)
        isSwiping = false
        motionJob = scope.launch {
            try {
                settlePage(destination, initialVelocity)
            } finally {
                if (request == generation) {
                    clickTarget = null
                    isSwiping = false
                }
            }
        }
    }

    fun beginDrag() {
        if (isNavigating) return
        ++generation
        motionJob?.cancel()
        dragSource = transition.currentState
        dragOffset = if (isSwiping) transition.position else 0f
        isDragging = true
        isSwiping = true
        pageVelocity = 0f
    }

    /** Positive values move towards the next destination, independent of layout direction. */
    fun dragBy(pages: Float) {
        if (!isDragging) return
        dragOffset = (dragOffset + pages).coerceIn(
            if (dragSource.ordinal > 0) -1f else 0f,
            if (dragSource.ordinal < RootDestination.entries.lastIndex) 1f else 0f
        )
        transition.drag(dragSource, dragOffset)
    }

    fun endDrag(velocity: Float, velocityThreshold: Float) {
        if (!isDragging) return
        val request = ++generation
        val offset = dragOffset
        // A reverse fling returns to the source rather than inventing a third page.
        val advance = if (abs(velocity) >= velocityThreshold) velocity.sign == offset.sign
            else abs(offset) >= 0.5f
        val target = RootDestination.entries[(dragSource.ordinal +
            if (advance) offset.sign.toInt() else 0).coerceIn(RootDestination.entries.indices)]
        savedDestination.value = target
        isDragging = false
        motionJob = scope.launch {
            try {
                settlePage(target, velocity)
            } finally {
                if (request == generation) isSwiping = false
            }
        }
    }

    private suspend fun settlePage(destination: RootDestination, initialVelocity: Float) {
        val start = transition.position
        val end = transition.anchorOf(destination)
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (abs(end - start) > 0.0001f && scale > 0f) {
            // The simulation runs in scaled animation time; its initial velocity
            // must still match the finger in real screen widths per second.
            val minimum = transition.pages.minOf { it.anchor }
            val maximum = transition.pages.maxOf { it.anchor }
            val animation = RootPageMotion.animation(start, end, initialVelocity * scale, minimum, maximum)
            var started = Long.MIN_VALUE
            do {
                // Publish inside the frame callback, before placement. Resuming
                // the coroutine first can publish after this frame's layout and
                // leave the visible position a frame behind the spring clock.
                val finished = withFrameNanos { frame ->
                    if (started == Long.MIN_VALUE) started = frame
                    val playTime = ((frame - started) / scale.toDouble()).toLong()
                    pageVelocity = animation.getVelocityFromNanos(playTime) / scale
                    // Preserve the exact handoff position, including Float rounding
                    // at t=0. It must never introduce even a tiny backwards step.
                    transition.moveTo(if (playTime == 0L) start else
                        animation.getValueFromNanos(playTime).coerceIn(minimum, maximum))
                    if (NavigationMotionDiagnostics.listener != null) {
                        NavigationMotionDiagnostics.event("page.motion", listOf(transition.position, pageVelocity))
                    }
                    animation.isFinishedFromNanos(playTime)
                }
            } while (!finished)
        }
        // Keep the same source/target coordinates throughout snap-back. A new
        // finger can therefore resume at the exact visible offset in either direction.
        transition.snapTo(destination)
        pageVelocity = 0f
    }

    suspend fun scrollTo(destination: RootDestination) {
        ++generation
        motionJob?.cancel()
        clickTarget = null
        isDragging = false
        isSwiping = false
        pageVelocity = 0f
        savedDestination.value = destination
        transition.snapTo(destination)
    }
}

@Composable
internal fun rememberRootNavigationState(
    initialDestination: RootDestination = RootDestination.TIMETABLE
): RootNavigationState {
    val scope = rememberCoroutineScope()
    // Save a destination, never a transient numeric page index or a half-page offset.
    val destination = rememberSaveable { mutableStateOf(initialDestination) }
    return remember(scope) { RootNavigationState(destination, scope) }
}
