package com.coursetable.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Course
import com.coursetable.app.data.weeksLabel
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme
import com.coursetable.app.ui.theme.fromStoredLong

private fun courseSectionLabel(start: Int, end: Int): String =
    if (start == end) "第 $start 节" else "第 $start–$end 节"

internal fun courseOccurrenceTime(course: Course): String =
    "周${"一二三四五六日".getOrNull(course.dayOfWeek - 1) ?: "?"} · " +
        courseSectionLabel(course.startSection, course.startSection + course.duration - 1)

/** A single sheet; the detail action runs only after its exit animation completes. */
@Composable
internal fun TimetableConflictPicker(
    block: TimetableCourseBlock,
    onDismiss: () -> Unit,
    onSelect: (Course) -> Unit
) {
    var selecting by remember(block) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, preferReadability = true) {
        val dismissController = LocalDialogDismissController.current
        val currentOnSelect by rememberUpdatedState(onSelect)
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)
            .testTag("timetable-conflict-picker")) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("冲突课程（${block.items.size} 门）", style = LiquidTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("周${"一二三四五六日"[block.dayOfWeek - 1]} · " +
                    courseSectionLabel(block.startSection, block.endSectionExclusive - 1),
                    style = LiquidTheme.typography.bodyMedium, color = LiquidTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            // Weight limits the list to the sheet's available height, even with large text.
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("timetable-conflict-list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(block.items, key = { it.course.id }) { item ->
                    val course = item.course
                    Row(Modifier.fillMaxWidth().listItemSurface(ListItemMaterial.Glass)
                        .heightIn(min = 64.dp)
                        .testTag("timetable-conflict-choice-${course.id}")
                        .clickable(enabled = !selecting, role = Role.Button) {
                            selecting = true
                            val commit = { currentOnSelect(course) }
                            dismissController?.dismiss(commit) ?: commit()
                        }.padding(14.dp),
                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape)
                            .background(Color.fromStoredLong(course.color)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(course.name, style = LiquidTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(courseOccurrenceTime(course), style = LiquidTheme.typography.bodyMedium,
                                color = LiquidTheme.colorScheme.onSurfaceVariant)
                            Text("教师：${course.teacher.ifBlank { "未填写" }}", style = LiquidTheme.typography.bodyMedium,
                                color = LiquidTheme.colorScheme.onSurfaceVariant)
                            Text("地点：${course.location.ifBlank { "未填写" }}", style = LiquidTheme.typography.bodyMedium,
                                color = LiquidTheme.colorScheme.onSurfaceVariant)
                            Text(course.weeksLabel(), style = LiquidTheme.typography.bodySmall,
                                color = LiquidTheme.colorScheme.onSurfaceVariant)
                        }
                        ListItemChevron(Modifier.padding(top = 3.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !selecting, onClick = { dismissController?.dismiss() ?: onDismiss() }) { Text("取消") }
            }
        }
    }
}
