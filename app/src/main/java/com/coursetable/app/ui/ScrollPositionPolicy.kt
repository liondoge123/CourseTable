package com.coursetable.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/** Position invalidation is independent of editor/draft state. */
internal class TimetableScrollResets {
    private val versions = MutableStateFlow<Map<Long, String>>(emptyMap())
    val revisions = versions.asStateFlow()

    fun onOverwriteCommitted(timetableId: Long) {
        // Never reuse a saved context after a process restart and another overwrite.
        val revision = UUID.randomUUID().toString()
        versions.update { current -> current + (timetableId to revision) }
    }
}

internal val LocalTimetableScrollKey = compositionLocalOf { "" }
internal val LocalPageScrollKey = compositionLocalOf { 0L }

/** Returning to an earlier table is a new visit, including while its page is unmounted. */
@Composable
internal fun rememberTimetableScrollKey(timetableId: Long, revision: String): String {
    var previousId by rememberSaveable { mutableLongStateOf(timetableId) }
    var visit by rememberSaveable { mutableLongStateOf(0L) }
    if (timetableId > 0 && timetableId != previousId) {
        previousId = timetableId
        visit++
    }
    return "$previousId:$visit:$revision"
}

/** Keep the departing page's key through its exit animation; advance only on entry. */
@Composable
internal fun rememberPageScrollVisit(destination: String?): Long {
    var previous by rememberSaveable { mutableStateOf(destination) }
    var visit by rememberSaveable { mutableLongStateOf(0L) }
    if (destination != previous) {
        if (destination != null) visit++
        previous = destination
    }
    return visit
}

@Composable
internal fun rememberContextScrollState(context: String): ScrollState = rememberSaveable(
    context,
    saver = Saver<ScrollState, List<Any>>(
        save = { listOf(context, it.value) },
        // rememberSaveable inputs alone do not validate a restored holder entry.
        restore = { if (it[0] == context) ScrollState(it[1] as Int) else null }
    )
) { ScrollState(0) }

@Composable
internal fun rememberContextLazyListState(context: String): LazyListState = rememberSaveable(
    context,
    saver = Saver<LazyListState, List<Any>>(
        save = { listOf(context, it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
        restore = { if (it[0] == context) LazyListState(it[1] as Int, it[2] as Int) else null }
    )
) { LazyListState() }

@Composable
internal fun rememberPageScrollState(): ScrollState =
    rememberContextScrollState(LocalPageScrollKey.current.toString())

@Composable
internal fun rememberPageLazyListState(): LazyListState =
    rememberContextLazyListState(LocalPageScrollKey.current.toString())
