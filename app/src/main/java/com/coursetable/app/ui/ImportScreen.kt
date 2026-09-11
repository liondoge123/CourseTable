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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.coursetable.app.importer.ImageTimetableOcr
import com.coursetable.app.importer.PdfParseResult
import com.coursetable.app.importer.UnifiedPdfImporter
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

private sealed class ImportPreview {
    data class Ics(val courses: List<Course>, val warnings: List<String>) : ImportPreview()
    data class Backup(val data: BackupData) : ImportPreview()
    data class Pdf(val result: PdfParseResult, val sourceLabel: String = "PDF 课表") : ImportPreview()
}

@Composable
fun ImportScreen(
    incoming: IncomingFile? = null,
    onConsumed: () -> Unit = {},
    initialEntry: ImportEntry = ImportEntry.HUB,
    onBack: (() -> Unit)? = null,
    bottomContentPadding: Dp = 0.dp,
    onSubpageChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as CourseApp }
    val repo = remember { app.courseRepository }
    val settingsRepo = remember { app.settingsRepository }
    val timetableRepo = remember { app.timetableRepository }
    val scope = rememberCoroutineScope()

    val settingsState = remember(settingsRepo) {
        settingsRepo.settings.stateIn(scope, SharingStarted.WhileSubscribed(5000), AppSettings())
    }
    val settings by settingsState.collectAsState()

    var busy by remember { mutableStateOf(false) }
    var pendingPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var overwriteMode by remember { mutableStateOf(false) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var reviewCandidates by remember { mutableStateOf<List<CandidateCourse>>(emptyList()) }
    var candidatesEdited by remember { mutableStateOf(false) }
    var editingCandidateIndex by remember { mutableStateOf(-1) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var showEduImport by remember(initialEntry) { mutableStateOf(initialEntry == ImportEntry.EDU) }
    var entryHandled by remember(initialEntry) { mutableStateOf(false) }

    LaunchedEffect(showEduImport) {
        onSubpageChanged(showEduImport)
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    suspend fun handleUri(uri: Uri, mimeType: String?) {
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
                    val (result, pathName, warn) = withContext(Dispatchers.IO) {
                        UnifiedPdfImporter.importPdf(context, uri)
                    }
                    if (result.candidates.isEmpty()) {
                        toast(warn.joinToString("\n").ifBlank { "未能从 PDF 中识别出课程" })
                    } else {
                        reviewCandidates = result.candidates
                        candidatesEdited = false
                        pendingPreview = ImportPreview.Pdf(result)
                        previewPath = if (pathName == "text") "文字层直读" else "OCR 识别"
                        overwriteMode = false
                    }
                }
                mime?.startsWith("image/") == true || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") -> {
                    val image = withContext(Dispatchers.IO) { decodeScaled(context, uri) }
                    if (image == null) {
                        toast("无法读取图片")
                    } else {
                        val result = withContext(Dispatchers.IO) { ImageTimetableOcr.parseImage(image) }
                        if (result.candidates.isEmpty()) {
                            toast(result.warnings.joinToString("\n").ifBlank { "未能从图片中识别出课程" })
                        } else {
                            reviewCandidates = result.candidates
                            candidatesEdited = false
                            pendingPreview = ImportPreview.Pdf(result)
                            previewPath = "OCR 识别"
                            overwriteMode = false
                        }
                    }
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
                            reviewCandidates = result.candidates
                            candidatesEdited = false
                            pendingPreview = ImportPreview.Pdf(result, "Excel/CSV 表格")
                            previewPath = "表格解析"
                            overwriteMode = false
                        }
                    }
                }
                else -> toast("不支持的文件类型")
            }
        } catch (t: Throwable) {
            toast("读取失败：${t.javaClass.simpleName} ${t.message}")
        } finally {
            busy = false
        }
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
        if (initialEntry == ImportEntry.INCOMING && pendingPreview == null) {
            onBack?.invoke()
        }
    }

    AnimatedContent(
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
                    reviewCandidates = result.candidates
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

    pendingPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            overwrite = overwriteMode,
            pdfCandidates = reviewCandidates,
            onPdfEdit = { index ->
                if (preview is ImportPreview.Pdf && index in reviewCandidates.indices) {
                    val c = reviewCandidates[index]
                    editingCandidateIndex = index
                    editingCourse = candidateToCourse(c, settings.timetableId)
                }
            },
            onPdfDelete = { index ->
                if (index in reviewCandidates.indices) {
                    reviewCandidates = reviewCandidates.filterIndexed { i, _ -> i != index }
                    candidatesEdited = true
                }
            },
            onOverwriteChange = { overwriteMode = it },
            onDismiss = {
                pendingPreview = null
                if (initialEntry == ImportEntry.INCOMING) onBack?.invoke()
            },
            onConfirm = {
                scope.launch {
                    busy = true
                    try {
                        when (preview) {
                            is ImportPreview.Ics -> {
                                if (overwriteMode) {
                                    repo.clear(settings.timetableId)
                                    toast("已覆盖原有数据")
                                }
                                val toImport = preview.courses.map { it.copy(timetableId = settings.timetableId) }
                                for (c in toImport) repo.save(c)
                                toast("导入成功：${toImport.size} 门课程")
                            }
                            is ImportPreview.Backup -> {
                                // v2 备份：导入为新课表（每个 TimetableBackup 创建一个课表）
                                val imported = preview.data.timetables
                                val createdIds = timetableRepo.importTimetables(imported.map { it.timetable to it.courses })
                                val totalCourses = imported.sumOf { it.courses.size }
                                toast("恢复成功：${imported.size} 个课表 · $totalCourses 门课程")
                                // 切换到第一个新建课表
                                createdIds.firstOrNull()?.let { settingsRepo.setActiveTimetable(it) }
                            }
                            is ImportPreview.Pdf -> {
                                if (overwriteMode) {
                                    repo.clear(settings.timetableId)
                                    toast("已覆盖原有数据")
                                }
                                val totalWeeks = settings.totalWeeks
                                val periodCount = settings.periods.size.coerceAtLeast(1)
                                val toImport = if (candidatesEdited) reviewCandidates else preview.result.candidates
                                for (c in toImport) {
                                    val sec = c.startSection.coerceIn(1, periodCount)
                                    val dur = c.duration.coerceIn(1, (periodCount - sec + 1).coerceAtLeast(1))
                                    val color = CourseColorPalette[
                                        (c.name.hashCode() and Int.MAX_VALUE) % CourseColorPalette.size
                                    ].toArgbLong()
                                    repo.save(
                                        Course(
                                            timetableId = settings.timetableId,
                                            name = c.name,
                                            teacher = c.teacher,
                                            location = c.location,
                                            dayOfWeek = c.dayOfWeek.coerceIn(1, 7),
                                            startSection = sec,
                                            duration = dur,
                                            startWeek = c.startWeek.coerceIn(1, totalWeeks),
                                            endWeek = c.endWeek.coerceIn(1, totalWeeks),
                                            weekType = c.weekType,
                                            color = color
                                        )
                                    )
                                }
                                toast("导入成功：${toImport.size} 门课程")
                            }
                        }
                    } catch (e: Exception) {
                        toast("导入失败：${e.message}")
                    } finally {
                        busy = false
                        pendingPreview = null
                        if (initialEntry == ImportEntry.INCOMING) onBack?.invoke()
                    }
                }
            }
        )
    }

    editingCourse?.let { course ->
        CourseEditorDialog(
            course = course,
            totalWeeks = settings.totalWeeks,
            periodCount = settings.periods.size.coerceAtLeast(1),
            onDismiss = {
                editingCourse = null
                editingCandidateIndex = -1
            },
            onSave = { saved ->
                editingCourse = null
                val idx = editingCandidateIndex
                editingCandidateIndex = -1
                if (idx in reviewCandidates.indices) {
                    reviewCandidates = reviewCandidates.toMutableList().apply {
                        this[idx] = courseToCandidate(saved)
                    }
                    candidatesEdited = true
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
private fun ImportPreviewDialog(
    preview: ImportPreview,
    overwrite: Boolean,
    pdfCandidates: List<CandidateCourse>,
    onPdfEdit: (Int) -> Unit,
    onPdfDelete: (Int) -> Unit,
    onOverwriteChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var deleteConfirmIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(deleteConfirmIndex) {
        if (deleteConfirmIndex != null) {
            delay(5_000)
            deleteConfirmIndex = null
        }
    }

    val (title, countText, info) = when (preview) {
        is ImportPreview.Ics -> Triple(
            "确认导入",
            "解析出 ${preview.courses.size} 条课程",
            preview.warnings.take(6).joinToString("\n")
        )
        is ImportPreview.Backup -> Triple(
            "确认恢复备份",
            "包含 ${preview.data.timetables.size} 个课表 · ${preview.data.timetables.sumOf { it.courses.size }} 门课程",
            preview.data.warnings.take(6).joinToString("\n")
        )
        is ImportPreview.Pdf -> Triple(
            "确认导入 ${preview.sourceLabel}",
            "识别出 ${preview.result.candidates.size} 门课程",
            preview.result.warnings.take(6).joinToString("\n")
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            SheetHeader(title = title, subtitle = countText)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (info.isNotBlank()) {
                    Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
                if (preview is ImportPreview.Pdf && pdfCandidates.isNotEmpty()) {
                    Text(
                        "识别结果",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    SectionFrame {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        items(pdfCandidates.size) { i ->
                            val c = pdfCandidates[i]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${WEEKDAY_NAMES2.getOrNull(c.dayOfWeek - 1) ?: c.dayOfWeek} " +
                                        "第${c.startSection}-${c.startSection + c.duration - 1}节  " +
                                        "${c.startWeek}-${c.endWeek}周 " +
                                        "${c.name} ${if (c.location.isNotBlank()) "· ${c.location}" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 2.dp)
                                )
                                IconButton(onClick = { onPdfEdit(i) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp))
                                }
                                InlineDeleteAction(
                                    armed = deleteConfirmIndex == i,
                                    onArm = { deleteConfirmIndex = i },
                                    onConfirm = {
                                        onPdfDelete(i)
                                        deleteConfirmIndex = null
                                    },
                                    compact = true
                                )
                            }
                        }
                    }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOverwriteChange(!overwrite) }) {
                    RadioButton(selected = overwrite, onClick = { onOverwriteChange(true) })
                    Text("覆盖现有数据")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOverwriteChange(!overwrite) }) {
                    RadioButton(selected = !overwrite, onClick = { onOverwriteChange(false) })
                    Text("合并（追加现有数据）")
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("确认导入") }
            }
        }
    }
}

/** 从 content Uri 解码图片，限制最大边长避免 OOM */
private fun decodeScaled(context: android.content.Context, uri: Uri): android.graphics.Bitmap? {
    return try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        val maxDim = 4096
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) {
            sample *= 2
        }
        val finalOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, finalOpts)
        }
    } catch (e: Throwable) {
        null
    }
}

/** 候选课程 → 编辑用 Course（用于课程编辑对话框） */
private fun candidateToCourse(c: CandidateCourse, timetableId: Long = 0): Course {
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
private fun courseToCandidate(c: Course): CandidateCourse {
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
