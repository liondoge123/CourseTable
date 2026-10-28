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
import android.util.Log
import kotlinx.coroutines.CancellationException
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.coursetable.app.MainActivity
import com.coursetable.app.data.AppDatabase
import com.coursetable.app.data.SettingsRepository
import com.coursetable.app.data.ReminderBackend
import com.coursetable.app.util.WeekUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 排程状态快照，供设置页诊断展示 */
data class ReminderStatus(
    val scheduledCount: Int,
    val nextTriggerMillis: Long,
    val nextCourseName: String,
    val lastFiredMillis: Long,
    val lastFiredName: String,
    val firedLog: List<String>,
    val lastError: String = "",
    val lastDelivery: String = "",
    val testTriggerMillis: Long = 0L,
    val testResult: String = "",
    val testScheduleMode: String = "",
    val backend: ReminderBackend = ReminderBackend.APP,
    val calendarEventCount: Int = 0,
    val calendarLastSyncMillis: Long = 0L,
    val calendarTestTriggerMillis: Long = 0L
)

object ReminderScheduler {

    const val ACTION_REMIND = "com.coursetable.app.action.COURSE_REMIND"
    const val ACTION_REPLAN = "com.coursetable.app.action.REPLAN"
    // 渠道一旦创建，其重要性等属性无法被代码修改；升级配置时须换用新 id 重建
    const val CHANNEL_ID = "course_reminder_v2"
    private const val PREFS = "reminder_state"
    private const val KEY_CLEANUP_DONE = "legacy_cleanup_v1"
    private const val KEY_LAST_ERROR = "last_schedule_error"
    private const val KEY_SCHEDULED = "scheduled_request_codes"
    private const val KEY_NEXT_TRIGGER = "next_trigger_millis"
    private const val KEY_NEXT_COURSE = "next_course_name"
    private const val KEY_LAST_FIRED = "last_fired_millis"
    private const val KEY_LAST_FIRED_NAME = "last_fired_name"
    private const val KEY_FIRED_KEYS = "fired_keys"
    private const val KEY_FIRED_LOG = "fired_log"
    private const val KEY_LAST_DELIVERY = "last_delivery"
    private const val KEY_TEST_TRIGGER = "test_trigger_millis"
    private const val KEY_TEST_RESULT = "test_result"
    private const val KEY_TEST_MODE = "test_schedule_mode"
    private const val KEY_BACKEND = "reminder_backend"
    private const val KEY_CALENDAR_COUNT = "calendar_event_count"
    private const val KEY_CALENDAR_REMINDERS = "calendar_reminder_count"
    private const val KEY_CALENDAR_TEST = "calendar_test_trigger"
    private const val KEY_CALENDAR_LAST_SYNC = "calendar_last_sync"
    internal const val EXTRA_TRIGGER_MILLIS = "triggerAtMillis"
    private const val EXTRA_ALARM_MODE = "alarmMode"
    private const val REQUEST_REPLAN = 900000
    private const val REQUEST_TEST = 900001
    private const val REQUEST_SHOW_ALARM = 900002
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordFailure(appContext, e)
            }
        }
    }

    /** 挂起广播生命周期的重排（Receiver 中使用，保证执行完） */
    fun rescheduleAsync(context: Context, pendingResult: BroadcastReceiver.PendingResult) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleInternal(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordFailure(appContext, e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 读取上次排程的状态快照 */
    fun status(context: Context): ReminderStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val backend = ReminderBackend.fromKey(prefs.getString(KEY_BACKEND, null))
        return ReminderStatus(
            scheduledCount = if (backend == ReminderBackend.SYSTEM_CALENDAR) prefs.getInt(KEY_CALENDAR_REMINDERS, 0)
                else prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().size,
            nextTriggerMillis = prefs.getLong(KEY_NEXT_TRIGGER, 0L),
            nextCourseName = prefs.getString(KEY_NEXT_COURSE, "").orEmpty(),
            lastFiredMillis = prefs.getLong(KEY_LAST_FIRED, 0L),
            lastFiredName = prefs.getString(KEY_LAST_FIRED_NAME, "").orEmpty(),
            firedLog = prefs.getString(KEY_FIRED_LOG, "").orEmpty()
                .split("\n").filter { it.isNotBlank() },
            lastError = prefs.getString(KEY_LAST_ERROR, "").orEmpty(),
            lastDelivery = prefs.getString(KEY_LAST_DELIVERY, "").orEmpty(),
            testTriggerMillis = prefs.getLong(KEY_TEST_TRIGGER, 0L),
            testResult = prefs.getString(KEY_TEST_RESULT, "").orEmpty(),
            testScheduleMode = prefs.getString(KEY_TEST_MODE, "").orEmpty(),
            backend = backend,
            calendarEventCount = prefs.getInt(KEY_CALENDAR_COUNT, 0),
            calendarLastSyncMillis = prefs.getLong(KEY_CALENDAR_LAST_SYNC, 0L),
            calendarTestTriggerMillis = prefs.getLong(KEY_CALENDAR_TEST, 0L)
        )
    }

    /** 排程或触发记录写入后，及时更新诊断页面。 */
    fun observeStatus(context: Context) = callbackFlow {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(status(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(status(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * 触发幂等：同一课程同一天只发一次通知。
     * 返回 true 表示本次应发送；false 表示重复触发，应跳过。
     */
    @Synchronized
    fun claimFired(context: Context, firedKey: String, displayName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val fired = recentFiredReminders(prefs.getStringSet(KEY_FIRED_KEYS, emptySet()).orEmpty(), today)
        // 该 key 今天已触发过 → 重复，跳过
        if (fired[firedKey] == today) return false

        val newFired = (fired + (firedKey to today))
            .map { "${it.key}@${it.value}" }.toSet()

        val now = LocalDateTime.now()
        val timeStr = String.format(Locale.getDefault(), "%02d月%02d日 %02d:%02d:%02d",
            now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second)
        val oldLog = prefs.getString(KEY_FIRED_LOG, "").orEmpty()
            .split("\n").filter { it.isNotBlank() }
        val newLog = (listOf("$timeStr · $displayName") + oldLog).take(5)

        prefs.edit()
            .putStringSet(KEY_FIRED_KEYS, newFired)
            .putLong(KEY_LAST_FIRED, System.currentTimeMillis())
            .putString(KEY_LAST_FIRED_NAME, displayName)
            .putString(KEY_FIRED_LOG, newLog.joinToString("\n"))
            .commit().also { if (!it) Log.e("ReminderScheduler", "Could not persist reminder deduplication") }
        return true
    }

    /** Persist the test identity before scheduling so a background result survives process death. */
    @android.annotation.SuppressLint("ApplySharedPref")
    fun scheduleTest(context: Context, useAlarmClock: Boolean = false): Boolean {
        val trigger = System.currentTimeMillis() + 30_000
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.edit().putLong(KEY_TEST_TRIGGER, trigger)
                .remove(KEY_TEST_RESULT).remove(KEY_TEST_MODE).commit()) {
            Log.e("ReminderScheduler", "Could not persist test reminder identity")
            return false
        }
        return try {
            val mode = setAlarm(context, REQUEST_TEST, trigger, useAlarmClock) {
                putExtra("courseName", "测试课程")
                putExtra("location", "测试教室")
                putExtra("timeLabel", "测试")
                putExtra("minutes", 0)
                putExtra("isTest", true)
            }
            prefs.edit().putString(KEY_TEST_MODE, mode.label).commit()
            true
        } catch (e: Exception) {
            Log.e("ReminderScheduler", "Test scheduling failed: ${e.javaClass.simpleName}")
            prefs.edit().putString(KEY_TEST_RESULT, "测试提醒排程失败，请检查系统设置后重试").commit()
            false
        }
    }

    // Store the observation before onReceive returns and the process becomes cached again.
    @android.annotation.SuppressLint("ApplySharedPref")
    internal fun recordDelivery(
        context: Context, intent: Intent, receivedMillis: Long, outcome: ReminderNotificationOutcome
    ) {
        val planned = intent.getLongExtra(EXTRA_TRIGGER_MILLIS, 0L)
        fun timeLabel(millis: Long) = java.time.Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
        val report = buildString {
            append(intent.getStringExtra("courseName") ?: "课程")
            if (planned > 0L) append(" · 计划 ").append(timeLabel(planned))
            append(" · 触发 ").append(timeLabel(receivedMillis))
            append(" · ").append(reminderTimingLabel(planned, receivedMillis))
            append(" · ").append(reminderAlarmModeLabel(intent.getStringExtra(EXTRA_ALARM_MODE)))
            append(" · ").append(outcome.label)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(KEY_LAST_DELIVERY, report)
        // A late broadcast from an older test must not overwrite a newer test's result.
        if (intent.getBooleanExtra("isTest", false) && planned > 0L &&
            prefs.getLong(KEY_TEST_TRIGGER, 0L) == planned) {
            editor.putString(KEY_TEST_RESULT, report)
        }
        editor.commit()
    }

    /** Clean the previous backend before making the new choice effective. */
    suspend fun configure(context: Context, enabled: Boolean? = null, backend: ReminderBackend? = null) =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            try {
                rescheduleMutex.withLock {
                    val repo = SettingsRepository(appContext, AppDatabase.get(appContext).timetableDao())
                    val old = repo.settings.first()
                    val next = backend ?: old.reminderBackend
                    val nextEnabled = enabled ?: old.reminderEnabled
                    if (nextEnabled && next == ReminderBackend.SYSTEM_CALENDAR &&
                        !SystemCalendarReminders.hasPermissions(appContext)) throw CalendarAccessRequired()
                    if (next == ReminderBackend.APP || !nextEnabled) SystemCalendarReminders.clear(appContext)
                    if (next != old.reminderBackend || !nextEnabled) cancelAlarm(appContext, REQUEST_TEST)
                    if (next == ReminderBackend.SYSTEM_CALENDAR) cancelAllInternal(appContext)
                    repo.save(reminderEnabled = nextEnabled, reminderBackend = next)
                    rescheduleLocked(appContext)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordFailure(appContext, e)
                throw e
            }
        }

    suspend fun rescheduleNow(context: Context) = withContext(Dispatchers.IO) {
        try {
            rescheduleInternal(context.applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(context, e)
            throw e
        }
    }

    suspend fun scheduleCalendarTest(context: Context): Long = withContext(Dispatchers.IO) {
        rescheduleMutex.withLock {
            val trigger = SystemCalendarReminders.scheduleTest(context.applicationContext)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_CALENDAR_TEST, trigger).apply()
            trigger
        }
    }

    /** A broadcast queued before switching backend must not send a duplicate app notification. */
    internal fun receiveReminderAsync(context: Context, pending: BroadcastReceiver.PendingResult,
                                      isTest: Boolean, deliver: () -> Unit) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleMutex.withLock {
                    val repo = SettingsRepository(appContext, AppDatabase.get(appContext).timetableDao())
                    val settings = repo.settings.first()
                    if (settings.reminderBackend == ReminderBackend.APP && (isTest || settings.reminderEnabled)) {
                        SystemCalendarReminders.clear(appContext)
                        deliver()
                    }
                    rescheduleLocked(appContext)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordFailure(appContext, e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun rescheduleInternal(context: Context) = rescheduleMutex.withLock { rescheduleLocked(context) }

    private suspend fun rescheduleLocked(context: Context) {
        cancelAllInternal(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LAST_ERROR).apply()

        val db = AppDatabase.get(context)
        val settingsRepo = SettingsRepository(context, db.timetableDao())
        val settings = settingsRepo.settings.first()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKEND, settings.reminderBackend.key).apply()
        if (!settings.reminderEnabled || settings.timetableId <= 0) {
            SystemCalendarReminders.clear(context)
            prefs.edit().putInt(KEY_CALENDAR_COUNT, 0).putInt(KEY_CALENDAR_REMINDERS, 0)
                .remove(KEY_CALENDAR_TEST).remove(KEY_CALENDAR_LAST_SYNC).apply()
            return
        }
        val activeId = settings.timetableId
        val timetable = db.timetableDao().byId(activeId) ?: run {
            SystemCalendarReminders.clear(context)
            return
        }
        val courses = db.courseDao().byTimetableOnce(activeId)
        if (settings.reminderBackend == ReminderBackend.SYSTEM_CALENDAR) {
            val now = System.currentTimeMillis()
            val result = SystemCalendarReminders.sync(context,
                calendarOccurrences(settings, courses, java.time.Instant.ofEpochMilli(now)), now)
            prefs.edit().putInt(KEY_CALENDAR_COUNT, result.eventCount)
                .putInt(KEY_CALENDAR_REMINDERS, result.reminderCount)
                .putLong(KEY_CALENDAR_LAST_SYNC, now)
                .putLong(KEY_NEXT_TRIGGER, result.nextTriggerMillis).putString(KEY_NEXT_COURSE, result.nextCourse).apply()
            return
        }
        SystemCalendarReminders.clear(context)
        prefs.edit().putInt(KEY_CALENDAR_COUNT, 0).putInt(KEY_CALENDAR_REMINDERS, 0)
            .remove(KEY_CALENDAR_TEST).remove(KEY_CALENDAR_LAST_SYNC).apply()
        // Keep maintenance alive even when this week has no courses.
        scheduleDailyReplan(context)

        val periods = timetable.periods()
        if (courses.isEmpty() || periods.isEmpty()) return

        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val scheduled = mutableSetOf<String>()
        val plans = mutableListOf<Triple<Int, Long, Intent>>()
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
                val extras = Intent().apply {
                    putExtra("courseId", course.id)
                    putExtra("epochDay", date.toEpochDay())
                    putExtra("courseName", course.name)
                    putExtra("location", course.location)
                    putExtra("timeLabel", timeLabel)
                    putExtra("minutes", settings.reminderMinutes)
                }
                plans += Triple(requestCode, millis, extras)
                scheduled.add(requestCode.toString())
                if (millis < nextTrigger) {
                    nextTrigger = millis
                    nextCourse = course.name
                }
            }
        }

        // Persist all identities before registering: interrupted scheduling remains cancellable.
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_SCHEDULED, scheduled).commit()) { "Could not persist alarm identities" }
        for ((requestCode, millis, extras) in plans) {
            setAlarm(context, requestCode, millis, settings.reminderUseAlarmClock) { putExtras(extras) }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SCHEDULED, scheduled)
            .putLong(KEY_NEXT_TRIGGER, if (nextTrigger == Long.MAX_VALUE) 0L else nextTrigger)
            .putString(KEY_NEXT_COURSE, nextCourse)
            .apply()

    }

    private inline fun setAlarm(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        useAlarmClock: Boolean = false,
        crossinline fillIntent: Intent.() -> Unit
    ): ReminderAlarmMode {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            fillIntent()
            putExtra(EXTRA_TRIGGER_MILLIS, triggerAtMillis)
        }
        val exactMode = if (useAlarmClock) ReminderAlarmMode.ALARM_CLOCK else ReminderAlarmMode.EXACT
        return registerReminderAlarm(
            exactAllowed = canScheduleExactAlarms(context),
            scheduleExact = {
                val pi = reminderPendingIntent(context, requestCode, intent, exactMode)
                if (useAlarmClock) {
                    val showIntent = PendingIntent.getActivity(
                        context, REQUEST_SHOW_ALARM,
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pi)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            },
            scheduleInexact = { mode ->
                val pi = reminderPendingIntent(context, requestCode, intent, mode)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            },
            exactMode = exactMode
        )
    }

    private fun reminderPendingIntent(
        context: Context, requestCode: Int, intent: Intent, mode: ReminderAlarmMode
    ): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode, intent.putExtra(EXTRA_ALARM_MODE, mode.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

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
        // Daily maintenance does not need exact timing. A 30-minute window is sufficient.
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis, 30 * 60 * 1000L, pi)
    }

    private fun cancelAlarm(context: Context, requestCode: Int, action: String = ACTION_REMIND) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply { this.action = action }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun recordFailure(context: Context, error: Exception) {
        // Do not persist exception messages, which could include course or device data.
        val kind = error.javaClass.simpleName
        Log.e("ReminderScheduler", "Reminder scheduling failed: $kind")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_ERROR, if (error is CalendarAccessRequired) "请允许日历读写权限，以同步或清理课程提醒"
                else "提醒排程或日历同步失败，请到提醒方式中重试").apply()
    }

    suspend fun cancelAll(context: Context) = rescheduleMutex.withLock { cancelAllInternal(context) }

    private suspend fun cancelAllInternal(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }.toMutableSet()
        val cleanup = !prefs.getBoolean(KEY_CLEANUP_DONE, false)
        if (cleanup) {
            // One-time migration; ordinary reschedules no longer scan every timetable.
            val courses = AppDatabase.get(context).courseDao().allOnce()
            val today = LocalDate.now()
            for (course in courses) for (dayOffset in 0..8) {
                codes += requestCodeOf(course.id, dayOffset)
                val epochDay = today.plusDays(dayOffset.toLong()).toEpochDay()
                codes += (course.id xor (epochDay shl 20)).toInt()
            }
        }
        codes.forEach { cancelAlarm(context, it) }
        cancelAlarm(context, REQUEST_REPLAN, ACTION_REPLAN)
        check(prefs.edit()
            .putBoolean(KEY_CLEANUP_DONE, true)
            .remove(KEY_SCHEDULED).remove(KEY_NEXT_TRIGGER).remove(KEY_NEXT_COURSE)
            .commit()) { "Could not persist alarm cleanup" }
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
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            openAppDetails(context)
        }
    }

    fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            openAppDetails(context)
        }
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

}

