package com.coursetable.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme

@Composable
internal fun CourseWeeksDialog(
    totalWeeks: Int,
    initialWeeks: List<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(initialWeeks.filter { it in 1..totalWeeks }) }
    val allWeeks = remember(totalWeeks) { (1..totalWeeks).toList() }
    val gridState = rememberLazyGridState()
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var dragSelect by remember { mutableStateOf<Boolean?>(null) }
    val visited = remember { mutableSetOf<Int>() }
    val edge = with(LocalDensity.current) { 32.dp.toPx() }

    fun weekAt(position: Offset): Int? = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
        position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
            position.y >= it.offset.y && position.y < it.offset.y + it.size.height
    }?.key as? Int
    fun paint(position: Offset) {
        val select = dragSelect ?: return
        val week = weekAt(position) ?: return
        if (visited.add(week)) selected = if (select) (selected + week).distinct().sorted() else selected - week
    }
    fun finishDrag() { dragPosition = null; dragSelect = null; visited.clear() }
    LaunchedEffect(dragPosition != null) {
        while (dragPosition != null) {
            withFrameNanos { }
            val position = dragPosition ?: break
            val viewport = gridState.layoutInfo.viewportSize
            val step = when {
                position.x !in 0f..viewport.width.toFloat() -> 0f
                position.y < edge -> -edge * 0.15f
                position.y > viewport.height - edge -> edge * 0.15f
                else -> 0f
            }
            if (step != 0f) {
                gridState.scrollBy(step)
                paint(position.copy(y = position.y.coerceIn(0f, (viewport.height - 1).toFloat())))
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择上课周次", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = LiquidTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.fillMaxWidth().testTag("course-weeks-dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全选" to allWeeks, "单周" to allWeeks.filter { it % 2 == 1 },
                        "双周" to allWeeks.filter { it % 2 == 0 }).forEach { (label, weeks) ->
                        WeekSelectionButton(label, selected.toSet() == weeks.toSet() && weeks.isNotEmpty(),
                            Modifier.weight(1f).heightIn(min = 48.dp).testTag("weeks-preset-$label"),
                            enabled = weeks.isNotEmpty()) { selected = weeks }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 360.dp)) {
                    val columns = ((maxWidth.value + 4f) / 52f).toInt().coerceIn(1, 6)
                    val rows = (totalWeeks + columns - 1) / columns
                    val gridHeight = (rows * 48 + (rows - 1) * 4).dp.coerceAtMost(360.dp)
                    LazyVerticalGrid(columns = GridCells.Fixed(columns), state = gridState,
                        // Once long-press painting owns the gesture, vertical movement must not
                        // be consumed by the grid's normal scroll detector.
                        userScrollEnabled = dragSelect == null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = gridHeight).testTag("course-weeks-grid")
                            .pointerInput(gridState) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val hold = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                    val week = weekAt(hold.position) ?: return@awaitEachGesture
                                    visited.clear()
                                    dragSelect = week !in selected
                                    dragPosition = hold.position
                                    paint(hold.position)
                                    var previous = hold.position
                                    try {
                                        while (true) {
                                            // Claim movement and release before child toggleables see them.
                                            // A stationary long press must not also become a click on release.
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            event.changes.forEach { it.consume() }
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val amount = change.position - previous
                                            val steps = (amount.getDistance() / 6f).toInt().coerceAtLeast(1)
                                            repeat(steps) { step -> paint(previous + amount * ((step + 1f) / steps)) }
                                            dragPosition = change.position
                                            previous = change.position
                                        }
                                    } finally { finishDrag() }
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(allWeeks, key = { it }) { week ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                WeekSelectionButton(week.toString(), week in selected,
                                    Modifier.size(48.dp).testTag("course-week-$week"), compact = true) {
                                    selected = if (week in selected) selected - week else (selected + week).sorted()
                                }
                            }
                        }
                    }
                }
                Text(if (selected.isEmpty()) "请至少选择一周" else "已选 ${selected.size} 周",
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = LiquidTheme.typography.bodySmall,
                    color = if (selected.isEmpty()) LiquidTheme.colorScheme.error else LiquidTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.sorted()); onDismiss() }, enabled = selected.isNotEmpty(),
                modifier = Modifier.testTag("course-weeks-confirm")) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun WeekSelectionButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LiquidTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh.copy(alpha = 0.38f))
    val foreground by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface)
    Box(modifier.toggleable(selected, interactionSource = remember { MutableInteractionSource() }, indication = null,
        enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center) {
        Box((if (compact) Modifier.size(40.dp) else Modifier.fillMaxWidth().height(40.dp))
            .clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Text(label, style = LiquidTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = foreground.copy(alpha = if (enabled) 1f else 0.38f), textAlign = TextAlign.Center)
        }
    }
}
