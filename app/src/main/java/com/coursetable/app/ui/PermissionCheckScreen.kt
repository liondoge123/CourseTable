package com.coursetable.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coursetable.app.reminder.ReminderScheduler
import com.coursetable.app.reminder.ReminderBackgroundStatus
import com.coursetable.app.reminder.ReminderStatus
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme

@Composable
fun PermissionCheckScreen(
    notificationEnabled: Boolean,
    bannerEnabled: Boolean,
    exactAlarmAllowed: Boolean,
    onBack: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
    backgroundStatus: ReminderBackgroundStatus = ReminderBackgroundStatus(null, null),
    reminderStatus: ReminderStatus? = null,
    useAlarmClock: Boolean = false
) {
    val context = LocalContext.current
    LiquidScrollablePage(
        Modifier.testTag("permission-check-page"),
        topBar = { LiquidPageTopBar("权限检查", onBack) }
    ) { insets ->
        val direction = LocalLayoutDirection.current
        val padding = insets.pagePadding(extraBottom = bottomContentPadding)
        Column(
            Modifier.fillMaxSize()
                .padding(top = insets.calculateTopPadding())
                .verticalScroll(rememberPageScrollState())
                .padding(
                    start = padding.calculateStartPadding(direction),
                    top = 16.dp,
                    end = padding.calculateEndPadding(direction),
                    bottom = padding.calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "应用提醒需要通知、精确闹钟和合适的后台设置。请逐项确认，并在页面底部测试后台送达；从系统设置返回后会更新状态。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PermissionItem(
                icon = Icons.Filled.Notifications,
                title = "通知权限",
                granted = notificationEnabled,
                hint = "用于发送课程提醒通知。",
                onClick = { ReminderScheduler.openAppNotificationSettings(context) }
            )
            PermissionItem(
                icon = Icons.Filled.NotificationsActive,
                title = "横幅通知",
                granted = bannerEnabled,
                hint = "开启后，课程提醒可在屏幕顶部显示横幅。",
                onClick = { ReminderScheduler.openChannelSettings(context) }
            )
            PermissionItem(
                icon = Icons.Filled.Schedule,
                title = "精确闹钟",
                granted = exactAlarmAllowed,
                hint = "用于按设定时间触发课程提醒；未允许时，提醒可能延迟。",
                onClick = { ReminderScheduler.openExactAlarmSettings(context) }
            )
            PermissionItem(
                icon = Icons.Filled.AutoStart,
                title = "后台运行限制",
                granted = backgroundStatus.restricted?.not(),
                statusLabel = when (backgroundStatus.restricted) {
                    true -> "系统已限制"
                    false -> "未检测到标准限制"
                    null -> "无法检测"
                },
                hint = "系统限制后台运行时，闹钟可能无法触发。一加/OPPO 请在应用信息的耗电管理中允许后台活动；厂商的额外限制无法完整检测。",
                onClick = { ReminderScheduler.openAppDetails(context) }
            )
            PermissionItem(
                icon = Icons.Filled.AutoStart,
                title = "电池优化",
                granted = if (backgroundStatus.batteryOptimizationIgnored == true) true else null,
                statusLabel = when (backgroundStatus.batteryOptimizationIgnored) {
                    true -> "已豁免系统优化"
                    false -> "仍受系统优化"
                    null -> "无法检测"
                },
                hint = "后台提醒异常时，可为课程表选择不优化。此项与通知权限独立，系统优化状态也不能代表厂商的全部后台策略。",
                onClick = { ReminderScheduler.openBatteryOptimizationSettings(context) }
            )
            PermissionItem(
                icon = Icons.Filled.AutoStart,
                title = "自启动",
                granted = null,
                hint = "部分手机需要允许自启动以提高后台提醒可靠性。此项无法自动检测，请在系统设置中确认。",
                onClick = { ReminderScheduler.openAutoStartSettings(context) }
            )
            PageSectionTitle("验证提醒")
            SectionFrame {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "发送后立即回到桌面，至少等待 1 分钟，不要重新打开应用。再返回这里查看实际触发时间和通知提交结果；单次测试成功不能保证后续提醒始终准时。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = {
                        val scheduled = ReminderScheduler.scheduleTest(context, useAlarmClock)
                        Toast.makeText(context, if (scheduled) "已排定 30 秒后的测试提醒，请立即回到桌面"
                            else "测试提醒排程失败，请检查系统设置后重试", Toast.LENGTH_LONG).show()
                    }) {
                        Text("发送测试提醒")
                    }
                    if (reminderStatus != null && reminderStatus.testTriggerMillis > 0L) {
                        val planned = java.time.Instant.ofEpochMilli(reminderStatus.testTriggerMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                        Text(
                            reminderStatus.testResult.ifBlank {
                                "计划 $planned · ${reminderStatus.testScheduleMode.ifBlank { "旧测试未记录调度方式" }} · 尚未记录触发"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    granted: Boolean?,
    hint: String,
    onClick: () -> Unit,
    statusLabel: String? = null
) {
    val statusColor = when (granted) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SectionFrame {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "打开系统设置", onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListItemIcon(icon, tint = statusColor)
            Spacer(Modifier.width(ListItemDefaults.IconGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(title, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        statusLabel ?: when (granted) { true -> "已开启"; false -> "未开启"; null -> "需手动确认" },
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
