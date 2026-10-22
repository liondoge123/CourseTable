package com.coursetable.app.ui

import com.coursetable.app.data.weeksLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coursetable.app.CourseApp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException

internal fun reviewQueue(courses: List<CandidateCourse>, settings: AppSettings) =
    courses.filter { it.reviewIssues(settings).isNotEmpty() }
        .sortedBy { if (it.fieldErrors(settings).isNotEmpty()) 0 else 1 }

internal data class ReviewCourseGroup(val label: String, val courses: List<CandidateCourse>)
private enum class ReviewMoreAction { ADD, ORIGINAL, RESELECT }

internal fun groupedReviewCourses(courses: List<CandidateCourse>, settings: AppSettings): List<ReviewCourseGroup> {
    val order = compareBy<CandidateCourse>({ it.startSection }, { it.duration }, { it.name }, { it.draftId.orEmpty() })
    fun CandidateCourse.hasUsablePosition() = dayOfWeek in 1..7 && startSection >= 1 && duration > 0 &&
        startSection.toLong() + duration - 1 <= settings.periods.size
    val buckets = Array(8) { mutableListOf<CandidateCourse>() }
    courses.forEach { buckets[if (it.hasUsablePosition()) it.dayOfWeek else 0].add(it) }
    return buildList {
        buckets[0].sortedWith(order).takeIf { it.isNotEmpty() }
            ?.let { add(ReviewCourseGroup("待修正", it)) }
        val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        weekdays.forEachIndexed { index, label ->
            buckets[index + 1].sortedWith(order)
                .takeIf { it.isNotEmpty() }?.let { add(ReviewCourseGroup(label, it)) }
        }
    }
}

