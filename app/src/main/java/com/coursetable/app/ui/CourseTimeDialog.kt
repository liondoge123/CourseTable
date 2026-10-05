package com.coursetable.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coursetable.app.ui.liquid.AlertDialog
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.liquid.TextButton
import com.coursetable.app.ui.theme.LiquidTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
internal fun CourseTimeDialog(
    initialDay: Int,
    initialStart: Int,
    initialEnd: Int,
    periodCount: Int,
    onConfirm: (day: Int, start: Int, end: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var day by rememberSaveable { mutableIntStateOf(initialDay.coerceIn(1, 7)) }
    var start by rememberSaveable { mutableIntStateOf(initialStart.coerceIn(1, periodCount)) }
    var end by rememberSaveable { mutableIntStateOf(initialEnd.coerceIn(start, periodCount)) }
    var dayScrolling by remember { mutableStateOf(false) }
    var startScrolling by remember { mutableStateOf(false) }
    var endScrolling by remember { mutableStateOf(false) }
    val periods = remember(periodCount) { (1..periodCount).toList() }
    val weekdays = remember { (1..7).toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课程时间", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = LiquidTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().testTag("course-time-dialog"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("星期", Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = LiquidTheme.typography.labelMedium)
                    Text("开始节次", Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = LiquidTheme.typography.labelMedium)
                    Spacer(Modifier.width(12.dp))
                    Text("结束节次", Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = LiquidTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CourseTimeWheel(weekdays, day, { "周${listOf("一", "二", "三", "四", "五", "六", "日")[it - 1]}" },
                        Modifier.weight(1f).testTag("course-time-day"), { dayScrolling = it }) { day = it }
                    CourseTimeWheel(periods, start, { "第 $it 节" },
                        Modifier.weight(1f).testTag("course-time-start"), { startScrolling = it }) {
                        start = it
                        end = end.coerceAtLeast(it)
                    }
                    Text("–", Modifier.width(12.dp), textAlign = TextAlign.Center,
                        color = LiquidTheme.colorScheme.onSurface, style = LiquidTheme.typography.bodyLarge)
                    CourseTimeWheel(periods, end, { "第 $it 节" },
                        Modifier.weight(1f).testTag("course-time-end"), { endScrolling = it }) {
                        end = it
                        start = start.coerceAtMost(it)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day, start, end); onDismiss() },
                enabled = !dayScrolling && !startScrolling && !endScrolling,
                modifier = Modifier.testTag("course-time-confirm")) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
internal fun CourseTimeWheel(
    values: List<Int>,
    selected: Int,
    formatter: (Int) -> String,
    modifier: Modifier,
    onScrolling: (Boolean) -> Unit,
    onSelected: (Int) -> Unit
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentOnScrolling by rememberUpdatedState(onScrolling)
    val rowHeight = 52.dp
    // Visual emphasis follows the viewport immediately; committing a value still waits for snapping.
    val centeredValue by remember(state, values, selected) {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
                ?.index?.let { values.getOrNull(it) } ?: selected
        }
    }
    // External range corrections move the other wheel to the same committed value.
    LaunchedEffect(selected) {
        val index = values.indexOf(selected)
        if (index >= 0 && state.firstVisibleItemIndex != index) state.scrollToItem(index)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { currentOnScrolling(it) }
    }
    LaunchedEffect(state) {
        // Initial positioning is supplied by the caller; only settled scrolls commit a draft value.
        snapshotFlow { state.isScrollInProgress }.drop(1).collect { scrolling ->
            if (!scrolling) {
                val layout = state.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                val item = layout.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
                item?.index?.let { values.getOrNull(it) }?.let(currentOnSelected)
            }
        }
    }
    LazyColumn(modifier.height(rowHeight * 3), state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = rowHeight),
        horizontalAlignment = Alignment.CenterHorizontally) {
        itemsIndexed(values, key = { _, value -> value }) { index, value ->
            Box(Modifier.fillMaxWidth().height(rowHeight).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {
                scope.launch {
                    state.animateScrollToItem(index)
                    currentOnSelected(value)
                }
            }.semantics { this.selected = value == centeredValue }
                .testTag("${formatter(value)}-time-option"), contentAlignment = Alignment.Center) {
                Text(formatter(value), maxLines = 1, textAlign = TextAlign.Center,
                    color = if (value == centeredValue) LiquidTheme.colorScheme.onSurface
                        else LiquidTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = LiquidTheme.typography.bodyLarge,
                    fontSize = if (value == centeredValue) 18.sp else 16.sp,
                    fontWeight = if (value == centeredValue) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
