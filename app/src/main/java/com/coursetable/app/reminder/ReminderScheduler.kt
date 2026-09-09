package com.coursetable.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.coursetable.app.MainActivity
import com.coursetable.app.data.AppDatabase
import com.coursetable.app.data.SettingsRepository
import com.coursetable.app.util.WeekUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 排程状态快照，供设置页诊断展示 */
data class ReminderStatus(
    val scheduledCount: Int,
    val nextTriggerMillis: Long,
    val nextCourseName: String,
    val lastFiredMillis: Long,
    val lastFiredName: String,
    val firedLog: List<String>
)

object ReminderScheduler {

    const val ACTION_REMIND = "com.coursetable.app.action.COURSE_REMIND"
    const val ACTION_REPLAN = "com.coursetable.app.action.REPLAN"
    // 渠道一旦创建，其重要性等属性无法被代码修改；升级配置时须换用新 id 重建
    const val CHANNEL_ID = "course_reminder_v2"
    private const val PREFS = "reminder_state"
    private const val KEY_SCHEDULED = "scheduled_request_codes"
    private const val KEY_NEXT_TRIGGER = "next_trigger_millis"
    private const val KEY_NEXT_COURSE = "next_course_name"
    private const val KEY_LAST_FIRED = "last_fired_millis"
    private const val KEY_LAST_FIRED_NAME = "last_fired_name"
    private const val KEY_FIRED_KEYS = "fired_keys"
    private const val KEY_FIRED_LOG = "fired_log"
    private const val REQUEST_REPLAN = 900000
    private const val REQUEST_TEST = 900001
    private const val REQUEST_BASE = 2_000_000
    private const val REPLAN_HOUR = 0
    private const val REPLAN_MINUTE = 30

    /** 防止多个重排并发执行（否则可能出现闹钟叠加） */
    private val rescheduleMutex = Mutex()

    /** 课程闹钟编号：与课程 id 和未来第几天唯一对应，避开保留编号 */
    private fun requestCodeOf(courseId: Long, dayOffset: Int): Int =
        REQUEST_BASE + (courseId * 10 + dayOffset).toInt()

