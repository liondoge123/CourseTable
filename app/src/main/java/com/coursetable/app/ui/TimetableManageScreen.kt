package com.coursetable.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Timetable
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import java.time.format.DateTimeFormatter

/**
 * 课表管理：切换、新建、重命名、删除课表
 */
@Composable
fun TimetableManageScreen(
    timetables: List<Timetable>,
    activeId: Long,
    onBack: () -> Unit,
    onSwitch: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Timetable) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Timetable?>(null) }
    var timetableToDelete by remember { mutableStateOf<Timetable?>(null) }

    LiquidScrollablePage(Modifier.testTag("timetable-manage-page"), topBar = {
        LiquidPageTopBar("课表管理", onBack) {
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建课表")
            }
        }
    }) { pageInsets ->
        LazyColumn(Modifier.fillMaxSize().selectableGroup().testTag("timetables-list"), state = rememberPageLazyListState(), contentPadding = pageInsets.pagePadding(),
            verticalArrangement = Arrangement.spacedBy(ManagementListDefaults.Gap)) {
            item(key = "summary") {
                Text(
                    "${timetables.size} 个课表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(timetables, key = { it.id }, contentType = { "timetable" }) { table ->
                TimetableCard(
                    table = table,
                    isActive = table.id == activeId,
                    canDelete = timetables.size > 1,
                    onSwitch = { onSwitch(table.id) },
                    onRename = { renameTarget = table },
                    onDelete = { timetableToDelete = table }
                )
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "新建课表",
            initial = "",
            onConfirm = { name ->
                onCreate(name)
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "重命名课表",
            initial = target.name,
            onConfirm = { name ->
                onRename(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    timetableToDelete?.let { table ->
        AlertDialog(
            onDismissRequest = { timetableToDelete = null },
            title = { Text("删除课表") },
            confirmButtonRole = DialogActionRole.Destructive,
            text = { Text("确定要删除课表「${table.name}」吗？此操作不可撤销。") },
            confirmButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(
                    onClick = {
                        dismissController?.dismiss {
                            onDelete(table)
                            timetableToDelete = null
                        } ?: run {
                            onDelete(table)
                            timetableToDelete = null
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(
                    onClick = {
                        dismissController?.dismiss { timetableToDelete = null } ?: run { timetableToDelete = null }
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun TimetableCard(
    table: Timetable,
    isActive: Boolean,
    canDelete: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ListItemSurface(
        modifier = Modifier.fillMaxWidth().testTag("timetable-card-${table.id}")
    ) {
        Row(
            Modifier.fillMaxWidth()
                .selectable(selected = isActive, role = Role.RadioButton, onClick = onSwitch)
                .testTag("timetable-select-${table.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f)
                    .heightIn(min = ManagementListDefaults.MinHeight)
                    .padding(ManagementListDefaults.ContentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListChoiceIndicator(isActive)
                Spacer(Modifier.size(ManagementListDefaults.IndicatorGap))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ManagementListDefaults.TextGap)) {
                    Text(table.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${table.semesterStart().format(DateTimeFormatter.ISO_LOCAL_DATE)} 开学",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        "${table.totalWeeks} 周 · 每天 ${table.periods().size} 节",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            ItemActionsMenu(
                description = "课表「${table.name}」的更多操作",
                modifier = Modifier.padding(end = ManagementListDefaults.TrailingPadding),
                actions = listOf(ItemMenuAction("重命名", onRename)) +
                    if (canDelete) listOf(ItemMenuAction("删除课表", onDelete, destructive = true)) else emptyList()
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, Modifier.testTag("name-dialog-title")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("课表名称") },
                showUnfocusedBorder = false,
                containerAlpha = 0.55f,
                modifier = Modifier.fillMaxWidth().testTag("timetable-name-input")
            )
        },
        confirmButton = {
            val dismissController = LocalDialogDismissController.current
            TextButton(
                onClick = {
                    dismissController?.dismiss { onConfirm(name.trim()) } ?: onConfirm(name.trim())
                },
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            val dismissController = LocalDialogDismissController.current
            TextButton(onClick = { dismissController?.dismiss() ?: onDismiss() }) { Text("取消") }
        }
    )
}
