package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.WeekType
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExporterTest {

    private val settings = AppSettings(
        semesterStart = LocalDate.of(2026, 9, 1),
        totalWeeks = 18,
        periods = listOf(
            PeriodTime(LocalTime.of(8, 0), LocalTime.of(8, 45)),
            PeriodTime(LocalTime.of(8, 55), LocalTime.of(9, 40))
        )
    )

    @Test
    fun exportsAndReimportsWeeklyCourse() {
        val course = Course(
            id = 1,
            name = "高等数学",
            teacher = "王老师",
            location = "A301",
            dayOfWeek = 3,
            startSection = 1,
            duration = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL.code,
            color = 0xFF4B6EAF
        )
        val ics = IcsExporter.export(settings, listOf(course))
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("SUMMARY:高等数学"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=WE;COUNT=16"))

        val parsed = IcsParser.parse(ics)
        assertEquals(1, parsed.events.size)
        val outcome = IcsImporter.convert(parsed.events, settings)
        assertEquals(1, outcome.courses.size)
        val reimported = outcome.courses.first()
        assertEquals("高等数学", reimported.name)
        assertEquals(3, reimported.dayOfWeek)
        assertEquals(1, reimported.startWeek)
        assertEquals(16, reimported.endWeek)
    }

    @Test
    fun exportsBiweeklyOddCourse() {
        val course = Course(
            id = 2,
            name = "大学英语",
            dayOfWeek = 5,
            startSection = 1,
            duration = 2,
            startWeek = 1,
            endWeek = 17,
            weekType = WeekType.ODD.code
        )
        val ics = IcsExporter.export(settings, listOf(course))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=FR;COUNT=9"))
    }

    @Test
    fun parserHandlesTzidParameter() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:1
            SUMMARY:测试课
            DTSTART;TZID=Asia/Shanghai:20260902T080000
            DTEND;TZID=Asia/Shanghai:20260902T094000
            RRULE:FREQ=WEEKLY;COUNT=16;BYDAY=WE
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val parsed = IcsParser.parse(ics)
        assertEquals(1, parsed.events.size)
        assertEquals(LocalDate.of(2026, 9, 2), parsed.events[0].date)
        assertEquals(LocalTime.of(8, 0), parsed.events[0].startTime)
    }

    @Test
    fun importerSplitsExcludedWeeks() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:1
            SUMMARY:高等数学
            DTSTART:20260902T080000
            DTEND:20260902T094000
            RRULE:FREQ=WEEKLY;COUNT=16;BYDAY=WE
            EXDATE:20260930T080000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val parsed = IcsParser.parse(ics)
        val outcome = IcsImporter.convert(parsed.events, settings)
        // 2026-09-30 在第 5 周，应拆成 1-4 和 6-16 两段
        assertEquals(2, outcome.courses.size)
        val sorted = outcome.courses.sortedBy { it.startWeek }
        assertEquals(1, sorted[0].startWeek)
        assertEquals(4, sorted[0].endWeek)
        assertEquals(6, sorted[1].startWeek)
        assertEquals(16, sorted[1].endWeek)
        assertEquals(3, sorted[0].dayOfWeek)
    }
}
