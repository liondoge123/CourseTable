package com.coursetable.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursetable.app.CourseApp
import com.coursetable.app.importer.BackupManager
import com.coursetable.app.importer.IcsExporter
import com.coursetable.app.reminder.ReminderScheduler
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.ThemeColor
import com.coursetable.app.ui.theme.ThemeMode
import com.coursetable.app.data.GlassEffect
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsSubpage {
    TIMETABLE_MANAGE,
    PERIOD_TIMES,
    PERMISSIONS,
    ABOUT,
    WALLPAPER
}

@Composable
fun SettingsScreen(
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onAtBottomChanged: (Boolean) -> Unit = {},
    onSubpageChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val appVersionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.6"
        } catch (_: Exception) {
            "1.5.6"
        }
    }
    val app = remember(context) { context.applicationContext as CourseApp }
    val settingsRepo = remember { app.settingsRepository }
    val courseRepo = remember { app.courseRepository }
    val timetableRepo = remember { app.timetableRepository }
    val scope = rememberCoroutineScope()

    // Do not render switches from AppSettings defaults while DataStore is loading.
    // Otherwise an enabled switch is first drawn off and then animates on whenever
    // this screen is opened.
    val loadedSettings by settingsRepo.settings.collectAsState(initial = null)
    val settings = loadedSettings ?: run {
        Box(Modifier.fillMaxSize())
        return
    }

    val coursesState = remember(courseRepo) {
        courseRepo.observeAll().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
    val courses by coursesState.collectAsState()

    val timetablesState = remember(timetableRepo) {
        timetableRepo.observeAll().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
    val timetables by timetablesState.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    val timeSchemes by settingsRepo.periodTimeSchemes.collectAsState(initial = emptyList())
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var activeSubpage by rememberSaveable { mutableStateOf<SettingsSubpage?>(null) }
    var displayedSubpage by remember { mutableStateOf<SettingsSubpage?>(null) }
    if (activeSubpage != null) {
        displayedSubpage = activeSubpage
    }
    val subpageScrollVisit = rememberPageScrollVisit(activeSubpage?.name)

    DisposableEffect(Unit) {
        onDispose { onSubpageChanged(false) }
    }

    // 提醒排程状态（设置变更后延迟刷新，等待异步重排完成）
    var statusTick by remember { mutableIntStateOf(0) }
    val reminderStatus = remember(statusTick, settings.reminderEnabled, settings.reminderMinutes, courses) {
        ReminderScheduler.status(context)
    }
    LaunchedEffect(settings.reminderEnabled, settings.reminderMinutes, courses.size) {
        if (settings.reminderEnabled) {
            kotlinx.coroutines.delay(1500)
            statusTick++
        }
    }

    // 权限状态：每次从系统设置页返回（ON_RESUME）时重新检查
    var permTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permNotification = remember(permTick) { ReminderScheduler.canPostNotifications(context) }
    val permChannelHigh = remember(permTick) { ReminderScheduler.isChannelHighImportance(context) }
    val permExactAlarm = remember(permTick) { ReminderScheduler.canScheduleExactAlarms(context) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val tables = timetableRepo.allOnce()
                val backup = tables.map { t ->
                    com.coursetable.app.importer.TimetableBackup(t, courseRepo.byTimetableOnce(t.id))
                }
                val json = withContext(Dispatchers.Default) { BackupManager.export(backup) }
                val total = backup.sumOf { it.courses.size }
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法写入所选文件" }.use { os ->
                        os.write(json.toByteArray())
                        os.flush()
                    }
                }
                toast("已导出 ${tables.size} 个课表 · $total 门课程")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("导出失败：${e.message}")
            }
        }
    }

    // 开启课前提醒时申请通知权限
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsRepo.save(reminderEnabled = true) }
        if (!granted) {
            toast("未授予通知权限，可能无法收到提醒")
        }
        if (!ReminderScheduler.canScheduleExactAlarms(context)) ReminderScheduler.openExactAlarmSettings(context)
    }

    /** 开启提醒：先确保通知权限，再引导授予精确闹钟特殊访问。 */
    fun enableReminder() {
        fun afterPermission() {
            scope.launch { settingsRepo.save(reminderEnabled = true) }
            if (!ReminderScheduler.canScheduleExactAlarms(context)) ReminderScheduler.openExactAlarmSettings(context)
        }
        if (ReminderScheduler.canPostNotifications(context)) {
            afterPermission()
        } else if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            afterPermission()
        }
    }

    val exportIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val exportSettings = settings
                val courses = courseRepo.byTimetableOnce(exportSettings.timetableId)
                val ics = withContext(Dispatchers.Default) { IcsExporter.export(exportSettings, courses) }
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法写入所选文件" }.use { os ->
                        os.write(ics.toByteArray())
                        os.flush()
                    }
                }
                toast("已导出 ${courses.size} 门课程到日历")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("导出失败：${e.message}")
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = settings.semesterStart.toEpochDay() * 24 * 60 * 60 * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(dpState.selectedDateMillis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    dismissController?.dismiss {
                        scope.launch { settingsRepo.save(semesterStart = date) }
                        showDatePicker = false
                    } ?: run {
                        scope.launch { settingsRepo.save(semesterStart = date) }
                        showDatePicker = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(onClick = { dismissController?.dismiss() ?: run { showDatePicker = false } }) { Text("取消") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }


    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空所有课程") },
            confirmButtonRole = DialogActionRole.Destructive,
            text = { Text("确定要清空当前课表的所有课程吗？此操作不可撤销，建议先导出备份。") },
            confirmButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(
                    onClick = {
                        dismissController?.dismiss {
                            scope.launch {
                                courseRepo.clear(settings.timetableId)
                                Toast.makeText(context, "已清空当前课表的所有课程", Toast.LENGTH_SHORT).show()
                            }
                            showClearConfirmDialog = false
                        } ?: run {
                            scope.launch {
                                courseRepo.clear(settings.timetableId)
                                Toast.makeText(context, "已清空当前课表的所有课程", Toast.LENGTH_SHORT).show()
                            }
                            showClearConfirmDialog = false
                        }
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(
                    onClick = {
                        dismissController?.dismiss { showClearConfirmDialog = false } ?: run { showClearConfirmDialog = false }
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val bottomThresholdPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collect { (value, maxValue) ->
                onAtBottomChanged(
                    maxValue > 0 && value >= (maxValue - bottomThresholdPx).coerceAtLeast(0)
                )
            }
    }

    PredictivePageTransition(
        targetState = activeSubpage != null,
        onBack = { activeSubpage = null },
        onMountedChanged = onSubpageChanged
    ) { isChild ->
        if (isChild) {
            CompositionLocalProvider(LocalPageScrollKey provides subpageScrollVisit) {
                when (displayedSubpage ?: activeSubpage) {
                    SettingsSubpage.WALLPAPER -> WallpaperEditorScreen(settings, courses.filter { it.timetableId == settings.timetableId },
                        onBack = { activeSubpage = null })
                    SettingsSubpage.TIMETABLE_MANAGE -> {
                        TimetableManageScreen(
                            timetables = timetables,
                            activeId = settings.timetableId,
                            onBack = { activeSubpage = null },
                            onSwitch = { id -> scope.launch { settingsRepo.setActiveTimetable(id) } },
                            onCreate = { name ->
                                scope.launch {
                                    val id = timetableRepo.create(name)
                                    settingsRepo.setActiveTimetable(id)
                                }
                            },
                            onRename = { id, name -> scope.launch { timetableRepo.rename(id, name) } },
                            onDelete = { table ->
                                scope.launch {
                                    timetableRepo.delete(table.id)
                                    if (table.id == settings.timetableId) {
                                        timetableRepo.allOnce().firstOrNull()?.let {
                                            settingsRepo.setActiveTimetable(it.id)
                                        }
                                    }
                                }
                            }
                        )
                    }
                    SettingsSubpage.PERIOD_TIMES -> {
                        val timetableId = settings.timetableId
                        PeriodTimeSchemesScreen(
                            schemes = timeSchemes,
                            periods = settings.periods,
                            durationMinutes = settings.periodDurationMinutes,
                            currentSchemeId = settings.activePeriodSchemeId,
                            onSave = { scheme ->
                                val normalized = if (scheme.isDefault) scheme.copy(
                                    id = com.coursetable.app.data.PeriodTimeScheme.defaultId(timetableId)
                                ) else scheme
                                settingsRepo.savePeriodTimeScheme(normalized)
                            },
                            onApply = { settingsRepo.applyPeriodTimeScheme(timetableId, it) },
                            onSaveCurrent = { settingsRepo.saveCurrentPeriodTimeScheme(timetableId, it) },
                            onDelete = { settingsRepo.deletePeriodTimeScheme(it, timetableId) },
                            onDismiss = { activeSubpage = null }
                        )
                    }
                    SettingsSubpage.PERMISSIONS -> {
                        PermissionCheckScreen(
                            notificationEnabled = permNotification,
                            bannerEnabled = permChannelHigh,
                            exactAlarmAllowed = permExactAlarm,
                            onBack = { activeSubpage = null },
                            bottomContentPadding = bottomContentPadding
                        )
                    }
                    SettingsSubpage.ABOUT -> {
                        AboutScreen(
                            appVersionName = appVersionName,
                            onBack = { activeSubpage = null },
                            bottomContentPadding = bottomContentPadding
                        )
                    }
                    null -> Unit
                }
            }
        } else {
            LiquidScrollablePage { pageInsets ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .testTag("settings-scroll")
                        .verticalScroll(scrollState)
                        .padding(pageInsets.pagePadding(extraBottom = bottomContentPadding)),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    PageHeader(
                        "设置",
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    )

        SettingsGroup(title = "学期与课表") {
            SettingItem(
                icon = Icons.Filled.CalendarMonth,
                title = "课表管理",
                subtitle = "${settings.timetableName} · ${timetables.size} 个课表",
                onClick = { activeSubpage = SettingsSubpage.TIMETABLE_MANAGE }
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.DateRange,
                title = "学期开始日期",
                subtitle = settings.semesterStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                onClick = { showDatePicker = true }
            )
            SettingsDivider()
            StepperItem(
                icon = Icons.Filled.ViewWeek,
                title = "学期总周数",
                value = "${settings.totalWeeks} 周",
                onDecrease = {
                    scope.launch { settingsRepo.save(totalWeeks = (settings.totalWeeks - 1).coerceAtLeast(1)) }
                },
                onIncrease = {
                    scope.launch { settingsRepo.save(totalWeeks = (settings.totalWeeks + 1).coerceAtMost(30)) }
                }
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.Schedule,
                title = "节次时间",
                subtitle = "共 ${settings.periods.size} 节 · " +
                    "${settings.periods.firstOrNull()?.start?.toString()?.substring(0, 5) ?: "--:--"} - " +
                    "${settings.periods.lastOrNull()?.end?.toString()?.substring(0, 5) ?: "--:--"}",
                onClick = {
                    activeSubpage = SettingsSubpage.PERIOD_TIMES
                }
            )
        }

        SettingsGroup(title = "课程显示") {
            SettingsChoiceItem(
                id = "card-align", icon = Icons.Filled.FormatAlignLeft,
                title = "课程卡片文字对齐",
                selectedKey = if (settings.cardAlignLeft) "left" else "center",
                choices = listOf(SettingsChoice("center", "居中"), SettingsChoice("left", "靠左")),
                onSelect = { scope.launch { settingsRepo.save(cardAlignLeft = it == "left") } }
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.Visibility,
                title = "显示非本周课程",
                subtitle = "关闭后只显示当前周的课程",
                onClick = {
                    scope.launch { settingsRepo.save(showNonCurrentWeek = !settings.showNonCurrentWeek) }
                },
                trailing = {
                    Switch(
                        checked = settings.showNonCurrentWeek,
                        onCheckedChange = { scope.launch { settingsRepo.save(showNonCurrentWeek = it) } }
                    )
                }
            )
        }

        SettingsGroup(title = "提醒与权限") {
            SettingItem(
                icon = Icons.Filled.Notifications,
                title = "课前提醒",
                subtitle = if (settings.reminderEnabled) "已开启" else "上课前发送系统通知",
                onClick = {
                    if (settings.reminderEnabled) {
                        scope.launch { settingsRepo.save(reminderEnabled = false) }
                    } else {
                        enableReminder()
                    }
                },
                trailing = {
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { enable ->
                            if (enable) enableReminder()
                            else scope.launch { settingsRepo.save(reminderEnabled = false) }
                        }
                    )
                }
            )
            if (settings.reminderEnabled) {
                SettingsDivider()
                StepperItem(
                    icon = Icons.Filled.Schedule,
                    title = "提前时间",
                    value = "${settings.reminderMinutes} 分钟",
                    onDecrease = {
                        scope.launch {
                            settingsRepo.save(reminderMinutes = (settings.reminderMinutes - 5).coerceAtLeast(5))
                        }
                    },
                    onIncrease = {
                        scope.launch {
                            settingsRepo.save(reminderMinutes = (settings.reminderMinutes + 5).coerceAtMost(60))
                        }
                    }
                )

                // 排程状态
                val statusLine = buildString {
                    when {
                        reminderStatus.scheduledCount <= 0 -> append("未排程任何提醒（未来 7 天没有课？）")
                        reminderStatus.nextTriggerMillis <= 0L -> append("已排程 ${reminderStatus.scheduledCount} 个提醒")
                        else -> {
                            val dt = java.time.LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(reminderStatus.nextTriggerMillis),
                                java.time.ZoneId.systemDefault()
                            )
                            append("下次提醒：${dt.monthValue}月${dt.dayOfMonth}日 ")
                            append(String.format(Locale.ROOT, "%02d:%02d", dt.hour, dt.minute))
                            append(" · ${reminderStatus.nextCourseName} · 共 ${reminderStatus.scheduledCount} 个")
                        }
                    }
                    if (reminderStatus.lastError.isNotBlank()) append("\n${reminderStatus.lastError}")
                    if (reminderStatus.firedLog.isNotEmpty()) {
                        append("\n最近触发：")
                        reminderStatus.firedLog.forEach { append("\n  $it") }
                    }
                }
                SettingsDivider()
                SettingItem(
                    icon = Icons.Filled.EventNote,
                    title = "排程状态",
                    subtitle = statusLine,
                    onClick = { statusTick++ }
                )
            }
            val permIssues = listOf(permNotification, permChannelHigh, permExactAlarm).count { !it }
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.Security,
                title = "权限检查",
                subtitle = if (permIssues > 0) "$permIssues 项待处理" else "可检测项均已开启",
                tint = if (permIssues > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                onClick = {
                    permTick++
                    activeSubpage = SettingsSubpage.PERMISSIONS
                }
            )
        }

        SettingsGroup(title = "外观") {
            SettingsChoiceItem(
                id = "theme-mode",
                icon = Icons.Filled.Brightness6,
                title = "主题模式",
                selectedKey = ThemeMode.fromKey(settings.themeMode).key,
                choices = ThemeMode.entries.map { SettingsChoice(it.key, it.label) },
                onSelect = { scope.launch { settingsRepo.save(themeMode = it) } }
            )
            SettingsDivider()
            ThemeColorItem(
                selected = ThemeColor.fromKey(settings.themeColor),
                onSelect = { color -> scope.launch { settingsRepo.save(themeColor = color.key) } }
            )
            SettingsDivider()
            SettingsChoiceItem(
                id = "glass-effect",
                icon = Icons.Filled.Palette,
                title = "玻璃效果",
                selectedKey = GlassEffect.fromKey(settings.glassEffect).key,
                choices = GlassEffect.entries.map { SettingsChoice(it.key, it.label) },
                onSelect = { scope.launch { settingsRepo.save(glassEffect = it) } }
            )
            SettingsDivider()
            val wallpaperThumbnail = rememberWallpaperBitmap(settings.wallpaper)
            SettingItem(
                icon = Icons.Filled.Palette,
                title = "课表壁纸",
                subtitle = if (settings.wallpaper == null) "未设置" else if (wallpaperThumbnail == null) "已设置 · 点此编辑或更换" else "已设置 · 所有课表共用",
                onClick = { activeSubpage = SettingsSubpage.WALLPAPER },
                trailing = {
                    if (wallpaperThumbnail != null) WallpaperBackground(settings.wallpaper, wallpaperThumbnail,
                        Modifier.size(42.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)))
                    else ListItemChevron()
                }
            )
        }

        SettingsGroup(title = "数据与安全") {
            SettingItem(
                icon = Icons.Filled.IosShare,
                title = "复制当前课表分享文本",
                subtitle = "同学可通过「粘贴导入」校对后导入",
                onClick = {
                    scope.launch {
                        try {
                            val sharedCourses = courseRepo.byTimetableOnce(settings.timetableId)
                            val text = withContext(Dispatchers.Default) {
                                com.coursetable.app.importer.PasteTimetableImporter.share(sharedCourses, settings.timetableName)
                            }
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("课表分享", text))
                            toast("已复制当前课表，可发送给同学")
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { toast(e.message ?: "分享失败，请重试") }
                    }
                }
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.Save,
                title = "导出备份文件",
                subtitle = "JSON 格式，可用于恢复",
                onClick = {
                    exportBackup.launch("coursetable_backup_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
                }
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.IosShare,
                title = "导出 ICS 日历",
                subtitle = "仅导出当前课表，可导入系统日历",
                onClick = {
                    exportIcs.launch("coursetable_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.ics")
                }
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.DeleteForever,
                title = "清空所有课程",
                subtitle = "建议先导出备份",
                tint = MaterialTheme.colorScheme.error,
                onClick = { showClearConfirmDialog = true }
            )
        }

        SettingsGroup(title = "关于") {
            SettingItem(
                icon = Icons.Filled.Info,
                title = "关于 CourseTable",
                subtitle = "v$appVersionName · 开源协议与项目信息",
                onClick = { activeSubpage = SettingsSubpage.ABOUT }
            )
        }
    }
            }
        }
    }


}
/** 轻量分组：标题、留白和细边框共同建立层级。 */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageSectionTitle(title)
        SectionFrame {
            Column { content() }
        }
    }
}