/** 接收闹钟广播：发通知 + 重排后续提醒 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_REMIND -> {
                val receivedMillis = System.currentTimeMillis()
                val isTest = intent.getBooleanExtra("isTest", false)
                val name = if (isTest) "测试提醒"
                else intent.getStringExtra("courseName") ?: "课程"

                // 触发幂等：同一课程同一天只发一次通知（测试提醒每次唯一）
                ReminderScheduler.receiveReminderAsync(context, goAsync(), isTest) {
                    val firedKey = if (isTest) "test@$receivedMillis"
                    else "${intent.getLongExtra("courseId", 0L)}@${intent.getLongExtra("epochDay", 0L)}"
                    if (ReminderScheduler.claimFired(context, firedKey, name)) {
                        val outcome = try {
                            postNotification(context, intent)
                        } catch (e: Exception) {
                            Log.e("ReminderReceiver", "Notification failed: ${e.javaClass.simpleName}")
                            ReminderNotificationOutcome.FAILED
                        }
                        ReminderScheduler.recordDelivery(context, intent, receivedMillis, outcome)
                    }
                }
            }
            ReminderScheduler.ACTION_REPLAN -> {
                ReminderScheduler.rescheduleAsync(context, goAsync())
            }
        }
    }

    private fun postNotification(context: Context, intent: Intent): ReminderNotificationOutcome {
        if (!ReminderScheduler.canPostNotifications(context)) return ReminderNotificationOutcome.APP_BLOCKED
        ReminderScheduler.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(ReminderScheduler.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return ReminderNotificationOutcome.CHANNEL_BLOCKED
        }

        val isTest = intent.getBooleanExtra("isTest", false)
        val name = intent.getStringExtra("courseName") ?: "课程"
        val location = intent.getStringExtra("location").orEmpty()
        val timeLabel = intent.getStringExtra("timeLabel").orEmpty()
        val minutes = intent.getIntExtra("minutes", 15)

        val title = if (isTest) "测试提醒已触发" else "$minutes 分钟后上课：$name"
        val content = if (isTest) {
            "请确认本次后台提醒是否按时显示，测试结果可在权限检查中查看"
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

        nm.notify((System.currentTimeMillis() and 0x0FFFFFFF).toInt(), notification)
        return ReminderNotificationOutcome.SUBMITTED
    }
}

/** System events that invalidate or require restoring the stored alarm plan. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in RECOVERY_ACTIONS) {
            ReminderScheduler.rescheduleAsync(context, goAsync())
        }
    }

    companion object {
        internal val RECOVERY_ACTIONS = buildSet {
            add(Intent.ACTION_BOOT_COMPLETED)
            add(Intent.ACTION_MY_PACKAGE_REPLACED)
            add(Intent.ACTION_TIME_CHANGED)
            add(Intent.ACTION_TIMEZONE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            }
        }
    }
}
