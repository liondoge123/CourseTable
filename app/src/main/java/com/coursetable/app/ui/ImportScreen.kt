package com.coursetable.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.coursetable.app.CourseApp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.TimetableRepository
import com.coursetable.app.data.WeekType
import com.coursetable.app.importer.CandidateCourse
import com.coursetable.app.importer.BackupData
import com.coursetable.app.importer.BackupManager
import com.coursetable.app.importer.ExcelTimetableImporter
import com.coursetable.app.importer.IcsImporter
import com.coursetable.app.importer.IcsParser
import com.coursetable.app.importer.PdfParseResult
import com.coursetable.app.importer.VisualTimetableImporter
import com.coursetable.app.importer.VisualImportSession
import com.coursetable.app.importer.PreparedImportImage
import com.coursetable.app.importer.ImageSelection
import com.coursetable.app.importer.ImageImportPreparation
import com.coursetable.app.importer.fieldErrors
import com.coursetable.app.importer.reviewIssues
import kotlinx.coroutines.CancellationException
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseColorPalette
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import com.coursetable.app.ui.theme.toArgbLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val WEEKDAY_NAMES2 = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** Close a completed result if cancellation prevents ownership reaching Compose. */
private suspend fun <T : java.io.Closeable> loadImportResource(producer: suspend () -> T): T {
    var resource: T? = null
    try {
        return withContext(Dispatchers.IO) { producer().also { resource = it } }
    } catch (e: Throwable) {
        resource?.close()
        throw e
    }
}

data class IncomingFile(val uri: Uri, val mimeType: String?) {
    companion object {
        fun fromIntent(intent: android.content.Intent?): IncomingFile? {
            if (intent == null) return null
            return when (intent.action) {
                android.content.Intent.ACTION_SEND -> {
                    @Suppress("DEPRECATION")
                    val uri = intent.getParcelableExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM) as? Uri
                    if (uri != null) IncomingFile(uri, intent.type) else null
                }
                android.content.Intent.ACTION_VIEW -> {
                    intent.data?.let { IncomingFile(it, intent.type) }
                }
                else -> null
            }
        }
    }
}

private val EXCEL_MIMES = setOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "text/csv",
    "text/comma-separated-values"
)

internal sealed class ImportPreview {
    data class Ics(val courses: List<Course>, val warnings: List<String>) : ImportPreview()
    data class Backup(val data: BackupData) : ImportPreview()
    data class Pdf(val result: PdfParseResult, val sourceLabel: String = "PDF 课表") : ImportPreview()
}

internal class ImportFlowState : androidx.lifecycle.ViewModel() {
    val busy = mutableStateOf(false)
    val pendingPreview = mutableStateOf<ImportPreview?>(null)
    val overwriteMode = mutableStateOf(false)
    val previewPath = mutableStateOf<String?>(null)
    val reviewCandidates = mutableStateOf<List<CandidateCourse>>(emptyList())
    val candidatesEdited = mutableStateOf(false)
    val visualSession = mutableStateOf<VisualImportSession?>(null)
    val preparedImage = mutableStateOf<PreparedImportImage?>(null)
    val autoRecognizing = mutableStateOf(false)
    val choosingImage = mutableStateOf(false)
    val imageSelection = mutableStateOf(ImageSelection())
    val selectionError = mutableStateOf<String?>(null)
    val reviewDirty = mutableStateOf(false)
    val completed = mutableStateOf(false)
    fun clearDraft() {
        visualSession.value?.close(); visualSession.value = null
        preparedImage.value?.close(); preparedImage.value = null
        pendingPreview.value = null
        reviewCandidates.value = emptyList()
        reviewDirty.value = false
    }
    override fun onCleared() {
        visualSession.value?.close()
        preparedImage.value?.close()
    }
}

