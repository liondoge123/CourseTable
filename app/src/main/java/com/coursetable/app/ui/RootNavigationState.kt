package com.coursetable.app.ui

import androidx.compose.animation.core.SeekableTransitionState
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

/** A single full-width transition for clicks and swipes; no temporary page ordering. */
@Stable
internal class RootNavigationState(
    private val savedDestination: MutableState<RootDestination>,
    private val scope: CoroutineScope
) {
    val transition = SeekableTransitionState(savedDestination.value)
    private var clickTarget by mutableStateOf<RootDestination?>(null)
    private var motionJob: Job? = null
    private var seekJob: Job? = null
    private var generation = 0
    private var dragSource = savedDestination.value
    private var dragOffset by mutableStateOf(0f)
    private var fractionVelocity = 0f
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
        val continuingSwipe = isSwiping
        val initialVelocity = if (isSwiping && !isDragging) fractionVelocity else 0f
        clickTarget = destination
        savedDestination.value = destination
        isDragging = false
        motionJob?.cancel()
        motionJob = scope.launch {
            try {
                seekJob?.join()
                isSwiping = false
                // Both inputs use the same coordinates, so a click can continue or
                // retarget a partial swipe directly, without an intermediate stop.
                if (continuingSwipe &&
                    (destination == transition.currentState || destination == transition.targetState)) {
                    settleSwipe(destination, initialVelocity)
                } else {
                    transition.animateTo(destination, RootPageMotion.spec)
                }
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
        dragOffset = if (isSwiping) {
            (transition.targetState.ordinal - dragSource.ordinal) * transition.fraction
        } else 0f
        isDragging = true
        isSwiping = true
        fractionVelocity = 0f
    }

    /** Positive values move towards the next destination, independent of layout direction. */
    fun dragBy(pages: Float) {
        if (!isDragging) return
        dragOffset = (dragOffset + pages).coerceIn(
            if (dragSource.ordinal > 0) -1f else 0f,
            if (dragSource.ordinal < RootDestination.entries.lastIndex) 1f else 0f
        )
        val offset = dragOffset
        val source = dragSource
        val target = RootDestination.entries[source.ordinal + offset.sign.toInt()]
        seekJob?.cancel()
        seekJob = scope.launch {
            // A reversal through the source starts the other adjacent pair at zero.
            // Keeping the original source also allows dragging back out of timetable.
            if (transition.currentState != source || transition.targetState != target) {
                transition.snapTo(source)
            }
            transition.seekTo(abs(offset), target)
        }
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
        val direction = offset.sign
        motionJob = scope.launch {
            try {
                seekJob?.join()
                settleSwipe(target, velocity * direction)
            } finally {
                if (request == generation) isSwiping = false
            }
        }
    }

    private suspend fun settleSwipe(destination: RootDestination, initialVelocity: Float) {
        val source = transition.currentState
        val target = transition.targetState
        val start = transition.fraction
        val end = if (destination == target) 1f else 0f
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (source != target && abs(end - start) > 0.0001f && scale > 0f) {
            // The simulation runs in scaled animation time; its initial velocity
            // must still match the finger in real screen widths per second.
            val animation = RootPageMotion.animation(start, end, initialVelocity * scale)
            val started = withFrameNanos { it }
            do {
                val playTime = ((withFrameNanos { it } - started) / scale.toDouble()).toLong()
                fractionVelocity = animation.getVelocityFromNanos(playTime) / scale
                transition.seekTo(animation.getValueFromNanos(playTime).coerceIn(0f, 1f), target)
            } while (!animation.isFinishedFromNanos(playTime))
        }
        // Keep the same source/target coordinates throughout snap-back. A new
        // finger can therefore resume at the exact visible offset in either direction.
        transition.snapTo(destination)
        fractionVelocity = 0f
    }

    suspend fun scrollTo(destination: RootDestination) {
        ++generation
        motionJob?.cancel()
        seekJob?.cancel()
        clickTarget = null
        isDragging = false
        isSwiping = false
        fractionVelocity = 0f
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
