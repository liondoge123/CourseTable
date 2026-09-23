package com.coursetable.app.ui

import androidx.activity.compose.BackHandler
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

internal fun reviewQueue(courses: List<CandidateCourse>, settings: AppSettings) =
    courses.filter { it.reviewIssues(settings).isNotEmpty() }
        .sortedBy { if (it.fieldErrors(settings).isNotEmpty()) 0 else 1 }

internal data class ReviewCourseGroup(val label: String, val courses: List<CandidateCourse>)
private enum class ReviewMoreAction { ADD, ORIGINAL, RESELECT }

internal fun groupedReviewCourses(courses: List<CandidateCourse>, settings: AppSettings): List<ReviewCourseGroup> {
    val order = compareBy<CandidateCourse>({ it.startSection }, { it.duration }, { it.name }, { it.draftId.orEmpty() })
    fun CandidateCourse.hasUsablePosition() = dayOfWeek in 1..7 && startSection >= 1 && duration > 0 &&
        startSection.toLong() + duration - 1 <= settings.periods.size
    return buildList {
        courses.filterNot { it.hasUsablePosition() }.sortedWith(order).takeIf { it.isNotEmpty() }
            ?.let { add(ReviewCourseGroup("待修正", it)) }
        val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        weekdays.forEachIndexed { index, label ->
            courses.filter { it.hasUsablePosition() && it.dayOfWeek == index + 1 }.sortedWith(order)
                .takeIf { it.isNotEmpty() }?.let { add(ReviewCourseGroup(label, it)) }
        }
    }
}

