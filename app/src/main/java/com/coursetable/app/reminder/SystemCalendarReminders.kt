package com.coursetable.app.reminder

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import java.time.ZoneId
import java.util.UUID

internal class CalendarAccessRequired : IllegalStateException("请允许日历读写权限，以同步或清理课程提醒")

internal data class CalendarSyncResult(
    val eventCount: Int, val reminderCount: Int, val nextTriggerMillis: Long, val nextCourse: String
)

/** Own local calendar only. No query, update or deletion of the user's other calendar events. */
object SystemCalendarReminders {
    private const val CALENDAR_NAME = "coursetable_reminders_v1"
    private const val PREFS = "system_calendar_reminders"
    private const val KEY_OWNED = "may_have_managed_events"
    private const val KEY_TEST = "test_event_id"
    private const val KEY_TEST_TRIGGER = "test_trigger"
    private const val KEY_LAST_SYNC = "last_sync"
    val requiredPermissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermissions(context: Context) = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun mayHaveEvents(context: Context) = prefs(context).getBoolean(KEY_OWNED, false)
    fun lastSyncMillis(context: Context) = prefs(context).getLong(KEY_LAST_SYNC, 0L)
    fun testTriggerMillis(context: Context) = prefs(context).getLong(KEY_TEST_TRIGGER, 0L)
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun requireAccess(context: Context) {
        if (!hasPermissions(context)) throw CalendarAccessRequired()
    }

