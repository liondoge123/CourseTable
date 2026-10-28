package com.coursetable.app.reminder

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/** Public Android checks cannot determine a vendor's private background policy. */
data class ReminderBackgroundStatus(
    val restricted: Boolean?,
    val batteryOptimizationIgnored: Boolean?
) {
    companion object {
        fun read(context: Context): ReminderBackgroundStatus = ReminderBackgroundStatus(
            restricted = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) false else runCatching {
                context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted
            }.getOrNull(),
            batteryOptimizationIgnored = runCatching {
                context.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrNull()
        )
    }
}

internal enum class ReminderNotificationOutcome(val label: String) {
    SUBMITTED("已提交系统；是否显示横幅需实际确认"),
    APP_BLOCKED("应用通知被关闭，未提交通知"),
    CHANNEL_BLOCKED("课前提醒通知渠道被关闭，未提交通知"),
    FAILED("通知提交失败")
}

internal enum class ReminderAlarmMode(val label: String) {
    EXACT("精确提醒"),
    ALARM_CLOCK("准时优先（系统闹钟）"),
    INEXACT_PERMISSION_MISSING("非精确提醒：排程时未获精确闹钟权限"),
    INEXACT_PERMISSION_REJECTED("非精确提醒：系统拒绝精确排程后降级")
}

internal fun reminderAlarmModeLabel(value: String?): String =
    ReminderAlarmMode.entries.firstOrNull { it.name == value }?.label ?: "旧提醒未记录调度方式"

/** Only a permission failure permits silently downgrading an exact reminder. */
internal inline fun registerReminderAlarm(
    exactAllowed: Boolean,
    scheduleExact: () -> Unit,
    scheduleInexact: (ReminderAlarmMode) -> Unit,
    exactMode: ReminderAlarmMode = ReminderAlarmMode.EXACT
): ReminderAlarmMode {
    var fallback = ReminderAlarmMode.INEXACT_PERMISSION_MISSING
    if (exactAllowed) {
        try {
            scheduleExact()
            return exactMode
        } catch (_: SecurityException) {
            fallback = ReminderAlarmMode.INEXACT_PERMISSION_REJECTED
        }
    }
    scheduleInexact(fallback)
    return fallback
}

/** Wall-clock differences describe observed lateness, never its cause. */
internal fun reminderTimingLabel(plannedMillis: Long, receivedMillis: Long): String = when {
    plannedMillis <= 0L -> "旧提醒未记录计划时间"
    receivedMillis < plannedMillis -> "设备时间发生变化，无法判断延迟"
    receivedMillis - plannedMillis < 1_000L -> "触发延迟不足 1 秒"
    else -> "触发延迟 ${(receivedMillis - plannedMillis) / 1_000L} 秒"
}
