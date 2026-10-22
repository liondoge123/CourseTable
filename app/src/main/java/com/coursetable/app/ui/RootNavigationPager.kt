package com.coursetable.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.coursetable.app.ui.liquid.LocalBackLayerActive
import com.coursetable.app.ui.liquid.LocalGlassOverlayController

/** One scroll state drives page movement and the dock's selected destination. */
@Composable
internal fun RootNavigationPager(
    state: PagerState,
    userScrollEnabled: Boolean,
    active: Boolean = true,
    content: @Composable (RootDestination) -> Unit
) {
    val parentActive = LocalBackLayerActive.current && active
    val overlayOpen = LocalGlassOverlayController.current?.entries?.isNotEmpty() == true
    HorizontalPager(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("root-pager"),
        // The timetable owns horizontal swipes for week navigation; ordinary course
        // rows let the root pager handle horizontal drags.
        userScrollEnabled = userScrollEnabled && parentActive && !overlayOpen &&
            state.settledPage != RootDestination.TIMETABLE.ordinal,
        key = { RootDestination.entries[it] }
    ) { page ->
        // Adjacent pages may be measured during a drag. Only the selected page can
        // register back handlers or present a portal into the shared overlay host.
        val pageActive = parentActive && !state.isScrollInProgress && page == state.settledPage
        CompositionLocalProvider(LocalBackLayerActive provides pageActive) {
            Box(Modifier.fillMaxSize().then(if (pageActive) Modifier else Modifier.clearAndSetSemantics {})) {
                content(RootDestination.entries[page])
            }
        }
    }
}
