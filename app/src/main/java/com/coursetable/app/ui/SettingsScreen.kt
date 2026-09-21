package com.coursetable.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursetable.app.CourseApp
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.PeriodUtils
import com.coursetable.app.importer.BackupManager
import com.coursetable.app.importer.IcsExporter
import com.coursetable.app.reminder.ReminderScheduler
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.ThemeColor
import com.coursetable.app.ui.theme.ThemeMode
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
    var showPeriodsEditor by remember { mutableStateOf(false) }
    var editorPeriods by remember { mutableStateOf<List<PeriodTime>>(emptyList()) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showThemeModePicker by remember { mutableStateOf(false) }
    var showTimetableManage by remember { mutableStateOf(false) }
    var showPermDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { onSubpageChanged(false) }
    }

    LaunchedEffect(showTimetableManage) {
        onSubpageChanged(showTimetableManage)
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
                val json = BackupManager.export(backup)
                val total = backup.sumOf { it.courses.size }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray())
                        os.flush()
                    }
                }
                toast("已导出 ${tables.size} 个课表 · $total 门课程")
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
                val courses = courseRepo.allOnce()
                val ics = IcsExporter.export(settings, courses)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(ics.toByteArray())
                        os.flush()
                    }
                }
                toast("已导出 ${courses.size} 门课程到日历")
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


    if (showThemeModePicker) {
        val current = ThemeMode.fromKey(settings.themeMode)
        AlertDialog(
            onDismissRequest = { showThemeModePicker = false },
            title = { Text("主题模式") },
            text = {
                val dismissController = LocalDialogDismissController.current
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    dismissController?.dismiss {
                                        scope.launch { settingsRepo.save(themeMode = mode.key) }
                                        showThemeModePicker = false
                                    } ?: run {
                                        scope.launch { settingsRepo.save(themeMode = mode.key) }
                                        showThemeModePicker = false
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = current == mode,
                                onClick = {
                                    dismissController?.dismiss {
                                        scope.launch { settingsRepo.save(themeMode = mode.key) }
                                        showThemeModePicker = false
                                    } ?: run {
                                        scope.launch { settingsRepo.save(themeMode = mode.key) }
                                        showThemeModePicker = false
                                    }
                                }
                            )
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(onClick = { dismissController?.dismiss() ?: run { showThemeModePicker = false } }) { Text("取消") }
            }
        )
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text("权限检查") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PermRow(
                        icon = Icons.Filled.Notifications,
                        title = "通知权限",
                        status = if (permNotification) "已开启" else "未开启",
                        ok = permNotification,
                        error = !permNotification,
                        hint = "没有它无法发出任何提醒",
                        onClick = { ReminderScheduler.openAppNotificationSettings(context) }
                    )
                    PermRow(
                        icon = Icons.Filled.NotificationsActive,
                        title = "横幅通知",
                        status = if (permChannelHigh) "已开启" else "未开启",
                        ok = permChannelHigh,
                        error = !permChannelHigh,
                        hint = "没有它提醒只出现在状态栏，不会从顶部弹出",
                        onClick = { ReminderScheduler.openChannelSettings(context) }
                    )
                    PermRow(
                        icon = Icons.Filled.Schedule,
                        title = "精确闹钟",
                        status = if (permExactAlarm) "已允许" else "未允许",
                        ok = permExactAlarm,
                        error = !permExactAlarm,
                        hint = if (permExactAlarm) "课程提醒可按设定时间触发" else "未允许时仍会提醒，但系统可能延迟",
                        onClick = { ReminderScheduler.openExactAlarmSettings(context) }
                    )
                    PermRow(
                        icon = Icons.Filled.AutoStart,
                        title = "自启动",
                        status = "请确认 →",
                        ok = false,
                        error = false,
                        hint = "国产系统需要，允许后 App 被杀也能收到提醒",
                        onClick = { ReminderScheduler.openAutoStartSettings(context) }
                    )
                }
            },
            confirmButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(onClick = { dismissController?.dismiss() ?: run { showPermDialog = false } }) { Text("完成") }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空所有课程") },
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

    AnimatedContent(
        targetState = showTimetableManage,
        transitionSpec = fullscreenSubpageTransitionSpec(),
        label = "SettingsToTimetableManage"
    ) { isManage ->
        if (isManage) {
            BackHandler { showTimetableManage = false }
            TimetableManageScreen(
                timetables = timetables,
                activeId = settings.timetableId,
                onBack = { showTimetableManage = false },
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
        } else {
            FullscreenPageContainer {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = bottomContentPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "课程安排、提醒、外观与数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsGroup(title = "学期与课表") {
            SettingItem(
                icon = Icons.Filled.CalendarMonth,
                title = "课表管理",
                subtitle = "当前：${settings.timetableName} · 共 ${timetables.size} 个课表",
                onClick = { showTimetableManage = true }
            )
            SettingItem(
                icon = Icons.Filled.DateRange,
                title = "学期开始日期",
                subtitle = settings.semesterStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                onClick = { showDatePicker = true }
            )
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
            SettingItem(
                icon = Icons.Filled.Schedule,
                title = "节次时间",
                subtitle = "共 ${settings.periods.size} 节 · " +
                    "${settings.periods.firstOrNull()?.start?.toString()?.substring(0, 5) ?: "--:--"} - " +
                    "${settings.periods.lastOrNull()?.end?.toString()?.substring(0, 5) ?: "--:--"}",
                onClick = {
                    editorPeriods = settings.periods
                    showPeriodsEditor = true
                }
            )
        }

        SettingsGroup(title = "课程显示") {
            AlignItem(
                alignLeft = settings.cardAlignLeft,
                onChange = { scope.launch { settingsRepo.save(cardAlignLeft = it) } }
            )
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
                    if (reminderStatus.firedLog.isNotEmpty()) {
                        append("\n最近触发：")
                        reminderStatus.firedLog.forEach { append("\n  $it") }
                    }
                }
                SettingItem(
                    icon = Icons.Filled.EventNote,
                    title = "排程状态",
                    subtitle = statusLine,
                    onClick = { statusTick++ }
                )

                SettingItem(
                    icon = Icons.Filled.Send,
                    title = "发送测试提醒",
                    subtitle = "30 秒后收到通知则说明链路正常",
                    onClick = {
                        ReminderScheduler.scheduleTest(context)
                        toast("已排定 30 秒后的测试提醒，请稍后查看通知栏")
                    }
                )

                // 权限检查（单一入口弹窗）
                val permIssues = listOf(permNotification, permChannelHigh, permExactAlarm).count { !it }
                val permSummary = when {
                    permIssues > 0 -> "$permIssues 项待处理"
                    else -> "全部正常"
                }
                SettingItem(
                    icon = Icons.Filled.Security,
                    title = "权限检查",
                    subtitle = permSummary,
                    tint = if (permIssues > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    onClick = { showPermDialog = true }
                )
            }
        }

        SettingsGroup(title = "外观") {
            SettingItem(
                icon = Icons.Filled.Brightness6,
                title = "主题模式",
                subtitle = ThemeMode.fromKey(settings.themeMode).label,
                onClick = { showThemeModePicker = true }
            )
            ThemeColorItem(
                selected = ThemeColor.fromKey(settings.themeColor),
                onSelect = { color -> scope.launch { settingsRepo.save(themeColor = color.key) } }
            )
        }

        SettingsGroup(title = "数据与安全") {
            SettingItem(
                icon = Icons.Filled.Save,
                title = "导出备份文件",
                subtitle = "JSON 格式，可用于恢复",
                onClick = {
                    exportBackup.launch("coursetable_backup_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
                }
            )
            SettingItem(
                icon = Icons.Filled.IosShare,
                title = "导出 ICS 日历",
                subtitle = "可导入系统日历",
                onClick = {
                    exportIcs.launch("coursetable_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.ics")
                }
            )
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
                title = "CourseTable $appVersionName",
                subtitle = "Liquid Glass 界面由 AndroidLiquidGlass / Backdrop 2.0.1 驱动\nBackdrop © Kyant · Apache 2.0；Lucide Icons 1.45.0 · ISC"
            )
        }
    }
            }
        }
    }

    if (showPeriodsEditor) {
        PeriodsEditorDialog(
            periods = editorPeriods,
            defaultDuration = settings.periodDurationMinutes,
            onConfirm = { periods, duration ->
                scope.launch {
                    settingsRepo.save(periods = periods, periodDurationMinutes = duration)
                }
                showPeriodsEditor = false
            },
            onDismiss = { showPeriodsEditor = false }
        )
    }
}

