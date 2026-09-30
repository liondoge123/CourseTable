package com.coursetable.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.PeriodTimeScheme
import com.coursetable.app.data.PeriodUtils
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 列表和编辑共享一个窗口；编辑保存与课表选用分别执行。 */
@Composable
fun PeriodTimeSchemesDialog(
    schemes: List<PeriodTimeScheme>,
    periods: List<PeriodTime>,
    durationMinutes: Int,
    onSave: suspend (PeriodTimeScheme) -> Unit,
    onApply: suspend (PeriodTimeScheme) -> Unit,
    onSaveCurrent: suspend (PeriodTimeScheme) -> Unit,
    onDelete: suspend (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<PeriodTimeScheme?>(null) }
    var editingCurrent by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deleteTarget by remember { mutableStateOf<PeriodTimeScheme?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun returnToList() {
        draft = null
        editingIndex = null
        error = null
    }

    fun openEditor(scheme: PeriodTimeScheme, current: Boolean = false) {
        draft = scheme
        editingCurrent = current
        editingIndex = null
        error = null
    }

    fun runOperation(action: suspend () -> Unit, onSuccess: () -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "保存失败，请重试"
            } finally {
                busy = false
            }
        }
    }

    fun saveDraft() {
        val item = draft ?: return
        val normalized = item.copy(name = item.name.trim())
        error = when {
            !editingCurrent && normalized.name.isBlank() -> "请输入方案名称"
            !editingCurrent && schemes.any { it.id != item.id && it.name == normalized.name } -> "已有同名方案"
            PeriodTimeScheme.decode(PeriodTimeScheme.encode(listOf(normalized))) != listOf(normalized) -> "请检查节次时间，不能重叠或跨越午夜"
            else -> null
        }
        if (error != null) return
        val saveCurrent = editingCurrent
        runOperation(
            action = { if (saveCurrent) onSaveCurrent(normalized) else onSave(normalized) },
            onSuccess = { returnToList() }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        actionShape = LiquidButtonShape,
        confirmButtonEmphasized = draft != null,
        onBackRequest = when {
            busy -> ({})
            draft != null -> ({ returnToList() })
            else -> null
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (draft != null) {
                    IconButton(onClick = { returnToList() }, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回方案列表")
                    }
                }
                Text(if (draft == null) "节次时间" else if (editingCurrent) "当前自定义" else "编辑方案")
            }
        },
        text = {
            val item = draft
            if (item == null) {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (schemes.none { it.matches(periods, durationMinutes) }) {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                openEditor(PeriodTimeScheme("current", "当前自定义", periods, durationMinutes), current = true)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("当前自定义", style = MaterialTheme.typography.titleSmall)
                                Text("${periods.size} 节", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "编辑当前时间", modifier = Modifier.size(20.dp))
                        }
                        HorizontalDivider()
                    }
                    if (schemes.isEmpty()) Text("暂无方案", style = MaterialTheme.typography.bodySmall)
                    schemes.forEach { scheme ->
                        val selected = scheme.matches(periods, durationMinutes)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).clickable(enabled = !busy) { openEditor(scheme) }
                                .padding(vertical = 8.dp)) {
                                Text(scheme.name, style = MaterialTheme.typography.titleSmall)
                                Text("${scheme.periods.size} 节", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                onClick = { runOperation({ onApply(scheme) }) },
                                enabled = !busy && !selected
                            ) { Text(if (selected) "已选用" else "选用") }
                            IconButton(onClick = { deleteTarget = scheme }, enabled = !busy) {
                                Icon(Icons.Filled.Delete, "删除${scheme.name}", modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider()
                    }
                    Button(
                        onClick = { openEditor(PeriodTimeScheme(UUID.randomUUID().toString(), "", periods, durationMinutes)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("新建方案") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!editingCurrent) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { draft = item.copy(name = it); error = null },
                            label = { Text("方案名称") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("每节课时长", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            val duration = (item.durationMinutes - 5).coerceIn(20, 90)
                            draft = item.copy(durationMinutes = duration, periods = PeriodUtils.build(item.periods.map { it.start }, duration))
                        }, enabled = !busy && item.durationMinutes > 20) {
                            Icon(Icons.Filled.Remove, "减 5 分钟", modifier = Modifier.size(20.dp))
                        }
                        Text("${item.durationMinutes} 分钟", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = {
                            val duration = (item.durationMinutes + 5).coerceIn(20, 90)
                            draft = item.copy(durationMinutes = duration, periods = PeriodUtils.build(item.periods.map { it.start }, duration))
                        }, enabled = !busy && item.durationMinutes < 90) {
                            Icon(Icons.Filled.Add, "加 5 分钟", modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider()
                    item.periods.forEachIndexed { index, period ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) { editingIndex = index }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("第 ${index + 1} 节", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                            Column(Modifier.weight(1f)) {
                                Text(period.start.format(PeriodTime.TIME_FMT), color = MaterialTheme.colorScheme.primary)
                                Text("结束 ${period.end.format(PeriodTime.TIME_FMT)}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.periods.size > 1) {
                                IconButton(onClick = {
                                    draft = item.copy(periods = item.periods.filterIndexed { i, _ -> i != index })
                                }, enabled = !busy) {
                                    Icon(Icons.Filled.Remove, "删除第 ${index + 1} 节", modifier = Modifier.size(18.dp))
                                }
                            }
                            Icon(Icons.Filled.Edit, "编辑第 ${index + 1} 节", modifier = Modifier.size(18.dp))
                        }
                        if (index != item.periods.lastIndex) HorizontalDivider()
                    }
                    OutlinedButton(onClick = {
                        val start = item.periods.lastOrNull()?.end?.plusMinutes(10) ?: LocalTime.of(8, 0)
                        draft = item.copy(periods = item.periods + PeriodTime(start, start.plusMinutes(item.durationMinutes.toLong())))
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("添加节次") }
                    if (editingCurrent) {
                        OutlinedButton(onClick = {
                            openEditor(item.copy(id = UUID.randomUUID().toString(), name = ""))
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("另存为方案") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            val controller = LocalDialogDismissController.current
            TextButton(onClick = {
                if (draft != null) saveDraft() else controller?.dismiss() ?: onDismiss()
            }, enabled = !busy) { Text(if (busy) "保存中…" else if (draft != null) "保存" else "关闭") }
        },
        dismissButton = if (draft != null) {
            { TextButton(onClick = { returnToList() }, enabled = !busy) { Text("取消") } }
        } else null
    )

    deleteTarget?.let { scheme ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除方案") },
            confirmButtonRole = DialogActionRole.Destructive,
            text = { Text("删除「${scheme.name}」？") },
            confirmButton = {
                val controller = LocalDialogDismissController.current
                TextButton(onClick = {
                    val action = {
                        deleteTarget = null
                        runOperation({ onDelete(scheme.id) })
                    }
                    controller?.dismiss(action) ?: action()
                }) { Text("删除") }
            },
            dismissButton = {
                val controller = LocalDialogDismissController.current
                TextButton(onClick = { controller?.dismiss() ?: run { deleteTarget = null } }) { Text("取消") }
            }
        )
    }

    editingIndex?.let { index ->
        val item = draft ?: return@let
        val initial = item.periods.getOrNull(index)?.start ?: return@let
        val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("第 ${index + 1} 节开始时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                val controller = LocalDialogDismissController.current
                TextButton(onClick = {
                    val action = {
                        val start = LocalTime.of(timeState.hour, timeState.minute)
                        draft = item.copy(periods = item.periods.toMutableList().also {
                            it[index] = PeriodTime(start, start.plusMinutes(item.durationMinutes.toLong()))
                        })
                        editingIndex = null
                        error = null
                    }
                    controller?.dismiss(action) ?: action()
                }) { Text("确定") }
            },
            dismissButton = {
                val controller = LocalDialogDismissController.current
                TextButton(onClick = { controller?.dismiss() ?: run { editingIndex = null } }) { Text("取消") }
            }
        )
    }
}