@Composable
private fun ReviewCourseCard(
    course: CandidateCourse,
    settings: AppSettings,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val fieldErrors = course.fieldErrors(settings)
    val status = when {
        fieldErrors.isNotEmpty() -> "需修正"
        course.needsReview -> "建议确认"
        else -> "已识别"
    }
    val accent = when {
        fieldErrors.isNotEmpty() -> LiquidTheme.colorScheme.error
        course.needsReview -> if (LiquidTheme.colorScheme.isDark) Color(0xFFFFB340) else Color(0xFFB26A00)
        else -> LiquidTheme.colorScheme.primary
    }
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrNull(course.dayOfWeek - 1) ?: "星期待确认"
    val section = if (course.startSection >= 1 && course.duration > 0 && course.startSection.toLong() + course.duration - 1 <= settings.periods.size) {
        "第 ${course.startSection}—${course.startSection + course.duration - 1} 节"
    } else "节次待确认"
    val weekRule = when (course.weekType) { 1 -> " · 单周"; 2 -> " · 双周"; else -> "" }
    SectionFrame(Modifier.testTag("review-course-${course.draftId.orEmpty()}")) {
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
                    Text("$weekday · $section · ${course.startWeek}—${course.endWeek} 周$weekRule", style = LiquidTheme.typography.bodySmall)
                    listOf(course.teacher, course.location).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }
                        ?.let { Text(it, style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant) }
                    fieldErrors.takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.error)
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
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
    hasEdits: Boolean, onReselect: (() -> Unit)?
) {
    val app = LocalContext.current.applicationContext as CourseApp
    var targetName by remember { mutableStateOf("当前课表") }
    var existingCount by remember { mutableIntStateOf(0) }
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
    var pendingMoreAction by remember { mutableStateOf<ReviewMoreAction?>(null) }
    var moreAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var original by remember { mutableStateOf(false) }
    var reselect by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var overwrite by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf<Pair<Int, CandidateCourse>?>(null) }
    val errors = candidates.count { it.fieldErrors(settings).isNotEmpty() }
    val suggestions = candidates.count { it.fieldErrors(settings).isEmpty() && it.needsReview }
    val activeFilter = when {
        filter == 1 && errors == 0 -> 0
        filter == 2 && suggestions == 0 -> 0
        else -> filter
    }
    LaunchedEffect(activeFilter, filter) { if (filter != activeFilter) filter = activeFilter }
    fun dismiss() { if (!saving) { if (hasEdits) leave = true else onDismiss() } }
    fun startQueue() { queue = true; selected = reviewQueue(candidates, settings).firstOrNull()?.draftId }
    fun update(c: CandidateCourse, replacement: CandidateCourse?) {
        val index = candidates.indexOfFirst { it.draftId == c.draftId }
        if (index < 0) return
        val next = candidates.toMutableList()
        if (replacement == null) { deleted = index to c; next.removeAt(index) } else next[index] = replacement
        onCandidates(next)
        selected = if (queue) reviewQueue(next, settings).firstOrNull()?.draftId else null
    }
    val editing = adding ?: candidates.firstOrNull { it.draftId != null && it.draftId == selected }
    val editor: @Composable (Boolean) -> Unit = { embedded -> editing?.let { c -> key(c.draftId) {
        CourseEditorDialog(course = candidateToCourse(c, settings.timetableId), totalWeeks = settings.totalWeeks, periodCount = settings.periods.size.coerceAtLeast(1), title = if (adding != null) "添加课程" else "校对课程", embedded = embedded, protectEdits = true, reviewHints = c.fieldErrors(settings), saveLabel = if (adding != null) "添加课程" else if(queue) "保存并校对下一条" else "保存校对", showColorPicker = false, pinSourceContent = adding == null,
            sourceContent = if (adding != null) null else ({
                Column {
                    if(queue) Text("剩余 ${reviewQueue(candidates, settings).size} 条 · 保存后校对下一条", style = LiquidTheme.typography.bodySmall)
                    c.reviewIssues(settings)
                        .filterNot { it == "请对照原图核对课程信息" }
                        .forEach { Text("! $it", style = LiquidTheme.typography.bodySmall, color = if(c.fieldErrors(settings).isNotEmpty()) LiquidTheme.colorScheme.error else LiquidTheme.colorScheme.onSurfaceVariant) }
                    if(session != null) ImportCourseSource(session, c, settings, compact = true) else Text("来源：$sourceLabel", style = LiquidTheme.typography.bodySmall)
                }
            }), onDismiss = { addingId = null; selected = null }, onSave = { saved ->
                val next = c.copy(name = saved.name, teacher = saved.teacher, location = saved.location, dayOfWeek = saved.dayOfWeek, startSection = saved.startSection, duration = saved.duration, startWeek = saved.startWeek, endWeek = saved.endWeek, weekType = saved.weekType, needsReview = false)
                if(adding != null) { onCandidates(candidates + next); addingId = null } else update(c, next)
            }, onDelete = if (adding != null) null else ({ update(c, null) }))
    } } }
    BackHandler(selected == null && adding == null && !original && !more && !confirm && !leave && !reselect) { dismiss() }
    FullscreenPageContainer(Modifier.testTag("import-review-page")) {
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
                        modifier = Modifier.onGloballyPositioned { moreAnchorBounds = it.boundsInRoot() }
                    ) { Text("更多") }
                    DropdownMenu(
                        expanded = more,
                        onDismissRequest = { more = false },
                        anchorBounds = moreAnchorBounds,
                        alignment = DropdownMenuAlignment.END,
                        onClosed = {
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
            val visible = candidates.filter { when (activeFilter) { 1 -> it.fieldErrors(settings).isNotEmpty(); 2 -> it.fieldErrors(settings).isEmpty() && it.needsReview; else -> true } }
            val groups = groupedReviewCourses(visible, settings)
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
                        ReviewCourseCard(c, settings, !saving && editing == null) { queue = false; selected = c.draftId }
                    }
                }
                if (warnings.isNotEmpty()) item("warnings") {
                    SectionFrame { Text(warnings.joinToString("\n"), Modifier.padding(12.dp), style = LiquidTheme.typography.bodySmall) }
                }
            }
            deleted?.let { (index, c) -> Row(Modifier.padding(horizontal = 16.dp)) {
                Text("已删除 ${c.name}", Modifier.weight(1f))
                TextButton(onClick = { onCandidates(candidates.toMutableList().apply { add(index.coerceAtMost(size), c) }); deleted = null }, enabled = !saving && editing == null) { Text("撤销") }
            } }
            SectionFrame(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("review-import-bar")) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compactBar = this.maxWidth < 400.dp
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val actionLabel = if (saving) "正在导入…" else if (editing != null) "请先保存校对" else if (errors > 0) "处理 $errors 条必修正项" else "导入 ${candidates.size} 条记录"
                    val destinationLabel = if (compactBar) {
                        "${if (overwrite) "覆盖 $existingCount 条" else "追加"} ▾"
                    } else {
                        "$targetName · ${if (overwrite) "覆盖 $existingCount 条" else "追加"} ▾"
                    }
                    val action: () -> Unit = { if (errors > 0) startQueue() else confirm = true }
                    TextButton(
                        onClick = { overwrite = !overwrite },
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
        if (wide && editing != null) SectionFrame(Modifier.width(380.dp).fillMaxHeight().padding(12.dp)) { editor(true) }
        }
        if (!wide && editing != null) editor(false)
        }
    }
    if (original && session != null) ImportSourceViewer(session.sourceImage?.let { listOf(it.preview) } ?: session.pages.map { it.image }, "完整原图", { original = false })
    if (reselect) ModalBottomSheet(onDismissRequest = { reselect = false }) {
        val dismissController = LocalDialogDismissController.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("重新识别？", style = LiquidTheme.typography.titleLarge)
            Text("本次已修改、添加或删除的记录将被放弃。", style = LiquidTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { dismissController?.dismiss { reselect = false } ?: run { reselect = false } }, modifier = Modifier.weight(1f)) { Text("继续校对") }
                Button(onClick = { dismissController?.dismiss { reselect = false; onReselect?.invoke() } ?: run { reselect = false; onReselect?.invoke() } }, modifier = Modifier.weight(1f)) { Text("放弃修改并继续") }
            }
        }
    }
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("放弃本次导入？") }, text = { Text("本次已修改、添加或删除的记录将被放弃。") }, confirmButton = { TextButton(onClick = { onDismiss(); leave = false }) { Text("放弃修改并继续") } }, dismissButton = { TextButton(onClick = { leave = false }) { Text("继续校对") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("确认导入") }, text = { Text("${if(overwrite) "覆盖" else "追加到"}「$targetName」：${candidates.size} 条记录。" + (if (overwrite) "将替换现有 $existingCount 条记录。" else "") + (if(suggestions > 0) "仍有 $suggestions 条建议确认，可返回校对或继续导入。" else "")) }, confirmButton = { TextButton(onClick = { confirm = false; onConfirm(overwrite) }, enabled = !saving && editing == null) { Text(if (overwrite) "覆盖并导入" else "确认导入") } }, dismissButton = { TextButton(onClick = { confirm = false; if(suggestions > 0) startQueue() }) { Text("返回校对") } })

}