/** 权限检查弹窗中的单行：图标 + 名称/说明 + 状态文字 */
@Composable
private fun PermRow(
    icon: ImageVector,
    title: String,
    status: String,
    ok: Boolean,
    error: Boolean,
    hint: String,
    onClick: () -> Unit
) {
    val statusColor = when {
        error -> MaterialTheme.colorScheme.error
        ok -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }
}

/** 轻量分组：标题、留白和细边框共同建立层级。 */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        SectionFrame {
            Column { content() }
        }
    }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (tint == MaterialTheme.colorScheme.onSurface) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else tint
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
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
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            Icon(
                Icons.Filled.Palette,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("主题色彩", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            Modifier.padding(start = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
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
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrease) {
            Icon(Icons.Filled.Remove, contentDescription = "减", modifier = Modifier.size(20.dp))
        }
        Text(value, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.Add, contentDescription = "加", modifier = Modifier.size(20.dp))
        }
    }
}

/** 课程卡片文字对齐设置行 */
@Composable
private fun AlignItem(
    alignLeft: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.FormatAlignLeft,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "课程卡片文字对齐",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .padding(2.dp)
        ) {
            AlignSegment("居中", selected = !alignLeft) { onChange(false) }
            AlignSegment("靠左", selected = alignLeft) { onChange(true) }
        }
    }
}

