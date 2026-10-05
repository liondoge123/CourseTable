package com.coursetable.app.data

import com.coursetable.app.importer.BackupManager
import com.coursetable.app.importer.TimetableBackup
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class CourseWeeksTest {
    private val course = Course(name = "数学", dayOfWeek = 1, startSection = 1, duration = 1,
        startWeek = 1, endWeek = 18)

    @Test fun regularSelectionsKeepRangeAndParity() {
        val weekly = course.withScheduledWeeks((3..8).toList())
        assertEquals("", weekly.selectedWeeksCsv)
        assertEquals(3, weekly.startWeek)
        assertEquals(8, weekly.endWeek)
        val odd = course.withScheduledWeeks(listOf(3, 5, 7))
        assertEquals(WeekType.ODD.code, odd.weekType)
        assertEquals("", odd.selectedWeeksCsv)
        assertEquals(listOf(3, 5, 7), odd.scheduledWeeks())
        assertEquals(WeekType.EVEN.code, course.withScheduledWeeks(listOf(2, 4, 6)).weekType)
    }

    @Test fun irregularWeeksControlVisibilityAndLastWeek() {
        val custom = course.withScheduledWeeks(listOf(8, 1, 2, 8, 5))
        assertEquals("1,2,5,8", custom.selectedWeeksCsv)
        assertEquals("第 1–2、5、8 周", custom.weeksLabel())
        assertTrue(custom.visibleOnWeek(5))
        assertFalse(custom.visibleOnWeek(6))
        assertEquals(8, custom.lastWeek())
        assertEquals(listOf(1, 2, 5, 8), custom.scheduledWeeks())
    }

    @Test fun legacyParityWithOppositeBoundaryStillWorks() {
        val odd = course.copy(startWeek = 2, endWeek = 8, weekType = WeekType.ODD.code)
        assertEquals(listOf(3, 5, 7), odd.scheduledWeeks())
        assertFalse(odd.visibleOnWeek(2))
        assertEquals(7, odd.lastWeek())
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySelectionCannotBeSaved() { course.withScheduledWeeks(emptyList()) }

    @Test fun backupRoundTripKeepsGapsAndReadsOlderBackups() {
        val custom = course.withScheduledWeeks(listOf(1, 4, 8))
        val table = Timetable(name = "秋季", semesterStartEpochDay = 20000,
            totalWeeks = 18, periodsCsv = Timetable.serializePeriods(AppSettings.defaultPeriods()),
            periodDurationMinutes = 45)
        val backup = BackupManager.export(listOf(TimetableBackup(table, listOf(custom))))
        val restored = BackupManager.parse(backup).timetables.single().courses.single()
        assertEquals(listOf(1, 4, 8), restored.scheduledWeeks())
        val legacy = JSONObject(BackupManager.export(listOf(TimetableBackup(table, listOf(course)))))
        legacy.put("version", 2)
        legacy.getJSONArray("timetables").getJSONObject(0).getJSONArray("courses")
            .getJSONObject(0).remove("selectedWeeksCsv")
        assertEquals(course.scheduledWeeks(), BackupManager.parse(legacy.toString()).timetables.single().courses.single().scheduledWeeks())
    }
}
