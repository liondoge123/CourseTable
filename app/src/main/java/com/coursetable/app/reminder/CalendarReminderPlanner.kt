package com.coursetable.app.reminder

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.util.WeekUtils
import java.time.Instant
import java.time.ZoneId

internal const val CALENDAR_COURSE_PREFIX = "coursetable-reminder://course/"
internal const val CALENDAR_TEST_PREFIX = "coursetable-reminder://test/"

internal data class CalendarOccurrence(
    val key: String,
    val title: String,
    val location: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val zone: String,
    val reminderMinutes: Int?
) {
    val triggerMillis: Long? get() = reminderMinutes?.let { startMillis - it * 60_000L }
}

/** Submit the remaining semester, so reminders do not depend on a daily app wake-up. */
internal fun calendarOccurrences(
    settings: AppSettings, courses: List<Course>, now: Instant,
    zone: ZoneId = ZoneId.systemDefault()
): List<CalendarOccurrence> {
    require(settings.totalWeeks in 1..60) { "Invalid semester length" }
    val nowMillis = now.toEpochMilli()
    val result = mutableListOf<CalendarOccurrence>()
    for (course in courses) {
        if (course.timetableId != settings.timetableId || course.dayOfWeek !in 1..7 || course.duration < 1) continue
        val first = settings.periods.getOrNull(course.startSection - 1) ?: continue
        val last = settings.periods.getOrNull(course.startSection + course.duration - 2) ?: continue
        for (week in 1..settings.totalWeeks) {
            if (!course.visibleOnWeek(week)) continue
            val date = WeekUtils.dateOfWeek(settings.semesterStart, week, course.dayOfWeek)
            val start = date.atTime(first.start).atZone(zone)
            var end = date.atTime(last.end).atZone(zone)
            if (!end.isAfter(start)) end = end.plusDays(1)
            if (end.toInstant().toEpochMilli() <= nowMillis) continue
            val startMillis = start.toInstant().toEpochMilli()
            // A reminder whose deadline already passed must not become an immediate catch-up alert.
            val minutes = settings.reminderMinutes.takeIf { startMillis - it * 60_000L > nowMillis }
            result += CalendarOccurrence(
                key = "$CALENDAR_COURSE_PREFIX${settings.timetableId}/${course.id}/${date.toEpochDay()}",
                title = course.name,
                location = course.location,
                description = buildString {
                    if (course.teacher.isNotBlank()) append("教师：").append(course.teacher).append('\n')
                    append("来自课程表 · ").append(settings.timetableName)
                    append("\n自动同步的课程，请在课程表中修改。")
                },
                startMillis = startMillis,
                endMillis = end.toInstant().toEpochMilli(),
                zone = zone.id,
                reminderMinutes = minutes
            )
        }
    }
    return result.distinctBy { it.key }.sortedWith(compareBy({ it.startMillis }, { it.key }))
}
