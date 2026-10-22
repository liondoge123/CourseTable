package com.coursetable.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.coursetable.app.CourseApp
import com.coursetable.app.importer.scheduleConflicts
import com.coursetable.app.importer.scheduleFingerprint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Course
import com.coursetable.app.data.scheduledWeeks
import com.coursetable.app.data.withScheduledWeeks
import com.coursetable.app.data.weeksLabel
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseColorPalette
import com.coursetable.app.ui.theme.fromStoredLong
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import com.coursetable.app.ui.theme.toArgbLong

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseEditorDialog(
    course: Course,
    totalWeeks: Int,
    periodCount: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    title: String? = null,
    sourceContent: (@Composable () -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showColorPicker: Boolean = true,
    pinSourceContent: Boolean = false,
    embedded: Boolean = false,
    saveLabel: String? = null,
    protectEdits: Boolean = false,
    reviewHints: List<String> = emptyList(),
    onSaveSuspend: (suspend (Course) -> Unit)? = null,
    reviewContent: (@Composable () -> Unit)? = null
) {
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.name) }
    var teacher by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.teacher) }
    var location by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.location) }
    var day by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.dayOfWeek.coerceIn(1, 7)) }
    var startSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.startSection.coerceIn(1, periodCount)) }
    var duration by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.duration.coerceIn(1, (periodCount - course.startSection.coerceIn(1, periodCount) + 1).coerceAtLeast(1))) }
    var selectedWeeks by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(course.scheduledWeeks().filter { it in 1..totalWeeks })
    }
    var showTimePicker by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showWeeksPicker by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var timeReviewed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val schedule = if (selectedWeeks.isNotEmpty()) course.withScheduledWeeks(selectedWeeks) else course
    var color by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.color) }
    var saving by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as CourseApp
    val scope = rememberCoroutineScope()
    var sessionDrafts by remember(course.id) { mutableStateOf(listOf(course.copy()) + course.additionalSessions.orEmpty()) }
    var slot by remember(course.id) { mutableIntStateOf(0) }
    var loaded by remember(course.id) { mutableStateOf(course.courseId == 0L || !showColorPicker) }
    var removeSlot by remember { mutableStateOf(false) }
    var deleteCourseConfirm by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<Course?>(null) }
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    var saveError by remember { mutableStateOf<String?>(null) }
    fun deliver(saved: Course) {
        if (onSaveSuspend == null) { onSave(saved); return }
        scope.launch {
            try { onSaveSuspend(saved) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { saveError = e.message ?: "保存失败，请重试"; saving = false }
        }
    }
    LaunchedEffect(course.courseId) {
        if (showColorPicker && course.courseId > 0) {
            sessionDrafts = app.courseRepository.byTimetableOnce(course.timetableId).filter { it.courseId == course.courseId }.sortedBy { it.id }
            slot = sessionDrafts.indexOfFirst { it.id == course.id }.coerceAtLeast(0)
            loaded = true
        }
    }
    fun currentSession(): Course = (sessionDrafts.getOrNull(slot) ?: course).copy(location = location, dayOfWeek = day,
        startSection = startSection, duration = duration).withScheduledWeeks(selectedWeeks)
    fun switchSlot(next: Int, append: Boolean = false) {
        if (selectedWeeks.isEmpty()) return
        val drafts = sessionDrafts.toMutableList()
        drafts[slot] = currentSession()
        if (append) drafts += course.copy(id = 0, location = "", dayOfWeek = 1, startSection = 1, duration = 1).withScheduledWeeks((1..totalWeeks).toList())
        sessionDrafts = drafts
        slot = next
        val chosen = drafts[next]
        location = chosen.location; day = chosen.dayOfWeek; startSection = chosen.startSection; duration = chosen.duration
        selectedWeeks = chosen.scheduledWeeks()
    }

    fun save() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || selectedWeeks.isEmpty() || saving) return
        val saved = (if(showColorPicker) currentSession() else schedule).copy(
                name = trimmed,
                teacher = teacher.trim(),
                location = location.trim(),
                dayOfWeek = day,
                startSection = startSection,
                duration = duration,
                color = color
            )
        if (showColorPicker) saved.additionalSessions = sessionDrafts.filterIndexed { index, _ -> index != slot }
            .map { it.copy(name = trimmed, teacher = teacher.trim(), color = color) }
        if (!showColorPicker) { saving = true; deliver(saved); return }
        saving = true
        scope.launch {
            try {
                val existing = app.courseRepository.byTimetableOnce(course.timetableId.takeIf { it > 0 } ?: app.settingsRepository.settings.first().timetableId)
                saved.reviewedSnapshot = existing
                val own = (listOf(saved) + saved.additionalSessions.orEmpty())
                val others = existing.filter { course.courseId == 0L || it.courseId != course.courseId }
                val duplicates = own.flatMap { s -> others.filter { scheduleFingerprint(s) == scheduleFingerprint(it) }.map { "重复：${it.name}" } }
                warnings = (own.flatMap { s -> scheduleConflicts(s, others + own.filterNot { it === s }).map { it.description() } } + duplicates).distinct()
                if (warnings.isEmpty()) deliver(saved) else { pendingSave = saved; saving = false }
            } catch (e: Exception) { saveError = e.message ?: "读取课表失败，请重试"; saving = false }
        }
    }

    var discardChanges by remember { mutableStateOf(false) }
    val changed = name != course.name || teacher != course.teacher || location != course.location ||
        day != course.dayOfWeek || startSection != course.startSection || duration != course.duration ||
        selectedWeeks != course.scheduledWeeks().filter { it in 1..totalWeeks } || color != course.color || sessionDrafts.size > 1
    fun requestDismiss() { if (!saving) { if (protectEdits && changed) discardChanges = true else onDismiss() } }
    val editorBack = rememberPredictiveBack(
        enabled = embedded && !discardChanges && !showWeeksPicker && !showTimePicker,
        canCommit = { !saving && !(protectEdits && changed) },
        onRejected = { requestDismiss() },
        onBack = { requestDismiss() }
    )
    if (discardChanges) AlertDialog(dismissButtonRole = DialogActionRole.Destructive, onDismissRequest = { discardChanges = false }, title = { Text("保存校对修改？") },
        text = { Text("当前课程有尚未保存的修改。") },
        confirmButton = { TextButton(onClick = { discardChanges = false; save() }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } })
    val deleteAction: @Composable () -> Unit = {
        if (onDelete != null) {
            val deleteLabel = if (saveLabel == null) "删除整门课程" else "删除当前记录"
            TextButton(
                onClick = {
                    if (saveLabel == null) deleteCourseConfirm = true else onDelete()
                },
                enabled = !saving && loaded,
                modifier = Modifier.fillMaxWidth().testTag("course-editor-delete")
                    .clip(LiquidButtonShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
            ) {
                val deleteColor = MaterialTheme.colorScheme.error.copy(alpha = if (!saving && loaded) 1f else 0.38f)
                Icon(Icons.Filled.Delete, deleteLabel, Modifier.size(20.dp), tint = deleteColor)
                Spacer(Modifier.width(8.dp))
                Text(deleteLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = deleteColor)
            }
        }
    }
    val editorContent: @Composable () -> Unit = { EndInputOnOutsideTap { endInputModifier ->
        Column(
            Modifier
                .then(endInputModifier)
                .fillMaxWidth()
                .testTag("course-editor-page")
                .then(if (embedded) Modifier.predictiveBackTransform(editorBack, BackPresentation.DIALOG) else Modifier)
                .fillMaxHeight(if (embedded) 1f else 0.94f)
                .navigationBarsPadding()
                // Reduce the editor's inner viewport without changing the sheet height.
                .imePadding()
        ) {
            val canSave = name.isNotBlank() && selectedWeeks.isNotEmpty() && !saving && loaded

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { requestDismiss() },
                    enabled = !saving,
                    modifier = Modifier
                        .size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "取消", modifier = Modifier.size(20.dp))
                }

                Text(
                    text = title ?: if (course.id == 0L) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = ::save,
                    enabled = canSave,
                    modifier = Modifier
                        .size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        contentColor = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = "保存", modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (pinSourceContent && sourceContent != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("course-editor-pinned-source")) {
                    sourceContent()
                }
            }

            // 表单滚动内容
            Column(
                Modifier
                    .weight(1f)
                    .testTag("course-editor-form")
                    .clipToBounds()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Import review replaces the live preview while sharing the entire editor.
                if (sourceContent != null) {
                    if (!pinSourceContent) sourceContent()
                } else CourseCardPreview(
                    name = name,
                    teacher = teacher,
                    location = location,
                    colorLong = color,
                    day = day,
                    startSection = startSection,
                    duration = duration,
                    weeksLabel = if (selectedWeeks.isEmpty()) "未选择周次" else schedule.weeksLabel()
                )
                reviewContent?.invoke()

                // 模块 1: 基本信息
                EditorSectionCard(icon = Icons.Filled.Edit, title = "基本信息") {
                    if (name.isBlank() && reviewHints.any { "名称" in it }) Text("请补齐课程名称", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("课程名称 *", style = MaterialTheme.typography.bodyMedium) },
                        showUnfocusedBorder = false,
                        containerAlpha = 0.55f,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("course-editor-name")
                    )
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = teacher, onValueChange = { teacher = it }, label = { Text("任课教师", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            showUnfocusedBorder = false, containerAlpha = 0.55f, singleLine = true,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).fillMaxHeight().testTag("course-editor-teacher")
                        )
                        OutlinedTextField(
                            value = location, onValueChange = { location = it }, label = { Text(if(showColorPicker) "本时段地点" else "上课地点", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            showUnfocusedBorder = false, containerAlpha = 0.55f, singleLine = true,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).fillMaxHeight().testTag("course-editor-location")
                        )
                    }
                }

                val visibleSessions = sessionDrafts.mapIndexed { index, draft ->
                    if (index == slot) (if (selectedWeeks.isEmpty()) draft else draft.withScheduledWeeks(selectedWeeks)).copy(
                        dayOfWeek = day, startSection = startSection, duration = duration, location = location) else draft
                }
                val sessionFields: @Composable ColumnScope.() -> Unit = {
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    val timeNeedsReview = !timeReviewed && reviewHints.any { "星期" in it || "节次" in it }
                    val sections = if (duration == 1) "第 $startSection 节" else "第 $startSection–${startSection + duration - 1} 节"
                    SessionSettingRow("上课时间", if (timeNeedsReview) "" else "周${WeekdayLabels[day - 1]} · $sections",
                        Icons.Filled.Schedule, "course-editor-time", !saving && loaded, { showTimePicker = true }, isError = timeNeedsReview)
                    SessionSettingRow("上课周次", if (selectedWeeks.isEmpty()) "" else schedule.weeksLabel(),
                        Icons.Filled.DateRange, "course-editor-weeks", !saving && loaded, { showWeeksPicker = true }, isError = selectedWeeks.isEmpty())
                }
                if (showColorPicker) CourseSessionsSection(
                    sessions = visibleSessions, selectedIndex = slot, enabled = !saving && loaded,
                    onSelect = { switchSlot(it) }, onAdd = { switchSlot(sessionDrafts.size, true) },
                    onRemove = if(sessionDrafts.size > 1 || onDelete != null) ({ removeSlot = true }) else null,
                    content = sessionFields
                ) else EditorSectionCard(icon = Icons.Filled.EventNote, title = "上课时段", content = sessionFields)

                // 模块 4: 课程颜色
                if (showColorPicker) EditorSectionCard(icon = Icons.Filled.Palette, title = "课程颜色") {
                    val paletteItems = remember {
                        CourseColorPalette.map { c ->
                            Triple(c, c.toArgbLong(), c.luminance() > 0.55f)
                        }
                    }
                    // Six equal-width cells keep the original two-row palette on narrow editors.
                    Column(
                        Modifier.fillMaxWidth().testTag("course-color-grid"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        paletteItems.chunked(6).forEach { paletteRow ->
                            Row(Modifier.fillMaxWidth()) {
                                paletteRow.forEach { (paletteColor, paletteColorLong, isLight) ->
                                    val selected = paletteColorLong == color
                                    val checkColor = if (isLight) Color(0xFF1C1C1E) else Color.White
                                    Box(
                                        Modifier.weight(1f).height(48.dp)
                                            .testTag("course-color-$paletteColorLong")
                                            .clickable { color = paletteColorLong },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier.size(40.dp).border(
                                                2.dp,
                                                if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                CircleShape
                                            ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                Modifier.size(30.dp).clip(CircleShape).background(paletteColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (selected) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = checkColor)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (saveLabel == null) deleteAction()
                Spacer(Modifier.height(24.dp))
            }
            if (saveLabel != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::save, enabled = canSave, modifier = Modifier.weight(1f)) { Text(saveLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
            }
            if (saveLabel != null) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                deleteAction()
            }
        }
    } }
    if (deleteCourseConfirm) AlertDialog(onDismissRequest = { deleteCourseConfirm = false }, title = { Text("删除整门课程？") },
        text = { Text("课程及其全部 ${sessionDrafts.size} 个上课时段将一起删除。") }, confirmButtonRole = DialogActionRole.Destructive,
        confirmButton = { TextButton(onClick = { deleteCourseConfirm = false; onDelete?.invoke() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteCourseConfirm = false }) { Text("取消") } })
    if (removeSlot) AlertDialog(onDismissRequest = { removeSlot = false }, title = { Text("删除此时段？") },
        text = { Text(if(sessionDrafts.size == 1) "这是最后一个时段，将同时删除整门课程。" else "其他时段保留，保存课程后生效。") },
        confirmButtonRole = DialogActionRole.Destructive,
        confirmButton = { TextButton(onClick = {
            removeSlot = false
            if (sessionDrafts.size == 1) onDelete?.invoke() else {
                sessionDrafts = sessionDrafts.filterIndexed { index, _ -> index != slot }
                slot = 0
                val chosen = sessionDrafts.first(); location = chosen.location; day = chosen.dayOfWeek
                startSection = chosen.startSection; duration = chosen.duration; selectedWeeks = chosen.scheduledWeeks()
            }
        }) { Text("删除") } }, dismissButton = { TextButton(onClick = { removeSlot = false }) { Text("取消") } })
    if (pendingSave != null) AlertDialog(onDismissRequest = { pendingSave = null }, title = { Text("课程重复或时间冲突") },
        text = { Text(warnings.joinToString("\n")) }, confirmButton = { TextButton(onClick = { val saved = pendingSave!!; pendingSave = null; saving = true; deliver(saved) }) { Text("仍然保存") } },
        dismissButton = { TextButton(onClick = { pendingSave = null }) { Text("返回修改") } })
    if (showTimePicker) CourseTimeDialog(
        initialDay = day,
        initialStart = startSection,
        initialEnd = startSection + duration - 1,
        periodCount = periodCount,
        onConfirm = { newDay, newStart, newEnd ->
            timeReviewed = true
            day = newDay
            startSection = newStart
            duration = newEnd - newStart + 1
        },
        onDismiss = { showTimePicker = false }
    )
    if (showWeeksPicker) CourseWeeksDialog(
        totalWeeks = totalWeeks,
        initialWeeks = selectedWeeks,
        onConfirm = { selectedWeeks = it },
        onDismiss = { showWeeksPicker = false }
    )
    if (embedded) editorContent() else {
        ModalBottomSheet(
            onDismissRequest = { requestDismiss() },
            canDismiss = { if (protectEdits && changed) { requestDismiss(); false } else !saving },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            preferReadability = true,
            shape = RoundedCornerShape(32.dp)
        ) { editorContent() }
    }
}

/**
 * 优雅拟态分组卡片容器
 */
@Composable
private fun EditorSectionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    FormSectionCard(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

/**
 * 课程信息与配色的实时预览，沿用编辑页的正文与辅助文字层级。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CourseCardPreview(
    name: String,
    teacher: String,
    location: String,
    colorLong: Long,
    day: Int,
    startSection: Int,
    duration: Int,
    weeksLabel: String,
    modifier: Modifier = Modifier
) {
    val accent = Color.fromStoredLong(colorLong)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardBg = lerp(MaterialTheme.colorScheme.surface, accent, if (isDark) 0.28f else 0.16f)
    val metadataColor = courseMetadataColor()
    val dayLabel = WeekdayLabels.getOrElse(day - 1) { "一" }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
        border = null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "效果预览",
                    modifier = Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "周$dayLabel · 第${startSection}-${startSection + duration - 1}节 · $weeksLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .heightIn(min = 76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cardBg.copy(alpha = 0.55f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(accent)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (name.isBlank()) "课程名称" else name,
                        color = if (name.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (teacher.isBlank()) "任课教师" else teacher,
                            color = if (teacher.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else metadataColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (location.isBlank()) "上课教室/地点" else location,
                            color = if (location.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else metadataColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