    private fun calendarIds(context: Context): List<Long> {
        requireAccess(context)
        return context.contentResolver.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.NAME}=? AND ${Calendars.DELETED}=0",
            arrayOf(context.packageName, CalendarContract.ACCOUNT_TYPE_LOCAL, CALENDAR_NAME), null
        )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
            ?: error("Calendar provider unavailable")
    }

    private fun ensureCalendar(context: Context): Long {
        // Preserve a visibility/notification choice made by the user in their calendar app.
        calendarIds(context).firstOrNull()?.let { return it }
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, context.packageName)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, context.packageName)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, CALENDAR_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, "课程表提醒（自动同步）")
            put(Calendars.OWNER_ACCOUNT, context.packageName)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.CALENDAR_COLOR, 0xFF0A84FF.toInt())
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT.toString())
            put(Calendars.MAX_REMINDERS, 1)
        }
        return ContentUris.parseId(requireNotNull(context.contentResolver.insert(uri, values)))
    }

    private data class Stored(val id: Long, val calendarId: Long, val event: CalendarOccurrence)
    private fun ownedSelection() = "(${Events.CUSTOM_APP_URI} LIKE ? OR ${Events.CUSTOM_APP_URI} LIKE ?)"
    private fun ownedArgs() = arrayOf("$CALENDAR_COURSE_PREFIX%", "$CALENDAR_TEST_PREFIX%")

    private fun readManaged(context: Context, ids: List<Long>): List<Stored> = buildList {
        for (calendarId in ids) {
            val projection = arrayOf(Events._ID, Events.CUSTOM_APP_URI, Events.TITLE, Events.EVENT_LOCATION,
                Events.DESCRIPTION, Events.DTSTART, Events.DTEND, Events.EVENT_TIMEZONE)
            requireNotNull(context.contentResolver.query(Events.CONTENT_URI, projection,
                "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0 AND ${ownedSelection()}",
                arrayOf(calendarId.toString(), *ownedArgs()), null)).use { cursor ->
                while (cursor.moveToNext()) add(Stored(cursor.getLong(0), calendarId,
                    CalendarOccurrence(cursor.getString(1), cursor.getString(2).orEmpty(),
                        cursor.getString(3).orEmpty(), cursor.getString(4).orEmpty(), cursor.getLong(5),
                        cursor.getLong(6), cursor.getString(7).orEmpty(), null)))
            }
        }
    }

    private fun eventValues(calendarId: Long, event: CalendarOccurrence) = ContentValues().apply {
        put(Events.CALENDAR_ID, calendarId)
        put(Events.CUSTOM_APP_URI, event.key)
        put(Events.TITLE, event.title)
        put(Events.EVENT_LOCATION, event.location)
        put(Events.DESCRIPTION, event.description)
        put(Events.DTSTART, event.startMillis)
        put(Events.DTEND, event.endMillis)
        put(Events.EVENT_TIMEZONE, event.zone)
        put(Events.ALL_DAY, 0)
        put(Events.HAS_ALARM, if (event.reminderMinutes == null) 0 else 1)
        put(Events.STATUS, Events.STATUS_CONFIRMED)
    }

    private fun reminderValues(minutes: Int) = ContentValues().apply {
        put(Reminders.MINUTES, minutes)
        put(Reminders.METHOD, Reminders.METHOD_ALERT)
    }

    private fun scope(row: Stored) = arrayOf(row.id.toString(), row.calendarId.toString(), row.event.key)
    // CalendarProvider rejects an extra selection on an item URI. Use the collection
    // URI with all three ownership predicates, including the exact event id.
    private val EVENT_SCOPE = "${Events._ID}=? AND ${Events.CALENDAR_ID}=? AND ${Events.CUSTOM_APP_URI}=?"

    /** Mark possible writes durably before the provider transaction: cleanup survives interruption. */
    @android.annotation.SuppressLint("ApplySharedPref")
    internal fun sync(context: Context, desired: List<CalendarOccurrence>, nowMillis: Long): CalendarSyncResult {
        requireAccess(context)
        check(prefs(context).edit().putBoolean(KEY_OWNED, true).commit())
        val calendarId = ensureCalendar(context)
        val existing = readManaged(context, calendarIds(context))
        val reminders = mutableMapOf<Long, MutableList<Pair<Int, Int>>>()
        for (chunk in existing.map { it.id }.chunked(200)) {
            val selection = "${Reminders.EVENT_ID} IN (${chunk.joinToString(",") { "?" }})"
            requireNotNull(context.contentResolver.query(Reminders.CONTENT_URI,
                arrayOf(Reminders.EVENT_ID, Reminders.MINUTES, Reminders.METHOD), selection,
                chunk.map { it.toString() }.toTypedArray(), null)).use { cursor ->
                while (cursor.moveToNext()) reminders.getOrPut(cursor.getLong(0)) { mutableListOf() }
                    .add(cursor.getInt(1) to cursor.getInt(2))
            }
        }
        val desiredByKey = desired.associateBy { it.key }
        val byKey = existing.filter { it.calendarId == calendarId }.groupBy { it.event.key }
        val operations = arrayListOf<ContentProviderOperation>()
        fun flush() {
            if (operations.isNotEmpty()) {
                context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
                operations.clear()
            }
        }
        for (row in existing) {
            val keepTest = row.event.key.startsWith(CALENDAR_TEST_PREFIX) && row.event.endMillis > nowMillis
            val keepCourse = row.calendarId == calendarId && desiredByKey.containsKey(row.event.key) &&
                byKey[row.event.key]?.first()?.id == row.id
            if (!keepTest && !keepCourse) {
                operations += ContentProviderOperation.newDelete(Events.CONTENT_URI)
                    .withSelection(EVENT_SCOPE, scope(row)).build()
                if (operations.size >= 80) flush()
            }
        }
        for (event in desired) {
            // Each event and its reminder rows stay in the same provider transaction.
            if (operations.size >= 76) flush()
            val old = byKey[event.key]?.firstOrNull()
            if (old == null) {
                val index = operations.size
                operations += ContentProviderOperation.newInsert(Events.CONTENT_URI)
                    .withValues(eventValues(calendarId, event)).build()
                event.reminderMinutes?.let { minutes ->
                    operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                        .withValues(reminderValues(minutes)).withValueBackReference(Reminders.EVENT_ID, index).build()
                }
            } else {
                val expectedReminders = event.reminderMinutes?.let { listOf(it to Reminders.METHOD_ALERT) }.orEmpty()
                if (old.event != event.copy(reminderMinutes = null) || reminders[old.id].orEmpty() != expectedReminders) {
                    operations += ContentProviderOperation.newUpdate(Events.CONTENT_URI)
                        .withSelection(EVENT_SCOPE, scope(old)).withExpectedCount(1)
                        .withValues(eventValues(calendarId, event)).build()
                    operations += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                        .withSelection("${Reminders.EVENT_ID}=?", arrayOf(old.id.toString())).build()
                    event.reminderMinutes?.let { minutes ->
                        operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                            .withValues(reminderValues(minutes).apply { put(Reminders.EVENT_ID, old.id) }).build()
                    }
                }
            }
        }
        flush()
        prefs(context).edit().putLong(KEY_LAST_SYNC, nowMillis).apply()
        val upcoming = desired.filter { (it.triggerMillis ?: 0L) > nowMillis }
        val next = upcoming.minByOrNull { it.triggerMillis!! }
        return CalendarSyncResult(desired.size, upcoming.size, next?.triggerMillis ?: 0L, next?.title.orEmpty())
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    internal fun clear(context: Context) {
        if (!mayHaveEvents(context)) return
        if (!hasPermissions(context)) {
            throw CalendarAccessRequired()
        }
        for (id in calendarIds(context)) {
            context.contentResolver.delete(Events.CONTENT_URI,
                "${Events.CALENDAR_ID}=? AND ${ownedSelection()}", arrayOf(id.toString(), *ownedArgs()))
        }
        check(readManaged(context, calendarIds(context)).isEmpty()) { "Managed calendar cleanup incomplete" }
        check(prefs(context).edit().putBoolean(KEY_OWNED, false)
            .remove(KEY_TEST).remove(KEY_TEST_TRIGGER).remove(KEY_LAST_SYNC).commit())
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    internal fun scheduleTest(context: Context): Long {
        requireAccess(context)
        check(prefs(context).edit().putBoolean(KEY_OWNED, true).commit())
        val calendarId = ensureCalendar(context)
        // An event 2 minutes away with a 1-minute reminder exercises the real pre-class route.
        val start = System.currentTimeMillis() + 120_000L
        val event = CalendarOccurrence("$CALENDAR_TEST_PREFIX${UUID.randomUUID()}", "课程表 · 日历提醒测试", "",
            "课程表创建的测试日程，可在测试结束后删除。", start, start + 300_000L,
            ZoneId.systemDefault().id, 1)
        val operations = arrayListOf<ContentProviderOperation>()
        for (row in readManaged(context, calendarIds(context)).filter { it.event.key.startsWith(CALENDAR_TEST_PREFIX) }) {
            operations += ContentProviderOperation.newDelete(Events.CONTENT_URI)
                .withSelection(EVENT_SCOPE, scope(row)).build()
        }
        val eventIndex = operations.size
        operations += ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(eventValues(calendarId, event)).build()
        operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(reminderValues(1))
            .withValueBackReference(Reminders.EVENT_ID, eventIndex).build()
        val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
        prefs(context).edit().putLong(KEY_TEST, ContentUris.parseId(requireNotNull(results[eventIndex].uri)))
            .putLong(KEY_TEST_TRIGGER, start - 60_000L).commit()
        return start - 60_000L
    }

    fun openCalendar(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW,
            CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
