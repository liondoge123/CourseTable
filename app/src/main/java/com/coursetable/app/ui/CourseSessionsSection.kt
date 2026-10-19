package com.coursetable.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Course
import com.coursetable.app.data.weeksLabel
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme

private fun sessionTime(course: Course): String {
    val day = "周${"一二三四五六日".getOrNull(course.dayOfWeek - 1) ?: "?"}"
    val end = course.startSection + course.duration - 1
    val sections = if (course.duration == 1) "第 ${course.startSection} 节" else "第 ${course.startSection}–$end 节"
    return "$day · $sections"
}

/** Same form surfaces, selection rows and action roles as period/week editors. */
@Composable
internal fun CourseSessionsSection(
    sessions: List<Course>, selectedIndex: Int, enabled: Boolean,
    onSelect: (Int) -> Unit, onAdd: () -> Unit, onRemove: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    var choosing by remember { mutableStateOf(false) }
    FormSectionCard(Modifier.testTag("course-sessions-section"), compact = true) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(Modifier.heightIn(min = if (sessions.size > 1) 48.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EventNote, null, Modifier.size(20.dp), tint = LiquidTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("上课时段", style = LiquidTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (sessions.size > 1) {
                FilledTonalButton(
                    onClick = { choosing = true },
                    enabled = enabled,
                    modifier = Modifier.testTag("course-session-selector").semantics {
                        contentDescription = "切换上课时段"
                        stateDescription = "正在编辑时段 ${selectedIndex + 1}，共 ${sessions.size} 个时段"
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("时段 ${selectedIndex + 1} / ${sessions.size}", style = LiquidTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp).rotate(90f))
                }
            }
        }
        content()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onAdd, enabled = enabled, modifier = Modifier.weight(1f).testTag("course-session-add")) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加时段")
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    enabled = enabled,
                    modifier = Modifier.testTag("course-session-remove"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = LiquidTheme.colorScheme.error.copy(alpha = 0.10f),
                        contentColor = LiquidTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, "删除此时段", Modifier.size(18.dp))
                }
            }
        }
    }
    if (choosing) {
        SessionPickerDialog(sessions, selectedIndex, enabled, onSelect, onDismiss = { choosing = false })
    }
}

@Composable
private fun SessionPickerDialog(
    sessions: List<Course>, selectedIndex: Int, enabled: Boolean,
    onSelect: (Int) -> Unit, onDismiss: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换上课时段") },
        text = {
            val dismissController = LocalDialogDismissController.current
            val colors = LiquidTheme.colorScheme
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择要编辑的时段", style = LiquidTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                LazyColumn(
                    Modifier.heightIn(max = 360.dp)
                        .selectableGroup().testTag("course-session-choices"),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 1.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(sessions, key = { index, _ -> index }) { index, session ->
                        SessionGlassChoice(session, index, index == selectedIndex, enabled) {
                            val commit = { onSelect(index); onDismiss() }
                            dismissController?.dismiss(commit) ?: commit()
                        }
                    }
                }
            }
        },
        confirmButton = {
            val dismissController = LocalDialogDismissController.current
            TextButton(onClick = { dismissController?.dismiss() ?: onDismiss() }) { Text("取消") }
        }
    )
}

/** Sample the same scene as the dialog, with a softer frost suited to compact text rows. */
@Composable
private fun SessionGlassChoice(session: Course, index: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LiquidTheme.colorScheme
    val dark = colors.isDark
    Row(
        Modifier.fillMaxWidth().listItemSurface(ListItemMaterial.Glass, selected = active)
            .heightIn(min = 64.dp)
            .selectable(selected = active, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "时段 ${index + 1}" }
            .testTag("course-session-choice-$index")
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (active) colors.primary.copy(alpha = 0.10f) else Color.White.copy(alpha = if (dark) 0.04f else 0.12f))
                .border(0.7.dp, Color.White.copy(alpha = if (dark) 0.18f else 0.48f), CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", style = LiquidTheme.typography.labelMedium,
                color = if (active) colors.primary else colors.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(sessionTime(session), style = LiquidTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (active) colors.primary else colors.onSurface)
            Text(listOf(session.weeksLabel(), session.location).filter { it.isNotBlank() }.joinToString(" · "),
                style = LiquidTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        ListChoiceIndicator(active, Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun SessionSettingRow(title: String, value: String, icon: ImageVector, tag: String, enabled: Boolean, onClick: () -> Unit, isError: Boolean = false) {
    Surface(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag(tag)
        .listItemSurface(ListItemMaterial.Inset, selected = isError, accent = if (isError) LiquidTheme.colorScheme.error else LiquidTheme.colorScheme.primary),
        shape=ListItemDefaults.Shape,color=Color.Transparent) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val inline = maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.3f
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = LiquidTheme.colorScheme.onSurfaceVariant)
                if (value.isBlank()) {
                    Text(title,modifier = Modifier.weight(1f),style=LiquidTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,
                        color=if (isError) LiquidTheme.colorScheme.error else LiquidTheme.colorScheme.onSurface)
                } else if (inline) {
                    Text(title,style=LiquidTheme.typography.bodyMedium,color=LiquidTheme.colorScheme.onSurfaceVariant)
                    Text(value,modifier = Modifier.weight(1f),textAlign = TextAlign.End,style=LiquidTheme.typography.bodyMedium,
                        fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                } else {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title,style=LiquidTheme.typography.labelMedium,color=LiquidTheme.colorScheme.onSurfaceVariant)
                        Text(value,style=LiquidTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
                ListItemChevron()
            }
        }
    }
}
