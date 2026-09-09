package com.coursetable.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

    FullscreenPageContainer {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "课表管理",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        SectionFrame {
            LazyColumn {
                items(timetables, key = { it.id }) { table ->
                    val isActive = table.id == activeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSwitch(table.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Filled.CheckCircle
                            else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                table.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                "${table.semesterStart().format(DateTimeFormatter.ISO_LOCAL_DATE)} 开学 · " +
                                    "${table.totalWeeks} 周 · ${table.periods().size} 节",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { renameTarget = table }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "重命名",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (timetables.size > 1) {
                            IconButton(onClick = { timetableToDelete = table }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除课表",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            IconButton(onClick = {}, enabled = false) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "无法删除唯一课表",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        Button(
            onClick = { showCreate = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("新建课表")
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
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("课表名称") },
                modifier = Modifier.fillMaxWidth()
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