@Composable
private fun ReviewCourseCard(
    course: CandidateCourse,
    settings: AppSettings,
    enabled: Boolean,
    analysis: AnalyzedRow,
    onToggle: () -> Unit,
    onNameAlternative: (OcrAlternative) -> Unit,
    onClick: () -> Unit
) {
    val fieldErrors = course.fieldErrors(settings)
    val status = when {
        fieldErrors.isNotEmpty() -> "需修正"
        analysis.status != ImportStatus.NORMAL -> analysis.status.label
        course.needsReview -> "建议确认"
        else -> analysis.status.label
    }
    val accent = when {
        fieldErrors.isNotEmpty() -> LiquidTheme.colorScheme.error
        analysis.status == ImportStatus.CONFLICT -> LiquidTheme.colorScheme.error
        course.needsReview -> if (LiquidTheme.colorScheme.isDark) Color(0xFFFFB340) else Color(0xFFB26A00)
        else -> LiquidTheme.colorScheme.primary
    }
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrNull(course.dayOfWeek - 1) ?: "星期待确认"
    val section = if (course.startSection >= 1 && course.duration > 0 && course.startSection.toLong() + course.duration - 1 <= settings.periods.size) {
        "第 ${course.startSection}—${course.startSection + course.duration - 1} 节"
    } else "节次待确认"
    SectionFrame(Modifier.testTag("review-course-${course.draftId.orEmpty()}"), containerAlpha = 0.60f) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
        ) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(accent)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(course.name.ifBlank { "课程名称待补齐" }, style = LiquidTheme.typography.titleSmall)
                    Text("$weekday · $section · ${candidateToCourse(course).weeksLabel()}", style = LiquidTheme.typography.bodySmall)
                    listOf(course.teacher, course.location).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }
                        ?.let { Text(it, style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant) }
                    fieldErrors.takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.error)
                    }
                    (course.issues + course.reviewPrompts()).distinct().forEach { Text(it, style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.error) }
                    course.alternatives.filter { it.field == OcrField.NAME }.distinctBy { it.candidate }.forEach { alternative ->
                        TextButton(onClick = { onNameAlternative(alternative) }, enabled = enabled && usableNameAlternative(alternative),
                            contentPadding = PaddingValues(0.dp)) { Text("使用候选：${alternative.candidate}") }
                    }
                    analysis.duplicate?.let { Text("已存在，默认跳过" + if (it.teacher != course.teacher || it.location != course.location) " · 教师或教室有变化" else "", style = LiquidTheme.typography.bodySmall) }
                    if (analysis.requiresTarget) Text("请选择归入已有课程或另建课程", style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.error)
                    if (analysis.conflicts.isNotEmpty()) Text(analysis.conflicts.joinToString("\n") { "冲突：${it.description()}" }, style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.error)
                    if (analysis.duplicate == null && !course.separateCourse) analysis.autoMergeTarget?.let { target -> Text("自动加入「${target.name}」的上课时段", style = LiquidTheme.typography.bodySmall) }
                }
                TextButton(onClick = onToggle, enabled = enabled, contentPadding = PaddingValues(4.dp)) { Text(if (analysis.included) "已选" else "跳过") }
                Box(
                    Modifier
                        .clip(LiquidButtonShape)
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(status, style = LiquidTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** All import adapters edit the same draft; only the final action writes courses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnifiedImportReview(
    session: VisualImportSession?, settings: AppSettings, candidates: List<CandidateCourse>,
    sourceLabel: String, warnings: List<String>, onCandidates: (List<CandidateCourse>) -> Unit,
    onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit, saving: Boolean,
    hasEdits: Boolean, onReselect: (() -> Unit)?,
    onRequest: ((ImportRequest) -> Unit)? = null,
    initialMode: ImportMode = ImportMode.APPEND,
    initialTimetable: com.coursetable.app.data.Timetable? = null,
    fixedNew: Boolean = false
) {
    val app = LocalContext.current.applicationContext as CourseApp
    var targetName by remember { mutableStateOf("当前课表") }
    var existingCount by remember { mutableIntStateOf(0) }
    val existing by remember(app) { app.courseRepository.observeAll() }.collectAsState(emptyList())
    LaunchedEffect(settings.timetableId) {
        targetName = app.timetableRepository.allOnce().firstOrNull { it.id == settings.timetableId }?.name ?: "当前课表"
        existingCount = app.courseRepository.byTimetableOnce(settings.timetableId).size
    }
    LaunchedEffect(candidates) {
        if (candidates.any { it.draftId == null }) onCandidates(candidates.map { if (it.draftId == null) it.copy(draftId = java.util.UUID.randomUUID().toString()) else it })
    }
    val listState = rememberLazyListState()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var addingId by rememberSaveable { mutableStateOf<String?>(null) }
    val adding = addingId?.let { CandidateCourse("", dayOfWeek = 1, startSection = 1, duration = 1, startWeek = 1, endWeek = settings.totalWeeks, weekType = 0, draftId = it) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var moreTriggerHidden by remember { mutableStateOf(false) }
    val moreSource = rememberMenuSource()
    var pendingMoreAction by remember { mutableStateOf<ReviewMoreAction?>(null) }
    var moreAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var original by remember { mutableStateOf(false) }
    var reselect by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var modeName by rememberSaveable { mutableStateOf(initialMode.name) }
    val mode = ImportMode.valueOf(modeName)
    val overwrite = mode == ImportMode.OVERWRITE
    var newName by rememberSaveable { mutableStateOf(initialTimetable?.name ?: "导入的课表") }
    var newStart by rememberSaveable { mutableStateOf((initialTimetable?.semesterStart() ?: settings.semesterStart).toString()) }
    var newWeeks by rememberSaveable { mutableStateOf((initialTimetable?.totalWeeks ?: settings.totalWeeks).toString()) }
    val rows = remember(candidates, existing, settings, mode) { ScheduleAnalysis.analyze(candidates, existing, settings, mode) }
    val presentation = remember(rows) { ImportReviewPresentation(rows) }
    val unresolved = presentation.unresolved
    val selectedErrors = presentation.selectedErrors
    var confirm by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf<Pair<Int, CandidateCourse>?>(null) }
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }
    var retryProposal by remember { mutableStateOf<Pair<CandidateCourse, CandidateCourse>?>(null) }
    var nameProposal by remember { mutableStateOf<Pair<CandidateCourse, OcrAlternative>?>(null) }
    var retryError by remember { mutableStateOf<String?>(null) }
    val errors = presentation.errors.size
    val suggestions = presentation.suggestions.size
    val activeFilter = when {
        filter == 1 && errors == 0 -> 0
        filter == 2 && suggestions == 0 -> 0
        else -> filter
    }
    LaunchedEffect(activeFilter, filter) { if (filter != activeFilter) filter = activeFilter }
    val groups = remember(presentation, activeFilter, settings) {
        val visible = when (activeFilter) { 1 -> presentation.errors; 2 -> presentation.suggestions; else -> presentation.courses }
        groupedReviewCourses(visible, settings)
    }
    fun dismiss() { if (!saving) { if (hasEdits) leave = true else onDismiss() } }
    fun startQueue() { queue = true; selected = presentation.queue.firstOrNull()?.draftId }
    fun update(c: CandidateCourse, replacement: CandidateCourse?) {
        val index = candidates.indexOfFirst { it.draftId == c.draftId }
        if (index < 0) return
        val next = candidates.toMutableList()
        if (replacement == null) { deleted = index to c; next.removeAt(index) } else next[index] = replacement
        onCandidates(next)
        selected = if (queue) reviewQueue(next, settings).firstOrNull()?.draftId else null
    }
    fun updateReviewOptions(c: CandidateCourse, replacement: CandidateCourse) {
        onCandidates(candidates.map { if (it.draftId == c.draftId) replacement else it })
    }
    val editing = adding ?: candidates.firstOrNull { it.draftId != null && it.draftId == selected }
    val editor: @Composable (Boolean) -> Unit = { embedded -> editing?.let { c -> key(c.draftId) {
        CourseEditorDialog(course = candidateToCourse(c, settings.timetableId), totalWeeks = settings.totalWeeks, periodCount = settings.periods.size.coerceAtLeast(1), title = if (adding != null) "添加课程" else "校对课程", embedded = embedded, protectEdits = true, reviewHints = c.fieldErrors(settings), saveLabel = if (adding != null) "添加课程" else if(queue) "保存并校对下一条" else "保存校对", showColorPicker = false, pinSourceContent = adding == null,
            sourceContent = if (adding != null) null else ({
                if (session != null) ImportCourseSource(session, c, settings, compact = true)
                else Text("来源：$sourceLabel", style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
            }), reviewContent = if (adding != null) null else ({
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val row = presentation.rowForDraft(c.draftId)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (row != null) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(checked = row.included, onCheckedChange = if (saving || retrying) null else ({ included -> updateReviewOptions(c, c.copy(included = included)) }),
                                modifier = Modifier.testTag("review-record-included"))
                            Text("导入此记录", style = LiquidTheme.typography.bodyMedium)
                        }
                        if (session != null) TextButton(onClick = { scope.launch {
                            retrying = true
                            try {
                                val region = session.result.regions.firstOrNull { it.id in c.sourceRegionIds() } ?: error("没有原图区域")
                                val page = session.pages.first { it.index == region.page }
                                val found = withContext(Dispatchers.IO) { VisualTimetableImporter.recognizeRegion(page, region, settings) }
                                val best = found.minByOrNull { kotlin.math.abs(it.startSection - c.startSection) + if(it.dayOfWeek == c.dayOfWeek) 0 else 30 } ?: error("此区域没有识别到记录，请手动补齐")
                                retryProposal = c to best
                            } catch(e: CancellationException) { throw e } catch(e: Exception) { retryError = e.message } finally { retrying = false }
                        } }, enabled = !retrying && !saving, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(if (retrying) "识别中…" else "重新识别", style = LiquidTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                    if (row != null) {
                        row.duplicate?.let { duplicate ->
                            Text("原教师：${duplicate.teacher} · 原教室：${duplicate.location}")
                            Text("更新教师会影响整门课程，教室只影响此时段。", style = LiquidTheme.typography.bodySmall)
                            TextButton(onClick = { updateReviewOptions(c, c.copy(included = true, updateDuplicate = true)) }) { Text("更新教师和本时段教室") }
                        }
                        if (row.requiresTarget || c.targetCourseId != null || c.separateCourse) {
                            row.mergeTargets.forEach { target -> TextButton(onClick = { updateReviewOptions(c, c.copy(targetCourseId = target.courseId, separateCourse = false, included = true)) }) { Text("归入 ${target.name} · ${target.teacher.ifBlank { "未填教师" }}${if(c.targetCourseId == target.courseId) " ✓" else ""}") } }
                            TextButton(onClick = { updateReviewOptions(c, c.copy(separateCourse = true, targetCourseId = null, included = true)) }) { Text("另建独立课程") }
                        }
                    }
                    val notes = (c.issues + c.reviewPrompts()).distinct().filterNot { it in c.fieldErrors(settings) || it.startsWith("周次缺失") || it.startsWith("周次无法解析") || it == "请对照原图核对课程信息" || c.alternatives.any { alternative -> alternative.reason == it } }
                    var showNotes by remember(c.draftId) { mutableStateOf(false) }
                    if (notes.isNotEmpty() || c.alternatives.isNotEmpty()) {
                        TextButton(onClick = { showNotes = !showNotes }, contentPadding = PaddingValues(0.dp)) {
                            Text("识别提示 ${notes.size + c.alternatives.size} · ${if (showNotes) "收起" else "展开"}", style = LiquidTheme.typography.bodySmall)
                        }
                    }
                    if (showNotes) {
                    notes.forEach { Text(it, style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant) }
                    c.alternatives.forEach { alternative ->
                        Text("${alternative.reason}：${alternative.original} / ${alternative.candidate}", style = LiquidTheme.typography.bodySmall)
                        if (session != null && alternative.field != OcrField.NAME) TextButton(onClick = { scope.launch {
                            retrying = true
                            try {
                                val region = session.result.regions.firstOrNull { it.id in c.sourceRegionIds() } ?: error("没有原图区域")
                                val page = session.pages.first { it.index == region.page }
                                val found = withContext(Dispatchers.IO) { VisualTimetableImporter.applyAlternative(page, region, settings, alternative) }
                                val best = found.minByOrNull { kotlin.math.abs(it.startSection - c.startSection) + if(it.dayOfWeek == c.dayOfWeek) 0 else 30 } ?: error("候选文字无法解析，请手动修改")
                                retryProposal = c to best
                            } catch(e: CancellationException) { throw e } catch(e: Exception) { retryError = e.message } finally { retrying = false }
                        } }, enabled = !retrying && !saving) { Text("使用候选：${alternative.candidate}") }
                    }
                    }
                    retryError?.let { Text(it, color = LiquidTheme.colorScheme.error) }
                    if(queue) Text("待校对 ${presentation.queue.size} 条", style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
                }
            }), onDismiss = { addingId = null; selected = null }, onSave = { saved ->
                val next = c.copy(name = saved.name, teacher = saved.teacher, location = saved.location, dayOfWeek = saved.dayOfWeek, startSection = saved.startSection, duration = saved.duration, startWeek = saved.startWeek, endWeek = saved.endWeek, weekType = saved.weekType, selectedWeeksCsv = saved.selectedWeeksCsv, needsReview = false, reviewFields = emptySet(), alternatives = emptyList(), issues = emptyList(), editedFields = c.editedFields + listOf("name", "teacher", "location", "dayOfWeek", "startSection", "duration", "weeks").filter { field -> when(field) { "name" -> saved.name != c.name; "teacher" -> saved.teacher != c.teacher; "location" -> saved.location != c.location; "dayOfWeek" -> saved.dayOfWeek != c.dayOfWeek; "startSection" -> saved.startSection != c.startSection; "duration" -> saved.duration != c.duration; else -> saved.selectedWeeksCsv != c.selectedWeeksCsv || saved.startWeek != c.startWeek || saved.endWeek != c.endWeek || saved.weekType != c.weekType } })
                if(adding != null) { onCandidates(candidates + next); addingId = null } else update(c, next)
            }, onDelete = if (adding != null) null else ({ update(c, null) }))
    } } }
    val reviewBack = rememberPredictiveBack(
        enabled = selected == null && adding == null && nameProposal == null && !original && !more && !confirm && !leave && !reselect,
        canCommit = { !saving && !hasEdits },
        onRejected = { dismiss() },
        onBack = { dismiss() }
    )
    FullscreenPageContainer(Modifier.testTag("import-review-page").predictiveBackTransform(reviewBack, BackPresentation.PAGE)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = { dismiss() }, enabled = !saving && editing == null) { Text("返回") }
                Column(Modifier.weight(1f).padding(8.dp)) {
                    Text("导入确认", style = LiquidTheme.typography.titleLarge)
                    Text(sourceLabel, style = LiquidTheme.typography.bodySmall)
                }
                Box {
                    TextButton(
                        onClick = { more = true },
                        enabled = !saving && editing == null,
                        modifier = Modifier.graphicsLayer { alpha = if (moreTriggerHidden) 0f else 1f }
                            .onGloballyPositioned { moreAnchorBounds = it.boundsInRoot() }
                    ) { Text("更多", Modifier.captureMenuSource(moreSource, moreTriggerHidden)) }
                    DropdownMenu(
                        expanded = more,
                        onDismissRequest = { more = false },
                        anchorBounds = moreAnchorBounds,
                        alignment = DropdownMenuAlignment.END,
                        sourceContent = moreSource,
                        onSourceCovered = { moreTriggerHidden = true },
                        onClosed = {
                            moreTriggerHidden = false
                            when (pendingMoreAction) {
                                ReviewMoreAction.ADD -> {
                                    queue = false
                                    addingId = java.util.UUID.randomUUID().toString()
                                }
                                ReviewMoreAction.ORIGINAL -> original = true
                                ReviewMoreAction.RESELECT -> if (hasEdits) reselect = true else onReselect?.invoke()
                                null -> Unit
                            }
                            pendingMoreAction = null
                        }
                    ) {
                        DropdownMenuItem(
                            text = { Text("添加课程", Modifier.fillMaxWidth()) },
                            onClick = {
                                pendingMoreAction = ReviewMoreAction.ADD
                                more = false
                            }
                        )
                        if (session != null) {
                            DropdownMenuItem(
                                text = { Text("查看原图", Modifier.fillMaxWidth()) },
                                onClick = { pendingMoreAction = ReviewMoreAction.ORIGINAL; more = false }
                            )
                        }
                        if (onReselect != null) {
                            DropdownMenuItem(
                                text = { Text("调整识别范围", Modifier.fillMaxWidth()) },
                                onClick = {
                                    pendingMoreAction = ReviewMoreAction.RESELECT
                                    more = false
                                }
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = buildList {
                        add(0 to "全部 ${candidates.size}")
                        if (errors > 0) add(1 to "需修正 $errors")
                        if (suggestions > 0) add(2 to "建议确认 $suggestions")
                    }
                    filters.forEach { (id, label) ->
                        OptionChip(activeFilter == id, { filter = id }, label, large = true)
                    }
                }
                if (errors + suggestions > 0) TextButton(onClick = ::startQueue, enabled = !saving && editing == null) { Text("开始校对") }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).testTag("import-review-content"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (groups.isEmpty()) item { Text(if (candidates.isEmpty()) "暂无课程，请从更多操作添加或重新识别" else "没有符合条件的记录", Modifier.padding(16.dp)) }
                groups.forEach { group ->
                    item("header-${group.label}") {
                        Row(Modifier.fillMaxWidth().padding(start = 2.dp, top = 4.dp, end = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(group.label, style = LiquidTheme.typography.labelLarge, color = LiquidTheme.colorScheme.onSurfaceVariant)
                            Text("${group.courses.size} 条", style = LiquidTheme.typography.labelSmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(group.courses) { c ->
                        val row = presentation.rowFor(c)
                        ReviewCourseCard(c, settings, !saving && editing == null, row,
                            { onCandidates(candidates.map { if (it.draftId == c.draftId) it.copy(included = !row.included) else it }) },
                            { alternative -> nameProposal = c to alternative }) { queue = false; selected = c.draftId }
                    }
                }
                if (warnings.isNotEmpty()) item("warnings") {
                    SectionFrame(containerAlpha = 0.60f) { Text(warnings.joinToString("\n"), Modifier.padding(12.dp), style = LiquidTheme.typography.bodySmall) }
                }
            }
            deleted?.let { (index, c) -> Row(Modifier.padding(horizontal = 16.dp)) {
                Text("已删除 ${c.name}", Modifier.weight(1f))
                TextButton(onClick = { onCandidates(candidates.toMutableList().apply { add(index.coerceAtMost(size), c) }); deleted = null }, enabled = !saving && editing == null) { Text("撤销") }
            } }
            SectionFrame(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("review-import-bar"), containerAlpha = 0.60f) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compactBar = this.maxWidth < 400.dp
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val actionLabel = if (saving) "正在导入…" else if (editing != null) "请先保存校对" else if (selectedErrors + unresolved > 0) "处理 ${selectedErrors + unresolved} 条问题" else "导入 ${presentation.included} 条记录"
                    val destinationLabel = if (compactBar) {
                        "${mode.label} ▾"
                    } else {
                        "$targetName · ${mode.label} ▾"
                    }
                    val action: () -> Unit = { if (selectedErrors > 0) startQueue() else if (unresolved > 0) { queue = false; selected = rows.first { it.requiresTarget }.candidate.draftId } else confirm = true }
                    TextButton(
                        onClick = { if (!fixedNew) modeName = ImportMode.entries[(mode.ordinal + 1) % ImportMode.entries.size].name },
                        enabled = !saving && editing == null,
                        modifier = Modifier.weight(0.42f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(destinationLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = action,
                        enabled = candidates.isNotEmpty() && !saving && editing == null,
                        modifier = Modifier.weight(0.58f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) { Text(actionLabel, maxLines = 1) }
                }
                }
            }
        }
        if (wide && editing != null) SectionFrame(Modifier.width(380.dp).fillMaxHeight().padding(12.dp), containerAlpha = 0.20f) { editor(true) }
        }
        if (!wide && editing != null) editor(false)
        }
    }
    if (original && session != null) ImportSourceViewer(session.sourceImage?.let { listOf(it.preview) } ?: session.pages.map { it.image }, "完整原图", { original = false })
    nameProposal?.let { (course, alternative) ->
        val related = relatedNameAlternativeRecords(candidates, course, alternative)
        val current = related.isNotEmpty()
        AlertDialog(onDismissRequest = { nameProposal = null }, title = { Text("确认课程名") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${course.name} → ${alternative.candidate}")
                if (!current) Text("记录已变化，请取消后重新选择候选。")
                else if (related.size > 1) Text("可一并确认同一来源的 ${related.size} 条上课记录。")
            } },
            confirmButton = { TextButton(enabled = current, onClick = {
                onCandidates(applyNameAlternativeRecords(candidates, listOf(course), alternative)); nameProposal = null
            }) { Text("采用候选课程名") } },
            additionalActions = if (related.size <= 1) emptyList() else listOf(DialogAction("同时应用到 ${related.size} 条记录", {
                onCandidates(applyNameAlternativeRecords(candidates, related, alternative)); nameProposal = null
            })),
            dismissButton = { TextButton(onClick = { nameProposal = null }) { Text("取消") } })
    }
    retryProposal?.let { (original, recognized) ->
        val others = original.sourceRecords.filterNot { record -> record.sourceRegion == recognized.sourceRegion }
        val merged = ImageTimetableOcr.mergeAcrossPages(others + recognized).firstOrNull { it.dayOfWeek == recognized.dayOfWeek && it.startSection == recognized.startSection } ?: recognized
        AlertDialog(onDismissRequest = { retryProposal = null }, title = { Text("确认识别差异") }, text = {
            Text(reviewDifferences(original, merged).joinToString("\n").ifEmpty { "字段没有变化" } + if(original.editedFields.isNotEmpty()) "\n保留已人工修改的字段。" else "")
        }, confirmButton = { TextButton(onClick = { update(original, preserveReviewedFields(original, merged)); retryProposal = null }) { Text("保留人工修改并应用") } },
            additionalActions = if(original.editedFields.isEmpty()) emptyList() else listOf(DialogAction("采用识别结果覆盖人工修改", { update(original, preserveReviewedFields(original.copy(editedFields = emptySet()), merged)); retryProposal = null })),
            dismissButton = { TextButton(onClick = { retryProposal = null }) { Text("取消") } })
    }
    if (reselect) ModalBottomSheet(onDismissRequest = { reselect = false }) {
        val dismissController = LocalDialogDismissController.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("重新识别？", style = LiquidTheme.typography.titleLarge)
            Text("本次已修改、添加或删除的记录将被放弃。", style = LiquidTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { dismissController?.dismiss { reselect = false } ?: run { reselect = false } }, modifier = Modifier.weight(1f)) { Text("继续校对") }
                DestructiveButton(onClick = { dismissController?.dismiss { reselect = false; onReselect?.invoke() } ?: run { reselect = false; onReselect?.invoke() } }, modifier = Modifier.weight(1f)) { Text("放弃修改并继续") }
            }
        }
    }
    if (leave) AlertDialog(confirmButtonRole = DialogActionRole.Destructive, onDismissRequest = { leave = false },
        title = { Text("放弃本次导入？") }, text = { Text("本次修改不会保存。") },
        confirmButton = { TextButton(onClick = { onDismiss(); leave = false }) { Text("放弃导入", maxLines = 1) } },
        dismissButton = { TextButton(onClick = { leave = false }) { Text("继续校对", maxLines = 1) } })
    if (confirm) AlertDialog(confirmButtonRole = if (overwrite) DialogActionRole.Destructive else DialogActionRole.Primary,
        onDismissRequest = { confirm = false }, title = { Text("确认导入") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${mode.label}「${if(mode == ImportMode.NEW) newName else targetName}」：选择 ${presentation.included} 条记录。" +
                    (if(overwrite) "将删除现有 ${existing.size} 条上课记录。" else "") +
                    (if(presentation.hasConflicts) "存在时间冲突，仍可保存。" else "") +
                    (if(suggestions > 0) "仍有 $suggestions 条识别建议确认。" else ""))
                if (mode == ImportMode.NEW) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("课表名称") })
                    OutlinedTextField(newStart, { newStart = it }, label = { Text("学期开始日期 YYYY-MM-DD") })
                    OutlinedTextField(newWeeks, { newWeeks = it }, label = { Text("学期总周数") })
                    Text("节次作息沿用${if(initialTimetable != null) "备份" else "当前课表"}。", style = LiquidTheme.typography.bodySmall)
                }
            }
        }, confirmButton = { TextButton(onClick = {
            val table = if (mode == ImportMode.NEW) (initialTimetable ?: com.coursetable.app.data.Timetable(name = newName,
                periodsCsv = com.coursetable.app.data.Timetable.serializePeriods(settings.periods), periodDurationMinutes = settings.periodDurationMinutes))
                .copy(name = newName.trim(), semesterStartEpochDay = java.time.LocalDate.parse(newStart).toEpochDay(), totalWeeks = newWeeks.toInt()) else null
            confirm = false
            if (onRequest != null) onRequest(ImportRequest(mode, candidates, existing, table)) else onConfirm(overwrite)
        }, enabled = !saving && editing == null && (mode != ImportMode.NEW || newName.isNotBlank() && runCatching { java.time.LocalDate.parse(newStart) }.isSuccess && newWeeks.toIntOrNull() in 1..60 && candidates.filterIndexed { index, _ -> rows[index].included }.all { it.endWeek <= (newWeeks.toIntOrNull() ?: 0) })) {
            Text(if (overwrite) "覆盖并导入" else if(presentation.hasConflicts) "仍然导入" else "确认导入")
        } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("返回修改") } })

}
