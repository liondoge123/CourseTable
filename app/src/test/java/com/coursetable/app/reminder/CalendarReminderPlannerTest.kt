package com.coursetable.app.reminder

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.ReminderBackend
import com.coursetable.app.data.WeekType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalendarReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val settings = AppSettings(timetableId = 7, semesterStart = LocalDate.of(2026, 8, 31),
        totalWeeks = 18, reminderMinutes = 15,
        periods = listOf(PeriodTime(LocalTime.of(9, 0), LocalTime.of(9, 45)),
            PeriodTime(LocalTime.of(10, 0), LocalTime.of(10, 45))))
    private val course = Course(id = 11, courseId = 3, timetableId = 7, name = "课程",
        dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18)
    private val beforeSemester = Instant.parse("2026-08-30T00:00:00Z")

    @Test fun remainingSemesterIsSubmittedWithoutRequiringWeeklyAppWakeups() {
        val events = calendarOccurrences(settings, listOf(course), beforeSemester, zone)
        assertEquals(18, events.size)
        assertEquals(LocalDate.of(2026, 12, 28), Instant.ofEpochMilli(events.last().startMillis).atZone(zone).toLocalDate())
        assertTrue(events.all { it.reminderMinutes == 15 })
    }

    @Test fun weekParityAndIrregularTeachingWeeksArePreserved() {
        val odd = calendarOccurrences(settings, listOf(course.copy(weekType = WeekType.ODD.code)), beforeSemester, zone)
        assertEquals(9, odd.size)
        val selected = course.copy(selectedWeeksCsv = "2,5,8", weekType = WeekType.EVEN.code)
        val events = calendarOccurrences(settings, listOf(selected), beforeSemester, zone)
        assertEquals(listOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 19)),
            events.map { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() })
    }

    @Test fun pastReminderDeadlineDoesNotBecomeAnImmediateCatchupAlert() {
        val now = Instant.parse("2026-08-31T00:50:00Z") // 08:50 local, reminder deadline 08:45
        val event = calendarOccurrences(settings, listOf(course.copy(endWeek = 1)), now, zone).single()
        assertNull(event.reminderMinutes)
        assertNull(event.triggerMillis)
        assertEquals(LocalTime.of(9, 0), Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalTime())
    }

    @Test fun completedCoursesAndOtherTimetablesAreExcluded() {
        val events = calendarOccurrences(settings, listOf(course.copy(endWeek = 1),
            course.copy(id = 12, timetableId = 8), course.copy(id = 13, startSection = 99),
            course.copy(id = 14, duration = 0)), Instant.parse("2026-08-31T03:00:00Z"), zone)
        assertTrue(events.isEmpty())
    }

    @Test fun courseEditsKeepStableCalendarIdentityWhileChangingContentsAndReminderTime() {
        val original = calendarOccurrences(settings, listOf(course), beforeSemester, zone)
        val edited = calendarOccurrences(settings.copy(reminderMinutes = 30),
            listOf(course.copy(name = "新名称", teacher = "教师", location = "新教室")), beforeSemester, zone)
        assertEquals(original.map { it.key }, edited.map { it.key })
        assertEquals("新名称", edited.first().title)
        assertEquals("新教室", edited.first().location)
        assertEquals(original.first().triggerMillis!! - 900_000L, edited.first().triggerMillis)
    }

    @Test fun separateSessionsOfTheSameCourseNeverCollapseIntoOneCalendarEvent() {
        val events = calendarOccurrences(settings, listOf(course.copy(endWeek = 1),
            course.copy(id = 12, startSection = 2, duration = 1, endWeek = 1)), beforeSemester, zone)
        assertEquals(2, events.size)
        assertEquals(2, events.map { it.key }.distinct().size)
    }

    @Test fun overnightSessionsEndOnTheFollowingDay() {
        val late = settings.copy(periods = listOf(PeriodTime(LocalTime.of(23, 0), LocalTime.of(0, 30))))
        val event = calendarOccurrences(late, listOf(course.copy(duration = 1, endWeek = 1)), beforeSemester, zone).single()
        assertEquals(LocalDate.of(2026, 9, 1), Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalDate())
        assertEquals(90 * 60_000L, event.endMillis - event.startMillis)
    }

    @Test fun nonMondaySemesterStartUsesTheSameWeekDefinitionAsTheTimetable() {
        val events = calendarOccurrences(settings.copy(semesterStart = LocalDate.of(2026, 9, 2)),
            listOf(course.copy(startWeek = 2, endWeek = 2)), beforeSemester, zone)
        assertEquals(LocalDate.of(2026, 9, 7), Instant.ofEpochMilli(events.single().startMillis).atZone(zone).toLocalDate())
    }

    @Test fun existingInstallationsAndUnrecognizedPreferencesKeepAppReminders() {
        assertEquals(ReminderBackend.APP, AppSettings().reminderBackend)
        assertEquals(ReminderBackend.APP, ReminderBackend.fromKey(null))
        assertEquals(ReminderBackend.APP, ReminderBackend.fromKey("unknown"))
    }
}
