package com.coursetable.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.PeriodTimeScheme
import com.coursetable.app.data.PeriodUtils
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SchemeRowCornerRadius = 8.dp

/** 列表和编辑共享一个窗口；编辑保存与课表选用分别执行。 */
@Composable
fun PeriodTimeSchemesDialog(
    schemes: List<PeriodTimeScheme>,
    periods: List<PeriodTime>,
    durationMinutes: Int,
    currentSchemeId: String? = null,
    onSave: suspend (PeriodTimeScheme) -> Unit,
    onApply: suspend (PeriodTimeScheme) -> Unit,
    onSaveCurrent: suspend (PeriodTimeScheme) -> Unit,
    onDelete: suspend (String) -> Unit,
    onDismiss: () -> Unit
) {
    val availableSchemes = PeriodTimeScheme.withDefault(schemes, PeriodTimeScheme.defaultFor(0, periods, durationMinutes))
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<PeriodTimeScheme?>(null) }
    var editingCurrent by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var previewUniformDuration by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PeriodTimeScheme?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSchemeId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentSchemeId) {
        if (pendingSchemeId == currentSchemeId) {
            pendingSchemeId = null
        }
    }

    val effectiveSchemeId = pendingSchemeId ?: currentSchemeId
    val resolvedActiveSchemeId = remember(availableSchemes, periods, durationMinutes, effectiveSchemeId, pendingSchemeId) {
        if (pendingSchemeId != null) {
            pendingSchemeId
        } else if (effectiveSchemeId != null) {
            availableSchemes.firstOrNull { it.id == effectiveSchemeId && it.matches(periods, durationMinutes) }?.id
        } else {
            availableSchemes.firstOrNull { it.isDefault && it.matches(periods, durationMinutes) }?.id
        }
    }

    fun returnToList() {
        draft = null
        editingIndex = null
        previewUniformDuration = false
        error = null
    }

    fun openEditor(scheme: PeriodTimeScheme, current: Boolean = false) {
        draft = scheme
        editingCurrent = current
        editingIndex = null
        previewUniformDuration = false
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
        if (busy) return
        val item = draft ?: return
        val normalized = item.copy(name = item.name.trim())
        error = when {
            !editingCurrent && normalized.name.isBlank() -> "请输入方案名称"
            !editingCurrent && availableSchemes.any { it.id != item.id && it.name == normalized.name } -> "已有同名方案"
            PeriodUtils.validatePeriods(normalized.periods) != null -> PeriodUtils.validatePeriods(normalized.periods)
            PeriodTimeScheme.decode(PeriodTimeScheme.encode(listOf(normalized))) != listOf(normalized) -> "请检查方案信息"
            else -> null
        }
        if (error != null) return
        val saveCurrent = editingCurrent
        runOperation(
            action = { if (saveCurrent) onSaveCurrent(normalized) else onSave(normalized) },
            onSuccess = {
                if (saveCurrent) {
                    pendingSchemeId = normalized.id
                }
                returnToList()
            }
        )
    }

    val presentedDraft = draft
    val presentedEditingCurrent = editingCurrent
    AlertDialog(
        onDismissRequest = onDismiss,
        actionShape = LiquidButtonShape,
        confirmButtonEmphasized = draft != null,
        fixedEditorFrame = draft != null,
        pageTransitionKey = presentedDraft?.id ?: "scheme-list",
        onBackRequest = when {
            busy -> ({})
            draft != null -> ({ returnToList() })
            else -> null
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (presentedDraft != null) {
                    IconButton(onClick = { returnToList() }, enabled = !busy, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回方案列表")
                    }
                }
                Text(if (presentedDraft == null) "节次时间" else if (presentedDraft.isDefault) PeriodTimeScheme.DEFAULT_NAME else if (presentedEditingCurrent) "当前自定义" else "编辑方案")
            }
        },
        text = {
            val item = presentedDraft
            if (item == null) {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    if (resolvedActiveSchemeId == null) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(SchemeRowCornerRadius)).clickable(enabled = !busy) {
                                if (pendingSchemeId != null) return@clickable
                                openEditor(PeriodTimeScheme("current", "当前自定义", periods, durationMinutes), current = true)
                            }.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("当前自定义", style = MaterialTheme.typography.titleSmall)
                                Text("${periods.size} 节", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "编辑当前时间", modifier = Modifier.size(20.dp))
                        }
                        HorizontalDivider(Modifier.padding(horizontal = SchemeRowCornerRadius))
                    }
                    availableSchemes.forEach { scheme ->
                        val selected = scheme.id == resolvedActiveSchemeId
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(SchemeRowCornerRadius)).clickable(enabled = !busy) {
                                if (pendingSchemeId != null) return@clickable
                                openEditor(scheme, current = scheme.isDefault)
                            }
                                .heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(scheme.name, style = MaterialTheme.typography.titleSmall)
                                Text("${scheme.periods.size} 节", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                onClick = {
                                    if (selected || busy || pendingSchemeId != null) return@TextButton
                                    pendingSchemeId = scheme.id
                                    scope.launch {
                                        try {
                                            onApply(scheme)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            pendingSchemeId = null
                                            error = "选用失败，请重试"
                                        }
                                    }
                                },
                                enabled = !busy && !selected,
                                modifier = Modifier.defaultMinSize(minWidth = 64.dp)
                            ) { Text(if (selected) "已选用" else "选用") }
                            if (!scheme.isDefault) IconButton(
                                onClick = {
                                    if (pendingSchemeId != null || busy) return@IconButton
                                    deleteTarget = scheme
                                },
                                enabled = !busy
                            ) {
                                Icon(Icons.Filled.Delete, "删除${scheme.name}", modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = SchemeRowCornerRadius))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { openEditor(PeriodTimeScheme(UUID.randomUUID().toString(), "", periods, durationMinutes)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("新建方案") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    if (!presentedEditingCurrent) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { draft = item.copy(name = it); error = null },
                            label = { Text("方案名称") },
                            showUnfocusedBorder = false,
                            containerAlpha = 0.55f,
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().testTag("scheme-name-input")
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("默认课时长", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            val duration = (item.durationMinutes - 5).coerceIn(20, 90)
                            draft = item.copy(durationMinutes = duration)
                            error = null
                        }, enabled = !busy && item.durationMinutes > 20) {
                            Icon(Icons.Filled.Remove, "减 5 分钟", modifier = Modifier.size(20.dp))
                        }
                        Text("${item.durationMinutes} 分钟", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = {
                            val duration = (item.durationMinutes + 5).coerceIn(20, 90)
                            draft = item.copy(durationMinutes = duration)
                            error = null
                        }, enabled = !busy && item.durationMinutes < 90) {
                            Icon(Icons.Filled.Add, "加 5 分钟", modifier = Modifier.size(20.dp))
                        }
                    }
                    TextButton(onClick = { previewUniformDuration = true }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("统一已有节次课时长…") }
                    HorizontalDivider(Modifier.padding(horizontal = SchemeRowCornerRadius))
                    item.periods.forEachIndexed { index, period ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(SchemeRowCornerRadius)).clickable(enabled = !busy) { editingIndex = index }
                                .testTag("period-row-${index + 1}")
                                .heightIn(min = 56.dp).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("第 ${index + 1} 节课", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text("${period.start.format(PeriodTime.TIME_FMT)} - ${period.end.format(PeriodTime.TIME_FMT)}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                            if (item.periods.size > 1) {
                                IconButton(onClick = {
                                    draft = item.copy(periods = item.periods.filterIndexed { i, _ -> i != index })
                                }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Filled.Remove, "删除第 ${index + 1} 节", modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (index != item.periods.lastIndex) HorizontalDivider(Modifier.padding(horizontal = SchemeRowCornerRadius))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = {
                        runCatching { PeriodUtils.appendPeriod(item.periods, item.durationMinutes) }
                            .onSuccess { draft = item.copy(periods = it); error = null }
                            .onFailure { error = it.message }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("添加节次") }
                    if (presentedEditingCurrent) {
                        Spacer(Modifier.height(8.dp))
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
            }, enabled = !busy) { Text(if (busy) "保存中…" else if (presentedDraft != null) "保存" else "关闭") }
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
                    val deletedId = scheme.id
                    val action = {
                        deleteTarget = null
                        runOperation(
                            action = { onDelete(deletedId) },
                            onSuccess = { if (pendingSchemeId == deletedId) pendingSchemeId = null }
                        )
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

    if (previewUniformDuration) draft?.let { item ->
        val candidate = runCatching { PeriodUtils.withUniformDuration(item.periods, item.durationMinutes) }
        val problem = candidate.exceptionOrNull()?.message ?: candidate.getOrNull()?.let { PeriodUtils.validatePeriods(it) }
        AlertDialog(
            onDismissRequest = { previewUniformDuration = false },
            title = { Text("统一为 ${item.durationMinutes} 分钟") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    candidate.getOrNull()?.let { PeriodChangesPreview(item.periods, it) }
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = {
                draft = item.copy(periods = candidate.getOrThrow())
                previewUniformDuration = false
                error = null
            }, enabled = problem == null) { Text("应用到草稿") } },
            dismissButton = { TextButton(onClick = { previewUniformDuration = false }) { Text("取消") } }
        )
    }

    editingIndex?.let { index ->
        val item = draft ?: return@let
        if (index in item.periods.indices) PeriodTimeEditorDialog(
            periods = item.periods, index = index,
            onConfirm = { draft = item.copy(periods = it); error = null },
            onDismiss = { editingIndex = null }
        )
    }
}
