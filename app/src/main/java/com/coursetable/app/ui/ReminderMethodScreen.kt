package com.coursetable.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.ReminderBackend
import com.coursetable.app.reminder.ReminderStatus
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun ReminderPage(title: String, tag: String, bottomPadding: Dp, onBack: () -> Unit,
                         content: @Composable () -> Unit) {
    LiquidScrollablePage(Modifier.testTag(tag), topBar = { LiquidPageTopBar(title, onBack) }) { insets ->
        val direction = LocalLayoutDirection.current
        val padding = insets.pagePadding(extraBottom = bottomPadding)
        Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())
            .verticalScroll(rememberPageScrollState()).padding(
                start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
                top = 16.dp, bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(20.dp)) { content() }
    }
}

@Composable
fun ReminderMethodScreen(
    backend: ReminderBackend, busy: Boolean, onSelect: (ReminderBackend) -> Unit,
    onSetup: () -> Unit, onBack: () -> Unit, bottomContentPadding: Dp
) {
    ReminderPage("提醒方式", "reminder-method-page", bottomContentPadding, onBack) {
        Text("通常使用应用提醒。希望交给手机日历时，可选择系统日历兼容方式；已有用户保持原设置，切换后清理上一种方式的待提醒课程。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionFrame {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("应用提醒", style = MaterialTheme.typography.titleMedium)
                Text("由课程表发送通知。需要开启通知、横幅和精确闹钟，并按手机要求调整耗电管理、自启动等后台设置。后台延迟明显时可开启“准时优先”。",
                    style = MaterialTheme.typography.bodyMedium)
                Button(enabled = !busy && backend != ReminderBackend.APP,
                    onClick = { onSelect(ReminderBackend.APP) }) {
                    Text(if (backend == ReminderBackend.APP) "当前使用应用提醒" else "选择应用提醒")
                }
            }
        }
        SectionFrame {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("系统日历提醒 · 兼容方式", style = MaterialTheme.typography.titleMedium)
                Text("适合在课程表导入、编辑课程，又希望交给手机日历提醒的用户。将后续课程写入独立本地日历，完成同步后由手机日历发送通知。需要授权日历访问，并开启手机日历的通知与提醒。",
                    style = MaterialTheme.typography.bodyMedium)
                Text("修改课程、提前时间或切换课表后自动同步。关闭或切换提醒方式会移除本应用同步的日程，其他日程不受影响。卸载应用后，已写入的日程可能仍会保留。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(enabled = !busy && backend != ReminderBackend.SYSTEM_CALENDAR,
                    onClick = { onSelect(ReminderBackend.SYSTEM_CALENDAR) }) {
                    Text(if (backend == ReminderBackend.SYSTEM_CALENDAR) "当前使用系统日历提醒" else "选择系统日历提醒")
                }
            }
        }
        Button(enabled = !busy, onClick = onSetup) {
            Text(if (backend == ReminderBackend.APP) "检查应用提醒设置" else "检查日历提醒设置")
        }
        Text("两种方式均需实际测试。系统日历的展示和准时性由手机日历及其系统设置决定。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CalendarReminderSetupScreen(
    granted: Boolean, enabled: Boolean, status: ReminderStatus, busy: Boolean,
    onGrant: () -> Unit, onSync: () -> Unit, onTest: () -> Unit, onOpenCalendar: () -> Unit,
    onBack: () -> Unit, bottomContentPadding: Dp
) {
    fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm:ss"))
    ReminderPage("日历提醒设置", "calendar-reminder-setup-page", bottomContentPadding, onBack) {
        SectionFrame {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("课程日历权限：${if (granted) "已授权" else "未授权"}", style = MaterialTheme.typography.titleMedium)
                Text("只维护本应用创建的“课程表提醒（自动同步）”日历中的课程日程。日历应用的通知、横幅和提醒开关需在手机设置中确认。",
                    style = MaterialTheme.typography.bodyMedium)
                if (!granted) Button(enabled = !busy, onClick = onGrant) { Text("授予日历权限") }
                Button(enabled = !busy, onClick = onOpenCalendar) { Text("打开手机日历") }
            }
        }
        SectionFrame {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("课程同步", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    !enabled -> "课前提醒已关闭，开启后同步后续课程。"
                    status.calendarLastSyncMillis <= 0L -> "尚未完成同步。"
                    else -> "上次同步：${time(status.calendarLastSyncMillis)}\n${status.calendarEventCount} 条课程日程，${status.scheduledCount} 个后续提醒。"
                }, style = MaterialTheme.typography.bodyMedium)
                if (status.lastError.isNotBlank()) Text(status.lastError, color = MaterialTheme.colorScheme.error)
                Button(enabled = !busy && enabled, onClick = onSync) { Text("立即同步") }
            }
        }
        SectionFrame {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("验证日历提醒", style = MaterialTheme.typography.titleMedium)
                Text("创建一条 2 分钟后开始、提前 1 分钟提醒的测试日程。发送后回到桌面，等待手机日历通知；这条通知不会由课程表发送。",
                    style = MaterialTheme.typography.bodyMedium)
                Button(enabled = !busy, onClick = onTest) { Text("发送日历测试提醒") }
                if (status.calendarTestTriggerMillis > 0L) Text(
                    "已写入测试日程，计划 ${time(status.calendarTestTriggerMillis)} 提醒。请实际确认手机日历是否通知；此处无法自动判断送达。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