    /**
     * 重新排程：取消旧闹钟，为当前激活课表未来 7 天内的课程设置课前提醒。
     * 可在任意线程调用。
     */
    fun reschedule(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleInternal(appContext)
            } catch (_: Throwable) {
            }
        }
    }

    /** 挂起广播生命周期的重排（Receiver 中使用，保证执行完） */
    fun rescheduleAsync(context: Context, pendingResult: BroadcastReceiver.PendingResult) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleInternal(appContext)
            } catch (_: Throwable) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 读取上次排程的状态快照 */
    fun status(context: Context): ReminderStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ReminderStatus(
            scheduledCount = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().size,
            nextTriggerMillis = prefs.getLong(KEY_NEXT_TRIGGER, 0L),
            nextCourseName = prefs.getString(KEY_NEXT_COURSE, "").orEmpty(),
            lastFiredMillis = prefs.getLong(KEY_LAST_FIRED, 0L),
            lastFiredName = prefs.getString(KEY_LAST_FIRED_NAME, "").orEmpty(),
            firedLog = prefs.getString(KEY_FIRED_LOG, "").orEmpty()
                .split("\n").filter { it.isNotBlank() }
        )
    }

    /**
     * 触发幂等：同一课程同一天只发一次通知。
     * 返回 true 表示本次应发送；false 表示重复触发，应跳过。
     */
    @Synchronized
    fun claimFired(context: Context, firedKey: String, displayName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val fired = prefs.getStringSet(KEY_FIRED_KEYS, emptySet()).orEmpty()
            .mapNotNull { it.split("@").takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }
            .toMap()
        // 该 key 今天已触发过 → 重复，跳过
        if (fired[firedKey] == today) return false

        val newFired = (fired + (firedKey to today))
            .map { "${it.key}@${it.value}" }.toSet()

        val now = LocalDateTime.now()
        val timeStr = String.format("%02d月%02d日 %02d:%02d:%02d",
            now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second)
        val oldLog = prefs.getString(KEY_FIRED_LOG, "").orEmpty()
            .split("\n").filter { it.isNotBlank() }
        val newLog = (listOf("$timeStr · $displayName") + oldLog).take(5)

        prefs.edit()
            .putStringSet(KEY_FIRED_KEYS, newFired)
            .putLong(KEY_LAST_FIRED, System.currentTimeMillis())
            .putString(KEY_LAST_FIRED_NAME, displayName)
            .putString(KEY_FIRED_LOG, newLog.joinToString("\n"))
            .apply()
        return true
    }

    /** 排一个 30 秒后的测试提醒，走正式提醒的完整链路 */
    fun scheduleTest(context: Context) {
        val trigger = System.currentTimeMillis() + 30_000
        setAlarm(context, REQUEST_TEST, trigger) {
            putExtra("courseName", "测试课程")
            putExtra("location", "测试教室")
            putExtra("timeLabel", "测试")
            putExtra("minutes", 0)
            putExtra("isTest", true)
        }
    }

    private suspend fun rescheduleInternal(context: Context) = rescheduleMutex.withLock {
        cancelAll(context)

        val db = AppDatabase.get(context)
        val settingsRepo = SettingsRepository(context, db.timetableDao())
        val activeId = settingsRepo.activeTimetableId.first()
        if (activeId <= 0) return@withLock
        val timetable = db.timetableDao().byId(activeId) ?: return@withLock

        val settings = settingsRepo.settings.first()
        if (!settings.reminderEnabled) return@withLock

        val courses = db.courseDao().byTimetableOnce(activeId)
        val periods = timetable.periods()
        if (courses.isEmpty() || periods.isEmpty()) return@withLock

        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val scheduled = mutableSetOf<String>()
        var nextTrigger = Long.MAX_VALUE
        var nextCourse = ""

        for (dayOffset in 0..7) {
            val date = today.plusDays(dayOffset.toLong())
            val week = WeekUtils.weekOf(timetable.semesterStart(), date)
            if (week < 1 || week > timetable.totalWeeks) continue
            val dayOfWeek = date.dayOfWeek.value
            for (course in courses) {
                if (course.dayOfWeek != dayOfWeek) continue
                if (!course.visibleOnWeek(week)) continue
                val period = periods.getOrNull(course.startSection - 1) ?: continue
                val trigger = LocalDateTime.of(date, period.start).minusMinutes(settings.reminderMinutes.toLong())
                if (!trigger.isAfter(now)) continue

                val requestCode = requestCodeOf(course.id, dayOffset)
                val millis = trigger.atZone(zone).toInstant().toEpochMilli()
                val timeLabel = period.start.toString().substring(0, 5)
                setAlarm(context, requestCode, millis) {
                    putExtra("courseId", course.id)
                    putExtra("epochDay", date.toEpochDay())
                    putExtra("courseName", course.name)
                    putExtra("location", course.location)
                    putExtra("timeLabel", timeLabel)
                    putExtra("minutes", settings.reminderMinutes)
                }
                scheduled.add(requestCode.toString())
                if (millis < nextTrigger) {
                    nextTrigger = millis
                    nextCourse = course.name
                }
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SCHEDULED, scheduled)
            .putLong(KEY_NEXT_TRIGGER, if (nextTrigger == Long.MAX_VALUE) 0L else nextTrigger)
            .putString(KEY_NEXT_COURSE, nextCourse)
            .apply()

        scheduleDailyReplan(context)
    }

    private inline fun setAlarm(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        crossinline fillIntent: Intent.() -> Unit
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            fillIntent()
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock：系统闹钟级唤醒，App 被杀/国产 ROM 限制下也能准点触发。
        // 最终只表现为通知栏一条消息，副作用是状态栏出现闹钟图标。
        val showIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            pi
        )
    }

    /** 每日重排闹钟，保证长期未打开 App 时提醒也能持续 */
    fun scheduleDailyReplan(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_REPLAN }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_REPLAN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), LocalTime.of(REPLAN_HOUR, REPLAN_MINUTE))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val showIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent), pi)
    }

    private fun cancelAlarm(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_REMIND }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    /**
     * 彻底取消：本地记录的编号 + 按数据库课程推导出的所有可能编号，全部取消。
     * 不存在的闹钟取消是无害操作，确保没有"孤儿闹钟"残留。
     */
    suspend fun cancelAll(context: Context) {
        // 1) 本地记录
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().forEach { codeStr ->
            codeStr.toIntOrNull()?.let { cancelAlarm(context, it) }
        }

        // 2) 按数据库推导（覆盖所有课表的所有课程，宁多勿漏）
        try {
            val db = AppDatabase.get(context)
            val courses = db.courseDao().allOnce()
            for (course in courses) {
                for (dayOffset in 0..8) {
                    cancelAlarm(context, requestCodeOf(course.id, dayOffset))
                }
            }
        } catch (_: Throwable) {
        }

        // 3) 旧版本（v1.2.14 及之前）使用的编号公式，逐一清理可能残留的旧闹钟
        try {
            val db = AppDatabase.get(context)
            val today = LocalDate.now()
            for (course in db.courseDao().allOnce()) {
                for (dayOffset in 0..8) {
                    val epochDay = today.plusDays(dayOffset.toLong()).toEpochDay()
                    cancelAlarm(context, (course.id xor (epochDay shl 20)).toInt())
                }
            }
        } catch (_: Throwable) {
        }

        prefs.edit()
            .remove(KEY_SCHEDULED)
            .remove(KEY_NEXT_TRIGGER)
            .remove(KEY_NEXT_COURSE)
            .apply()
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "课前提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课程开始前的提醒通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    /** 跳转本 App「课前提醒」渠道的系统设置页（供用户开启横幅/悬浮通知） */
    fun openChannelSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** 渠道重要性是否 ≥ HIGH（决定通知是否有顶部横幅） */
    fun isChannelHighImportance(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(CHANNEL_ID) ?: return false
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    /** 跳转本 App 的通知设置页 */
    fun openAppNotificationSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /** 跳转国产 ROM 的自启动管理页（无统一 API，按厂商逐个尝试，失败回退到应用详情页） */
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            // 小米 MIUI
            android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // 华为 EMUI
            android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
            // OPPO ColorOS
            android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            android.content.ComponentName(
                "com.coloros.phonemanager",
                "com.coloros.phonemanager.startupapp.StartupAppListActivity"
            ),
            // vivo
            android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ),
            android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.MainActivity"
            ),
            // 三星
            android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        )
        for (component in candidates) {
            try {
                val intent = Intent().apply {
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        // 回退：应用详情页
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /** 是否已加入电池优化白名单（未加入时国产 ROM 可能延迟/丢弃闹钟） */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 跳转"请求忽略电池优化"授权页 */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }
}

/** 接收闹钟广播：发通知 + 重排后续提醒 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_REMIND -> {
                val isTest = intent.getBooleanExtra("isTest", false)
                val name = if (isTest) "测试提醒"
                else intent.getStringExtra("courseName") ?: "课程"

                // 触发幂等：同一课程同一天只发一次通知（测试提醒每次唯一）
                val firedKey = if (isTest) "test@${System.currentTimeMillis()}"
                else "${intent.getLongExtra("courseId", 0L)}@${intent.getLongExtra("epochDay", 0L)}"
                val shouldNotify = ReminderScheduler.claimFired(context, firedKey, name)
                if (shouldNotify) {
                    postNotification(context, intent)
                }
                ReminderScheduler.rescheduleAsync(context, goAsync())
            }
            ReminderScheduler.ACTION_REPLAN -> {
                ReminderScheduler.rescheduleAsync(context, goAsync())
            }
        }
    }

    private fun postNotification(context: Context, intent: Intent) {
        if (!ReminderScheduler.canPostNotifications(context)) return
        ReminderScheduler.ensureChannel(context)

        val isTest = intent.getBooleanExtra("isTest", false)
        val name = intent.getStringExtra("courseName") ?: "课程"
        val location = intent.getStringExtra("location").orEmpty()
        val timeLabel = intent.getStringExtra("timeLabel").orEmpty()
        val minutes = intent.getIntExtra("minutes", 15)

        val title = if (isTest) "提醒测试成功" else "$minutes 分钟后上课：$name"
        val content = if (isTest) {
            "提醒链路正常，正式提醒将按时送达"
        } else buildString {
            append(timeLabel)
            if (location.isNotBlank()) append(" · ").append(location)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(com.coursetable.app.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() and 0x0FFFFFFF).toInt(), notification)
    }
}

/** 开机后重排提醒 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAsync(context, goAsync())
        }
    }
}