@Composable
private fun AlignSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .width(46.dp)
            .height(30.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PeriodsEditorDialog(
    periods: List<PeriodTime>,
    defaultDuration: Int,
    onConfirm: (List<PeriodTime>, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var duration by remember { mutableStateOf(defaultDuration.coerceIn(20, 90)) }
    var starts by remember(periods) { mutableStateOf(periods.map { it.start }) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deleteConfirmIndex by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deleteConfirmIndex) {
        if (deleteConfirmIndex != null) {
            kotlinx.coroutines.delay(5_000)
            deleteConfirmIndex = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑节次时间") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 全局默认时长
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("每节课时长", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { duration = (duration - 5).coerceAtLeast(20) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "减 5 分钟", modifier = Modifier.size(20.dp))
                    }
                    Text("$duration 分钟", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { duration = (duration + 5).coerceAtMost(90) }) {
                        Icon(Icons.Filled.Add, contentDescription = "加 5 分钟", modifier = Modifier.size(20.dp))
                    }
                }
                HorizontalDivider()

                starts.forEachIndexed { index, start ->
                    val end = start.plusMinutes(duration.toLong())
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { editingIndex = index }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "第 ${index + 1} 节",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(64.dp)
                        )
                        Column {
                            Text(
                                text = start.toString().substring(0, 5),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "结束 ${end.toString().substring(0, 5)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (starts.size > 1) {
                            InlineDeleteAction(
                                armed = deleteConfirmIndex == index,
                                onArm = { deleteConfirmIndex = index },
                                onConfirm = {
                                    starts = starts.filterIndexed { i, _ -> i != index }
                                    deleteConfirmIndex = null
                                },
                                compact = true
                            )
                        }
                        Icon(Icons.Filled.Edit, contentDescription = "选择开始时间", modifier = Modifier.size(18.dp))
                    }
                    if (index != starts.lastIndex) HorizontalDivider()
                }

                OutlinedButton(onClick = {
                    val lastEnd = starts.lastOrNull()?.plusMinutes(duration.toLong())
                    val newStart = lastEnd?.plusMinutes(10) ?: java.time.LocalTime.of(8, 0)
                    starts = starts + newStart
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" 添加节次")
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val dismissController = LocalDialogDismissController.current
            TextButton(onClick = {
                val err = PeriodUtils.validate(starts, duration)
                if (err != null) {
                    error = err
                    return@TextButton
                }
                dismissController?.dismiss {
                    onConfirm(PeriodUtils.build(starts, duration), duration)
                } ?: onConfirm(PeriodUtils.build(starts, duration), duration)
            }) { Text("保存") }
        },
        dismissButton = {
            val dismissController = LocalDialogDismissController.current
            TextButton(onClick = { dismissController?.dismiss() ?: onDismiss() }) { Text("取消") }
        }
    )

    editingIndex?.let { index ->
        val initial = starts[index]
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("第 ${index + 1} 节开始时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(onClick = {
                    dismissController?.dismiss {
                        starts = starts.toMutableList().also {
                            it[index] = java.time.LocalTime.of(timeState.hour, timeState.minute)
                        }
                        editingIndex = null
                    } ?: run {
                        starts = starts.toMutableList().also {
                            it[index] = java.time.LocalTime.of(timeState.hour, timeState.minute)
                        }
                        editingIndex = null
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(onClick = { dismissController?.dismiss() ?: run { editingIndex = null } }) { Text("取消") }
            }
        )
    }
}
