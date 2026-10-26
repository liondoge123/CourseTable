package com.coursetable.app.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.coursetable.app.ui.liquid.LocalBackLayerActive
import com.coursetable.app.ui.liquid.LocalGlassOverlayController
import com.coursetable.app.ui.liquid.NavigationMotionDiagnostics

/** Requested pages share one full-width translation, for clicks and finger swipes. */
@Composable
internal fun RootNavigationHost(
    navigation: RootNavigationState,
    userScrollEnabled: Boolean,
    active: Boolean = true,
    content: @Composable (RootDestination) -> Unit
) {
    val parentActive = LocalBackLayerActive.current && active
    val overlayOpen = LocalGlassOverlayController.current?.entries?.isNotEmpty() == true
    val savedPages = rememberSaveableStateHolder()
    var width by remember { mutableIntStateOf(1) }
    val swipeSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    val flingThreshold = with(LocalDensity.current) { 400.dp.toPx() } / width
    val motionObserver = NavigationMotionDiagnostics.listener
    LaunchedEffect(motionObserver, navigation) {
        if (motionObserver != null) snapshotFlow {
            listOf(navigation.transition.fraction, if (navigation.isTransitioning) 1f else 0f)
        }.collect { NavigationMotionDiagnostics.event("page", it) }
    }
    Box(Modifier.fillMaxSize().testTag("root-pager").clipToBounds()
        .onSizeChanged { width = it.width.coerceAtLeast(1) }
        .draggable(
            state = rememberDraggableState { navigation.dragBy(swipeSign * it / width) },
            orientation = Orientation.Horizontal,
            enabled = userScrollEnabled && parentActive && !overlayOpen && !navigation.isNavigating &&
                (navigation.isSwiping || navigation.settledDestination != RootDestination.TIMETABLE),
            startDragImmediately = navigation.isSwiping && !navigation.isDragging,
            onDragStarted = { navigation.beginDrag() },
            onDragStopped = { navigation.endDrag(swipeSign * it / width, flingThreshold) }
        )) {
        for (page in navigation.transition.pages) key(page.destination) {
            val destination = page.destination
            val pageActive = parentActive && !navigation.isTransitioning &&
                destination == navigation.settledDestination
            savedPages.SaveableStateProvider(destination) {
                CompositionLocalProvider(LocalBackLayerActive provides pageActive) {
                    Box(Modifier.fillMaxSize().absoluteOffset {
                        IntOffset(((page.anchor - navigation.transition.position) * width * -swipeSign).roundToInt(), 0)
                    }.then(
                        if (pageActive) Modifier else Modifier.clearAndSetSemantics {}
                    )) { content(destination) }
                }
            }
        }
    }
}
