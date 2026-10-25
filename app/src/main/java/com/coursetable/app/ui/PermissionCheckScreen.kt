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
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme

@Composable
fun PermissionCheckScreen(
    notificationEnabled: Boolean,
    bannerEnabled: Boolean,
    exactAlarmAllowed: Boolean,
    onBack: () -> Unit,
    bottomContentPadding: Dp = 0.dp
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
                "检查课程提醒所需的设置。从系统设置返回后，状态会自动更新。",
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
                title = "自启动",
                granted = null,
                hint = "部分手机需要允许自启动以提高后台提醒可靠性。此项无法自动检测，请在系统设置中确认。",
                onClick = { ReminderScheduler.openAutoStartSettings(context) }
            )
            PageSectionTitle("验证提醒")
            SectionFrame {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "检查完成后，可发送一条 30 秒后的测试通知，验证提醒是否能够送达。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = {
                        ReminderScheduler.scheduleTest(context)
                        Toast.makeText(context, "已排定 30 秒后的测试提醒，请稍后查看通知栏", Toast.LENGTH_LONG).show()
                    }) {
                        Text("发送测试提醒")
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
    onClick: () -> Unit
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
                        when (granted) { true -> "已开启"; false -> "未开启"; null -> "需手动确认" },
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