@Composable
fun ImportScreen(
    incoming: IncomingFile? = null,
    onConsumed: () -> Unit = {},
    initialEntry: ImportEntry = ImportEntry.HUB,
    onBack: (() -> Unit)? = null,
    bottomContentPadding: Dp = 0.dp,
    onSubpageChanged: (Boolean) -> Unit = {},
    onImported: () -> Unit = {},
    recognizeImage: suspend (android.content.Context, PreparedImportImage, ImageSelection, AppSettings) -> VisualImportSession = ImageImportPreparation::recognize
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as CourseApp }
    val repo = remember { app.courseRepository }
    val settingsRepo = remember { app.settingsRepository }
    val timetableRepo = remember { app.timetableRepository }
    val flowState: ImportFlowState = androidx.lifecycle.viewmodel.compose.viewModel()
    val scope = flowState.viewModelScope

    val settingsState = remember(settingsRepo) {
        settingsRepo.settings.stateIn(scope, SharingStarted.WhileSubscribed(5000), AppSettings())
    }
    val settings by settingsState.collectAsState()

    LaunchedEffect(flowState.completed.value) {
        if (flowState.completed.value) { flowState.completed.value = false; onImported() }
    }
    var busy by flowState.busy
    var pendingPreview by flowState.pendingPreview
    var overwriteMode by flowState.overwriteMode
    var previewPath by flowState.previewPath
    var reviewCandidates by flowState.reviewCandidates
    var candidatesEdited by flowState.candidatesEdited
    var visualSession by flowState.visualSession
    var preparedImage by flowState.preparedImage
    var autoRecognizing by flowState.autoRecognizing
    var choosingImage by flowState.choosingImage
    var imageSelection by flowState.imageSelection
    var selectionError by flowState.selectionError
    var reviewDirty by flowState.reviewDirty
    var showEduImport by androidx.compose.runtime.saveable.rememberSaveable(initialEntry) { mutableStateOf(initialEntry == ImportEntry.EDU) }
    var entryHandled by androidx.compose.runtime.saveable.rememberSaveable(initialEntry) { mutableStateOf(false) }

    val imagePreviewOpen = pendingPreview != null && pendingPreview !is ImportPreview.Backup && !choosingImage
    LaunchedEffect(showEduImport, imagePreviewOpen) {
        onSubpageChanged(showEduImport || imagePreviewOpen)
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    suspend fun handleUri(uri: Uri, mimeType: String?) {
        if (busy || autoRecognizing || choosingImage || pendingPreview != null) {
            toast("请先完成或取消当前导入")
            return
        }
        reviewDirty = false
        val mime = mimeType ?: context.contentResolver.getType(uri)
        val path = uri.lastPathSegment.orEmpty().lowercase()
        busy = true
        try {
            when {
                mime == "text/calendar" || mime == "text/vcs" || path.endsWith(".ics") -> {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content.isNullOrBlank()) {
                        toast("文件为空")
                    } else {
                        val parsed = IcsParser.parse(content)
                        if (parsed.events.isEmpty()) {
                            toast("没有找到可导入的课程日程（${parsed.warnings.joinToString("；").ifBlank { "格式不受支持" }}）")
                        } else {
                            val outcome = IcsImporter.convert(parsed.events, settings)
                            reviewCandidates = outcome.courses.map { courseToCandidate(it).copy(draftId = java.util.UUID.randomUUID().toString()) }
                            pendingPreview = ImportPreview.Ics(outcome.courses, parsed.warnings + outcome.warnings)
                            overwriteMode = false
                        }
                    }
                }
                mime == "application/json" || path.endsWith(".json") -> {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content.isNullOrBlank()) {
                        toast("文件为空")
                    } else {
                        val data = BackupManager.parse(content)
                        if (data.timetables.isEmpty()) {
                            toast(data.warnings.joinToString("\n").ifBlank { "备份文件无效" })
                        } else {
                            pendingPreview = ImportPreview.Backup(data)
                            overwriteMode = false
                        }
                    }
                }
                mime == "application/pdf" || path.endsWith(".pdf") -> {
                    val session = loadImportResource {
                        VisualTimetableImporter.open(context, uri, true, settings)
                    }
                    visualSession = session
                    reviewCandidates = session.result.candidates.map { it.copy(draftId = java.util.UUID.randomUUID().toString()) }
                    candidatesEdited = true
                    pendingPreview = ImportPreview.Pdf(session.result)
                    previewPath = session.path
                    overwriteMode = false
                }
                mime?.startsWith("image/") == true || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") -> {
                    preparedImage = loadImportResource { ImageImportPreparation.prepare(context, uri) }
                    imageSelection = ImageSelection()
                    selectionError = null
                    autoRecognizing = true
                    reviewDirty = false
                    overwriteMode = false
                }
                mime in EXCEL_MIMES || path.endsWith(".csv") || path.endsWith(".xlsx") || path.endsWith(".xls") -> {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes == null) {
                        toast("无法读取文件")
                    } else {
                        val result = withContext(Dispatchers.IO) {
                            if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                                ExcelTimetableImporter.parseXlsx(bytes)
                            } else {
                                ExcelTimetableImporter.parseCsv(bytes.toString(Charsets.UTF_8))
                            }
                        }
                        if (result.candidates.isEmpty()) {
                            toast(result.warnings.joinToString("\n").ifBlank { "未能从表格中识别出课程" })
                        } else {
                            reviewCandidates = result.candidates.map { it.copy(draftId = java.util.UUID.randomUUID().toString()) }
                            candidatesEdited = false
                            pendingPreview = ImportPreview.Pdf(result, "Excel/CSV 表格")
                            previewPath = "表格解析"
                            overwriteMode = false
                        }
                    }
                }
                else -> toast("不支持的文件类型")
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            android.util.Log.e("CourseTableImport", "Import failed", t)
            toast("读取失败：${t.javaClass.simpleName} ${t.message}")
        } finally {
            busy = false
        }
    }

    suspend fun recognizePrepared(image: PreparedImportImage) {
        busy = true
        selectionError = null
        try {
            val session = loadImportResource { recognizeImage(context, image, imageSelection, settings) }
            if (session.result.candidates.isEmpty()) {
                session.close()
                selectionError = "未识别到课程，请重试或调整范围，保留星期和节次表头"
            } else {
                visualSession?.takeIf { it.directory != session.directory }?.close()
                visualSession = session
                reviewCandidates = session.result.candidates.map { it.copy(draftId = java.util.UUID.randomUUID().toString()) }
                candidatesEdited = true
                reviewDirty = false
                pendingPreview = ImportPreview.Pdf(session.result, "图片课表")
                previewPath = session.path
                choosingImage = false
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { selectionError = "识别失败：${e.message ?: "请重试"}" }
        finally { busy = false; autoRecognizing = false }
    }

    LaunchedEffect(preparedImage, autoRecognizing) {
        if (autoRecognizing) preparedImage?.let { recognizePrepared(it) }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { handleUri(uri, null) }
    }

    LaunchedEffect(initialEntry) {
        if (!entryHandled) {
            entryHandled = true
            when (initialEntry) {
                ImportEntry.EDU -> showEduImport = true
                ImportEntry.FILE, ImportEntry.BACKUP -> openFile.launch("*/*")
                else -> Unit
            }
        }
    }

    LaunchedEffect(incoming) {
        val inc = incoming ?: return@LaunchedEffect
        handleUri(inc.uri, inc.mimeType)
        onConsumed()
        if (initialEntry == ImportEntry.INCOMING && pendingPreview == null && preparedImage == null) {
            onBack?.invoke()
        }
    }

    if (!imagePreviewOpen) AnimatedContent(
        targetState = showEduImport,
        transitionSpec = fullscreenSubpageTransitionSpec(),
        label = "ImportToEduImport"
    ) { isEdu ->
        if (isEdu) {
            BackHandler {
                if (initialEntry == ImportEntry.EDU && onBack != null) onBack()
                else showEduImport = false
            }
            EduImportScreen(
                semesterStart = settings.semesterStart,
                onDone = { result, sourceLabel ->
                    reviewCandidates = result.candidates.map { it.copy(draftId = java.util.UUID.randomUUID().toString()) }
                    candidatesEdited = false
                    pendingPreview = ImportPreview.Pdf(result, sourceLabel)
                    previewPath = "教务系统"
                    overwriteMode = false
                    showEduImport = false
                },
                onCancel = {
                    if (initialEntry == ImportEntry.EDU && onBack != null) onBack()
                    else showEduImport = false
                }
            )
        } else {
            FullscreenPageContainer {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomContentPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                    Column {
                        Text(
                            "课程数据导入",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "选择一种方式将课程导入到课表",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ImportCard(
                    icon = Icons.Filled.School,
                    title = "从教务系统导入",
                    subtitle = "登录学校教务系统并自动获取课表",
                    enabled = !busy,
                    onClick = {
                        overwriteMode = false
                        showEduImport = true
                    }
                )

                ImportCard(
                    icon = Icons.Filled.FileImport,
                    title = "从文件导入",
                    subtitle = "支持 ICS、Excel、CSV、PDF 与图片",
                    enabled = !busy,
                    onClick = {
                        overwriteMode = false
                        openFile.launch("*/*")
                    }
                )

                ImportCard(
                    icon = Icons.Filled.Restore,
                    title = "从备份恢复",
                    subtitle = "恢复本应用导出的 JSON 课表备份",
                    enabled = !busy,
                    onClick = {
                        overwriteMode = false
                        openFile.launch("*/*")
                    }
                )

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(10.dp))
                        Text("处理中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            }
        }
    }

    if (preparedImage != null && !choosingImage && pendingPreview == null && (autoRecognizing || selectionError != null)) {
        if (busy || autoRecognizing) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(Modifier.fillMaxWidth().testTag("image-recognition-progress"), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("正在识别课表…", style = MaterialTheme.typography.titleMedium)
                            Text("完成后自动显示预览", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else AlertDialog(
            onDismissRequest = { if (!busy) { preparedImage = null; selectionError = null } },
            title = { Text(if (busy || autoRecognizing) "正在识别课表…" else "未能识别课表") },
            text = { Text(selectionError ?: "识别完成后将直接显示导入确认清单") },
            confirmButton = { if (!busy && !autoRecognizing) TextButton(onClick = { autoRecognizing = true }) { Text("重试") } },
            dismissButton = { if (!busy && !autoRecognizing) Row {
                TextButton(onClick = { choosingImage = true }) { Text("调整范围") }
                TextButton(onClick = { preparedImage = null; selectionError = null; openFile.launch("image/*") }) { Text("换图") }
            } }
        )
    }

    if (choosingImage) preparedImage?.let { image ->
        ImportImageSelection(
            image = image, selection = imageSelection, onSelection = { imageSelection = it },
            busy = busy, error = selectionError,
            onDismiss = {
                choosingImage = false
                selectionError = null
                if (pendingPreview == null) {
                    preparedImage = null
                    if (initialEntry == ImportEntry.INCOMING) onBack?.invoke()
                }
            },
            onConfirm = {
                if (!busy) scope.launch { recognizePrepared(image) }
            }
        )
    }

    if (!choosingImage) pendingPreview?.let { preview ->
        val confirmImport: () -> Unit = {
                scope.launch {
                    if (busy) return@launch
                    busy = true
                    try {
                        val toImport = reviewCandidates
                        if (toImport.isNotEmpty() && toImport.all { it.fieldErrors(settings).isEmpty() }) {
                            repo.importCourses(settings.timetableId, toImport.map { candidateToCourse(it, settings.timetableId) }, overwriteMode)
                            toast("导入成功：${toImport.size} 条上课记录")
                            flowState.clearDraft()
                            flowState.completed.value = true
                        } else toast("请先完成校对")
                    } catch (e: Exception) { toast("导入失败：${e.message}") } finally { busy = false }
                }
        }
        val session = visualSession
        if (preview !is ImportPreview.Backup) UnifiedImportReview(
            session = session, settings = settings, candidates = reviewCandidates,
            sourceLabel = if (preview is ImportPreview.Pdf) preview.sourceLabel else "ICS 日历",
            warnings = when (preview) { is ImportPreview.Pdf -> preview.result.warnings; is ImportPreview.Ics -> preview.warnings; else -> emptyList() },
            onCandidates = { reviewCandidates = it; candidatesEdited = true; reviewDirty = true },
            onDismiss = { flowState.clearDraft(); if (initialEntry == ImportEntry.INCOMING) onBack?.invoke() },
            onConfirm = { overwriteMode = it; confirmImport() }, saving = busy,
            hasEdits = reviewDirty,
            onReselect = if (preparedImage != null) { { choosingImage = true; selectionError = null } } else null
        ) else ImportPreviewDialog(
            preview = preview, saving = busy,
            onDismiss = { if (!busy) { pendingPreview = null; if (initialEntry == ImportEntry.INCOMING) onBack?.invoke() } },
            onConfirm = {
                scope.launch {
                    if (busy) return@launch
                    busy = true
                    try {
                        val imported = preview.data.timetables
                        val createdIds = timetableRepo.importTimetables(imported.map { it.timetable to it.courses })
                        createdIds.firstOrNull()?.let { settingsRepo.setActiveTimetable(it) }
                        toast("恢复成功：${imported.size} 个课表")
                        pendingPreview = null
                        onImported()
                    } catch (e: Exception) { toast("恢复失败：${e.message}") }
                    finally { busy = false }
                }
            }
        )
    }

}

@Composable
private fun ImportCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                Modifier
                    .padding(start = 14.dp, end = 8.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ImportPreviewDialog(preview: ImportPreview.Backup, saving: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, canDismiss = { !saving }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHeader("确认恢复备份", "包含 ${preview.data.timetables.size} 个课表 · ${preview.data.timetables.sumOf { it.courses.size }} 条记录")
            Text("备份将恢复为新的独立课表。", style = MaterialTheme.typography.bodyMedium)
            if(preview.data.warnings.isNotEmpty()) Text(preview.data.warnings.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onConfirm, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if(saving) "正在恢复…" else "确认恢复") }
        }
    }
}

/** 候选课程 → 编辑用 Course（用于课程编辑对话框） */
internal fun candidateToCourse(c: CandidateCourse, timetableId: Long = 0): Course {
    return Course(
        id = 0,
        timetableId = timetableId,
        name = c.name,
        teacher = c.teacher,
        location = c.location,
        dayOfWeek = c.dayOfWeek.coerceIn(1, 7),
        startSection = c.startSection,
        duration = c.duration,
        startWeek = c.startWeek,
        endWeek = c.endWeek,
        weekType = c.weekType,
        color = CourseColorPalette[
            (c.name.hashCode() and Int.MAX_VALUE) % CourseColorPalette.size
        ].toArgbLong()
    )
}

/** 编辑后的 Course → 候选课程（更新校对列表） */
internal fun courseToCandidate(c: Course): CandidateCourse {
    return CandidateCourse(
        name = c.name,
        teacher = c.teacher,
        location = c.location,
        dayOfWeek = c.dayOfWeek,
        startSection = c.startSection,
        duration = c.duration,
        startWeek = c.startWeek,
        endWeek = c.endWeek,
        weekType = c.weekType
    )
}
