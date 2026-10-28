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
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursetable.app.CourseApp
import com.coursetable.app.importer.BackupManager
import com.coursetable.app.importer.IcsExporter
import com.coursetable.app.reminder.ReminderScheduler
import com.coursetable.app.reminder.ReminderStatus
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.ThemeColor
import com.coursetable.app.ui.theme.ThemeMode
import com.coursetable.app.data.GlassEffect
import com.coursetable.app.data.ReminderBackend
import com.coursetable.app.reminder.SystemCalendarReminders
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
    REMINDER_STATUS,
    REMINDER_METHOD,
    ABOUT,
    COURSE_DISPLAY,
    APPEARANCE,
    DATA
}

private enum class ReminderAction { ENABLE, DISABLE, APP, CALENDAR, SYNC, TEST_CALENDAR, GRANT_CALENDAR }

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

    val reminderStatusFlow = remember(context) { ReminderScheduler.observeStatus(context) }
    val initialReminderStatus = remember(context) { ReminderScheduler.status(context) }
    val reminderStatus by reminderStatusFlow.collectAsState(initial = initialReminderStatus)

    // 权限状态：每次从系统设置页返回（ON_RESUME）时重新检查
    var permTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, settings.reminderBackend, settings.reminderEnabled) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permTick++
                if (settings.reminderBackend == ReminderBackend.SYSTEM_CALENDAR && settings.reminderEnabled) {
                    ReminderScheduler.reschedule(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permNotification = remember(permTick) { ReminderScheduler.canPostNotifications(context) }
    val permChannelHigh = remember(permTick) { ReminderScheduler.isChannelHighImportance(context) }
    val permExactAlarm = remember(permTick) { ReminderScheduler.canScheduleExactAlarms(context) }
    val reminderBackground = remember(permTick) {
        com.coursetable.app.reminder.ReminderBackgroundStatus.read(context)
    }
    val calendarPermissionGranted = remember(permTick) { SystemCalendarReminders.hasPermissions(context) }
    var reminderRequest by rememberSaveable { mutableStateOf<ReminderAction?>(null) }
    var reminderPermissionInFlight by rememberSaveable { mutableStateOf(false) }
    var reminderBusy by remember { mutableStateOf(false) }

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

    // Permission prompts precede any calendar write or backend change.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        reminderPermissionInFlight = false
        permTick++
        if (!granted) {
            reminderRequest = null
            toast("未允许通知，提醒设置未更改")
        } else if (!ReminderScheduler.canPostNotifications(context)) {
            // Some device policies can keep the effective app switch off despite a runtime grant.
            reminderRequest = null
            toast("请在系统设置中允许课程表发送通知")
            ReminderScheduler.openAppNotificationSettings(context)
        }
    }
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        reminderPermissionInFlight = false
        permTick++
        if (!SystemCalendarReminders.hasPermissions(context)) {
            reminderRequest = null
            toast("未允许日历读写权限，提醒方式和已同步日程未更改")
        }
    }
    fun requestReminderAction(action: ReminderAction) {
        if (!reminderBusy && !reminderPermissionInFlight) reminderRequest = action
    }
    LaunchedEffect(reminderRequest, permTick, reminderPermissionInFlight) {
        val action = reminderRequest ?: return@LaunchedEffect
        if (reminderBusy || reminderPermissionInFlight) return@LaunchedEffect
        val calendarNeeded = when (action) {
            ReminderAction.ENABLE -> settings.reminderBackend == ReminderBackend.SYSTEM_CALENDAR
            ReminderAction.DISABLE, ReminderAction.APP -> SystemCalendarReminders.mayHaveEvents(context)
            ReminderAction.CALENDAR -> settings.reminderEnabled
            else -> true
        }
        if (calendarNeeded && !SystemCalendarReminders.hasPermissions(context)) {
            reminderPermissionInFlight = true
            calendarPermission.launch(SystemCalendarReminders.requiredPermissions)
            return@LaunchedEffect
        }
        val needsAppNotification = (action == ReminderAction.ENABLE && settings.reminderBackend == ReminderBackend.APP) ||
            (action == ReminderAction.APP && settings.reminderEnabled)
        if (needsAppNotification && !ReminderScheduler.canPostNotifications(context)) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                reminderPermissionInFlight = true
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                reminderRequest = null
                toast("请先允许课程表发送通知")
                ReminderScheduler.openAppNotificationSettings(context)
            }
            return@LaunchedEffect
        }
        reminderRequest = null
        reminderBusy = true
        scope.launch {
            try {
                when (action) {
                    ReminderAction.ENABLE -> ReminderScheduler.configure(context, enabled = true)
                    ReminderAction.DISABLE -> ReminderScheduler.configure(context, enabled = false)
                    ReminderAction.APP -> ReminderScheduler.configure(context, backend = ReminderBackend.APP)
                    ReminderAction.CALENDAR -> ReminderScheduler.configure(context, backend = ReminderBackend.SYSTEM_CALENDAR)
                    ReminderAction.SYNC -> ReminderScheduler.rescheduleNow(context)
                    ReminderAction.TEST_CALENDAR -> {
                        ReminderScheduler.scheduleCalendarTest(context)
                        toast("测试日程已写入，请回到桌面等待约 1 分钟")
                    }
                    ReminderAction.GRANT_CALENDAR -> if (settings.reminderEnabled) ReminderScheduler.rescheduleNow(context)
                }
                if (action == ReminderAction.APP || action == ReminderAction.CALENDAR) {
                    activeSubpage = SettingsSubpage.PERMISSIONS
                }
                if (action == ReminderAction.ENABLE && settings.reminderBackend == ReminderBackend.APP &&
                    !ReminderScheduler.canScheduleExactAlarms(context)) {
                    toast("提醒已开启，尚未允许精确闹钟，可在权限检查中设置")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                toast("提醒设置或日历同步未完成，请检查权限后重试")
            } finally {
                reminderBusy = false
                permTick++
            }
        }
    }
    fun enableReminder() = requestReminderAction(ReminderAction.ENABLE)

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
                        if (settings.reminderBackend == ReminderBackend.SYSTEM_CALENDAR) CalendarReminderSetupScreen(
                            granted = calendarPermissionGranted,
                            enabled = settings.reminderEnabled,
                            status = reminderStatus,
                            busy = reminderBusy || reminderPermissionInFlight,
                            onGrant = { requestReminderAction(ReminderAction.GRANT_CALENDAR) },
                            onSync = { requestReminderAction(ReminderAction.SYNC) },
                            onTest = { requestReminderAction(ReminderAction.TEST_CALENDAR) },
                            onOpenCalendar = {
                                try { SystemCalendarReminders.openCalendar(context) }
                                catch (_: Exception) { toast("未找到可打开的手机日历，请先安装或启用日历应用") }
                            },
                            onBack = { activeSubpage = null },
                            bottomContentPadding = bottomContentPadding
                        ) else PermissionCheckScreen(
                            notificationEnabled = permNotification,
                            bannerEnabled = permChannelHigh,
                            exactAlarmAllowed = permExactAlarm,
                            backgroundStatus = reminderBackground,
                            reminderStatus = reminderStatus,
                            useAlarmClock = settings.reminderUseAlarmClock,
                            onBack = { activeSubpage = null },
                            bottomContentPadding = bottomContentPadding
                        )
                    }
                    SettingsSubpage.REMINDER_METHOD -> ReminderMethodScreen(
                        backend = settings.reminderBackend,
                        busy = reminderBusy || reminderPermissionInFlight,
                        onSelect = { backend -> requestReminderAction(if (backend == ReminderBackend.APP)
                            ReminderAction.APP else ReminderAction.CALENDAR) },
                        onSetup = { activeSubpage = SettingsSubpage.PERMISSIONS },
                        onBack = { activeSubpage = null },
                        bottomContentPadding = bottomContentPadding
                    )
                    SettingsSubpage.REMINDER_STATUS -> ReminderStatusPage(
                        status = reminderStatus,
                        enabled = settings.reminderEnabled,
                        bottomContentPadding = bottomContentPadding,
                        onBack = { activeSubpage = null }
                    )
                    SettingsSubpage.COURSE_DISPLAY -> SettingsDetailPage(
                        title = "课程卡片设置", tag = "course-display-page",
                        bottomContentPadding = bottomContentPadding,
                        onBack = { activeSubpage = null }
                    ) {
                        SettingsGroup(title = "卡片内容与排列") {
                            SettingItem(
                                icon = Icons.Filled.Person,
                                title = "显示教师",
                                subtitle = "在课程卡片中显示任课教师",
                                onClick = { scope.launch { settingsRepo.save(cardShowTeacher = !settings.cardShowTeacher) } },
                                trailing = {
                                    Switch(checked = settings.cardShowTeacher,
                                        onCheckedChange = { scope.launch { settingsRepo.save(cardShowTeacher = it) } })
                                }
                            )
                            SettingsDivider()
                            SettingItem(
                                icon = Icons.Filled.LocationOn,
                                title = "显示地点",
                                subtitle = "在课程卡片中显示上课地点",
                                onClick = { scope.launch { settingsRepo.save(cardShowLocation = !settings.cardShowLocation) } },
                                trailing = {
                                    Switch(checked = settings.cardShowLocation,
                                        onCheckedChange = { scope.launch { settingsRepo.save(cardShowLocation = it) } })
                                }
                            )
                            SettingsDivider()
                            SettingsChoiceItem(
                                id = "card-info-order", icon = Icons.Filled.FormatAlignLeft,
                                title = "教师与地点顺序",
                                selectedKey = if (settings.cardLocationFirst) "location-first" else "teacher-first",
                                choices = listOf(SettingsChoice("teacher-first", "教师在前"), SettingsChoice("location-first", "地点在前")),
                                onSelect = { scope.launch { settingsRepo.save(cardLocationFirst = it == "location-first") } }
                            )
                            SettingsDivider()
                            SettingsChoiceItem(
                                id = "card-align", icon = Icons.Filled.FormatAlignLeft,
                                title = "课程卡片文字对齐",
                                selectedKey = if (settings.cardAlignLeft) "left" else "center",
                                choices = listOf(SettingsChoice("center", "居中"), SettingsChoice("left", "靠左")),
                                onSelect = { scope.launch { settingsRepo.save(cardAlignLeft = it == "left") } }
                            )
                        }
                    }
                    SettingsSubpage.APPEARANCE -> {
                        var editingWallpaper by rememberSaveable { mutableStateOf(false) }
                        val wallpaperVisit = rememberPageScrollVisit(if (editingWallpaper) "wallpaper" else null)
                        PredictivePageTransition(targetState = editingWallpaper, onBack = { editingWallpaper = false }) { isWallpaper ->
                            if (isWallpaper) {
                                CompositionLocalProvider(LocalPageScrollKey provides wallpaperVisit) {
                                    WallpaperEditorScreen(settings, courses.filter { it.timetableId == settings.timetableId },
                                        onBack = { editingWallpaper = false })
                                }
                            } else SettingsDetailPage(
                                title = "外观设置", tag = "appearance-settings-page",
                                bottomContentPadding = bottomContentPadding,
                                onBack = { activeSubpage = null }
                            ) {
                                SettingsGroup(title = "颜色与背景") {
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
                                        onClick = { editingWallpaper = true },
                                        trailing = {
                                            if (wallpaperThumbnail != null) WallpaperBackground(settings.wallpaper, wallpaperThumbnail,
                                                Modifier.size(42.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)))
                                            else ListItemChevron()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    SettingsSubpage.DATA -> SettingsDetailPage(
                        title = "备份与分享", tag = "data-settings-page",
                        bottomContentPadding = bottomContentPadding,
                        onBack = { activeSubpage = null }
                    ) {
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
            val visibleFields = listOfNotNull(
                "教师".takeIf { settings.cardShowTeacher },
                "地点".takeIf { settings.cardShowLocation }
            )
            val cardSummary = if (visibleFields.isEmpty()) "仅显示课程名" else
                "显示${visibleFields.joinToString("、")}" +
                    if (visibleFields.size == 2) " · " + if (settings.cardLocationFirst) "地点在前" else "教师在前" else ""
            SettingItem(
                icon = Icons.Filled.FormatAlignLeft,
                title = "课程卡片设置",
                subtitle = cardSummary,
                onClick = { activeSubpage = SettingsSubpage.COURSE_DISPLAY }
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
                subtitle = if (reminderBusy) "正在处理提醒设置…" else if (settings.reminderEnabled)
                    "已开启 · ${settings.reminderBackend.label}" else "上课前发送提醒",
                onClick = {
                    if (settings.reminderEnabled) {
                        requestReminderAction(ReminderAction.DISABLE)
                    } else {
                        enableReminder()
                    }
                },
                trailing = {
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { enable ->
                            if (enable) enableReminder()
                            else requestReminderAction(ReminderAction.DISABLE)
                        }
                    )
                }
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.Schedule,
                title = "提醒方式",
                subtitle = settings.reminderBackend.label,
                onClick = { activeSubpage = SettingsSubpage.REMINDER_METHOD }
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

                if (settings.reminderBackend == ReminderBackend.APP) {
                    SettingsDivider()
                    SettingItem(
                        icon = Icons.Filled.Schedule,
                        title = "准时优先",
                        subtitle = "使用系统闹钟触发通知，部分手机可能显示闹钟标记。图标是否出现不代表提醒是否生效，仍需按手机要求允许后台运行。",
                        onClick = {
                            scope.launch { settingsRepo.save(reminderUseAlarmClock = !settings.reminderUseAlarmClock) }
                        },
                        trailing = {
                            Switch(
                                checked = settings.reminderUseAlarmClock,
                                onCheckedChange = { enabled ->
                                    scope.launch { settingsRepo.save(reminderUseAlarmClock = enabled) }
                                }
                            )
                        }
                    )
                }
            }
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.EventNote,
                title = "排程状态",
                subtitle = when {
                    !settings.reminderEnabled -> "提醒已关闭"
                    reminderStatus.lastError.isNotBlank() -> "排程异常 · 查看详情"
                    reminderStatus.scheduledCount <= 0 -> "暂无待提醒课程"
                    else -> "已排程 ${reminderStatus.scheduledCount} 个提醒"
                },
                onClick = { activeSubpage = SettingsSubpage.REMINDER_STATUS }
            )
            val permIssues = if (settings.reminderBackend == ReminderBackend.SYSTEM_CALENDAR)
                if (calendarPermissionGranted) 0 else 1
                else listOf(permNotification, permChannelHigh, permExactAlarm).count { !it }
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.Security,
                title = "权限检查",
                subtitle = if (permIssues > 0) "$permIssues 项待处理" else if (settings.reminderBackend == ReminderBackend.SYSTEM_CALENDAR)
                    "日历已授权 · 请确认手机日历通知" else if (reminderBackground.batteryOptimizationIgnored != true)
                    "通知权限已就绪 · 请确认耗电管理" else "通知权限已就绪 · 请确认厂商后台设置",
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
            SettingItem(
                icon = Icons.Filled.Palette,
                title = "外观设置",
                subtitle = "${ThemeColor.fromKey(settings.themeColor).label} · ${GlassEffect.fromKey(settings.glassEffect).label} · " +
                    if (settings.wallpaper == null) "未设置壁纸" else "已设置壁纸",
                onClick = { activeSubpage = SettingsSubpage.APPEARANCE }
            )
        }

        SettingsGroup(title = "数据与安全") {
            SettingItem(
                icon = Icons.Filled.Save,
                title = "备份与分享",
                subtitle = "分享课表、导出备份与日历",
                onClick = { activeSubpage = SettingsSubpage.DATA }
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
/** Keep the same full-window surface and scroll policy as existing settings pages. */
@Composable
private fun ReminderStatusPage(
    status: ReminderStatus,
    enabled: Boolean,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit
) {
    fun triggerLabel(millis: Long, courseName: String): String = buildString {
        append(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss", Locale.CHINA)
            .withZone(java.time.ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis)))
        if (courseName.isNotBlank()) append("\n$courseName")
    }

    SettingsDetailPage(
        title = "排程状态", tag = "reminder-status-page",
        bottomContentPadding = bottomContentPadding, onBack = onBack
    ) {
        SettingsGroup(title = "排程概览") {
            SettingItem(
                icon = Icons.Filled.Notifications, title = "课前提醒",
                subtitle = if (enabled) "已开启" else "已关闭"
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.EventNote, title = "已排程提醒",
                subtitle = if (!enabled) "提醒已关闭" else if (status.scheduledCount > 0)
                    if (status.backend == ReminderBackend.SYSTEM_CALENDAR) "本学期后续共 ${status.scheduledCount} 个日历提醒"
                    else "未来 7 天共 ${status.scheduledCount} 个提醒"
                else "暂无待提醒课程"
            )
            SettingsDivider()
            SettingItem(
                icon = Icons.Filled.Schedule, title = "下次提醒",
                subtitle = if (enabled && status.nextTriggerMillis > 0L)
                    triggerLabel(status.nextTriggerMillis, status.nextCourseName) else "暂无待触发提醒"
            )
        }
        if (status.lastError.isNotBlank()) {
            SettingsGroup(title = "排程异常") {
                SettingItem(
                    icon = Icons.Filled.Info, title = "最近排程异常",
                    subtitle = status.lastError, tint = MaterialTheme.colorScheme.error
                )
            }
        }
        if (status.backend == ReminderBackend.SYSTEM_CALENDAR) {
            SettingsGroup(title = "日历同步") {
                SettingItem(icon = Icons.Filled.EventNote, title = "已同步课程日程",
                    subtitle = "${status.calendarEventCount} 条；通知由手机日历发送，课程表无法自动读取送达结果。")
            }
        }
        if (status.backend == ReminderBackend.APP && status.lastDelivery.isNotBlank()) {
            SettingsGroup(title = "最近通知处理") {
                SettingItem(
                    icon = Icons.Filled.Notifications,
                    title = "触发时间与提交结果",
                    subtitle = status.lastDelivery
                )
            }
        }
        if (status.backend == ReminderBackend.APP) SettingsGroup(title = "触发日志") {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("保留最近 5 次触发记录，最新记录在前。触发记录不代表通知已送达。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (status.firedLog.isNotEmpty()) {
                    status.firedLog.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        Text(entry, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        if (status.lastFiredMillis > 0L) triggerLabel(status.lastFiredMillis, status.lastFiredName)
                        else "暂无触发记录",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailPage(
    title: String,
    tag: String,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    LiquidScrollablePage(Modifier.testTag(tag), topBar = { LiquidPageTopBar(title, onBack) }) { insets ->
        val direction = LocalLayoutDirection.current
        val padding = insets.pagePadding(extraBottom = bottomContentPadding)
        Column(
            Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())
                .testTag("$tag-scroll")
                .verticalScroll(rememberPageScrollState())
                .padding(start = padding.calculateStartPadding(direction), top = 16.dp,
                    end = padding.calculateEndPadding(direction), bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) { content() }
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
