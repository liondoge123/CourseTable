package com.coursetable.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.sign

internal data class RootPagePlacement(val destination: RootDestination, val anchor: Float)

/** One strip of requested pages, translated by one shared screen-space position. */
@Stable
internal class RootPageTransition(initial: RootDestination) {
    var currentState by mutableStateOf(initial)
        private set
    var targetState by mutableStateOf(initial)
        private set
    var pages by mutableStateOf(listOf(RootPagePlacement(initial, 0f)))
        private set
    var position by mutableFloatStateOf(0f)
        private set

    val fraction: Float
        get() {
            val target = anchorOf(targetState)
            return if (target == 0f) 0f else (position / target).coerceIn(0f, 1f)
        }

    fun anchorOf(destination: RootDestination): Float =
        pages.first { it.destination == destination }.anchor

    fun target(destination: RootDestination): Float {
        if (pages.none { it.destination == destination }) {
            val direction = (destination.ordinal - targetState.ordinal).sign
            val edge = if (direction > 0) pages.maxOf { it.anchor } else pages.minOf { it.anchor }
            pages = pages + RootPagePlacement(destination, edge + direction)
        }
        targetState = destination
        return anchorOf(destination)
    }

    fun drag(source: RootDestination, offset: Float) {
        val target = RootDestination.entries[source.ordinal + offset.sign.toInt()]
        val desired = if (source == target) listOf(RootPagePlacement(source, 0f)) else
            listOf(RootPagePlacement(source, 0f), RootPagePlacement(target, offset.sign))
        if (pages != desired) pages = desired
        targetState = target
        position = offset
    }

    fun moveTo(value: Float) { position = value }

    fun snapTo(destination: RootDestination) {
        currentState = destination
        targetState = destination
        pages = listOf(RootPagePlacement(destination, 0f))
        position = 0f
    }
}
