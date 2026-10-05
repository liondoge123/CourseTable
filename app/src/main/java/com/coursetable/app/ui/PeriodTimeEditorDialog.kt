package com.coursetable.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.PeriodUtils
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme
import java.time.LocalTime

@Composable
internal fun PeriodTimeEditorDialog(
    periods: List<PeriodTime>,
    index: Int,
    onConfirm: (List<PeriodTime>) -> Unit,
    onDismiss: () -> Unit
) {
    val original = periods[index]
    var start by rememberSaveable { mutableIntStateOf(original.start.hour * 60 + original.start.minute) }
    var end by rememberSaveable { mutableIntStateOf(original.end.hour * 60 + original.end.minute) }
    var scrolling by remember { mutableStateOf(emptySet<String>()) }
    val edited = PeriodTime(LocalTime.of(start / 60, start % 60), LocalTime.of(end / 60, end % 60))
    val candidate = runCatching { PeriodUtils.replacePeriod(periods, index, edited) }
    val error = candidate.exceptionOrNull()?.message ?: candidate.getOrNull()?.let { PeriodUtils.validatePeriods(it) }

    fun moveStart(value: Int) {
        // Preserve this period's actual duration, including a manually customized end.
        end = Math.floorMod(end + value - start, 1440)
        start = value
    }
    fun track(wheel: String, moving: Boolean) { scrolling = if (moving) scrolling + wheel else scrolling - wheel }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("第 ${index + 1} 节课", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = LiquidTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("period-time-editor"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("上课时间", Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = LiquidTheme.typography.labelMedium)
                    Spacer(Modifier.width(12.dp))
                    Text("下课时间", Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = LiquidTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        PeriodClockWheel(0..23, start / 60, "period-start-hour", Modifier.weight(1f),
                            { track("start-hour", it) }) { moveStart(it * 60 + start % 60) }
                        Text(":", color = LiquidTheme.colorScheme.onSurface)
                        PeriodClockWheel(0..59, start % 60, "period-start-minute", Modifier.weight(1f),
                            { track("start-minute", it) }) { moveStart(start / 60 * 60 + it) }
                    }
                    Text("–", Modifier.width(12.dp), textAlign = TextAlign.Center, color = LiquidTheme.colorScheme.onSurface)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        PeriodClockWheel(0..23, end / 60, "period-end-hour", Modifier.weight(1f),
                            { track("end-hour", it) }) { end = it * 60 + end % 60 }
                        Text(":", color = LiquidTheme.colorScheme.onSurface)
                        PeriodClockWheel(0..59, end % 60, "period-end-minute", Modifier.weight(1f),
                            { track("end-minute", it) }) { end = end / 60 * 60 + it }
                    }
                }
                error?.let { Text(it, color = LiquidTheme.colorScheme.error, style = LiquidTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(candidate.getOrThrow()); onDismiss() },
                enabled = error == null && scrolling.isEmpty(), modifier = Modifier.testTag("period-time-confirm")) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PeriodClockWheel(range: IntRange, value: Int, tag: String, modifier: Modifier,
    onScrolling: (Boolean) -> Unit, onSelected: (Int) -> Unit) {
    val values = remember(range) { range.toList() }
    CourseTimeWheel(values, value, { it.toString().padStart(2, '0') }, modifier.testTag(tag), onScrolling, onSelected)
}

@Composable
internal fun PeriodChangesPreview(before: List<PeriodTime>, after: List<PeriodTime>) {
    val changes = before.indices.filter { before[it] != after[it] }
    if (changes.isNotEmpty()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("调整预览", style = LiquidTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        changes.forEach { index ->
            Text("第 ${index + 1} 节  ${before[index].label()} → ${after[index].label()}",
                style = LiquidTheme.typography.bodySmall)
        }
    }
}