/** 分组内分隔线，与各设置行的文字起点对齐。 */
@Composable
private fun SettingsDivider() {
    // Shared leading icon and gap keep separators on the same text axis.
    HorizontalDivider(Modifier.padding(start = ListItemDefaults.TextInset, end = 16.dp))
}

/** 带图标、标题、副标题与尾部控件的设置行 */
@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListItemIcon(
            icon,
            tint = if (tint == MaterialTheme.colorScheme.onSurface) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else tint
        )
        Spacer(Modifier.width(ListItemDefaults.IconGap))
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal, color = tint)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            ListItemChevron()
        }
    }
}

/** 主题色彩选择：一行彩色圆点 */
@Composable
private fun ThemeColorItem(
    selected: ThemeColor,
    onSelect: (ThemeColor) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListItemIcon(Icons.Filled.Palette, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(ListItemDefaults.IconGap))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("强调色", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(start = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeColor.entries.forEach { color ->
                val isSelected = color == selected
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            2.dp,
                            if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                        .clickable { onSelect(color) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color.swatch),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 步进器设置行 */
@Composable
private fun StepperItem(
    icon: ImageVector,
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListItemIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(ListItemDefaults.IconGap))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrease) {
            Icon(Icons.Filled.Remove, contentDescription = "减", modifier = Modifier.size(20.dp))
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.Add, contentDescription = "加", modifier = Modifier.size(20.dp))
        }
    }
}
