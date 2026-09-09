package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.Timetable
import com.coursetable.app.data.WeekType
import com.coursetable.app.util.WeekUtils
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportLogicTest {

    private val sampleIcs = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Test//EN
        BEGIN:VEVENT
        UID:1
        SUMMARY:高等数学
        LOCATION:教学楼A301
        DTSTART:20260902T080000
        DTEND:20260902T094000
        RRULE:FREQ=WEEKLY;COUNT=16;BYDAY=WE
        END:VEVENT
        BEGIN:VEVENT
        UID:2
        SUMMARY:大学英语
        LOCATION:外语楼203
        DTSTART:20260904T140000
        DTEND:20260904T154000
        RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=9
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun parserExtractsWeeklyEvents() {
        val result = IcsParser.parse(sampleIcs)
        assertEquals(2, result.events.size)
        val first = result.events[0]
        assertEquals("高等数学", first.summary)
        assertEquals("教学楼A301", first.location)
        assertEquals(LocalDate.of(2026, 9, 2), first.date)
        assertEquals(LocalTime.of(8, 0), first.startTime)
        assertEquals(LocalTime.of(9, 40), first.endTime)
        assertTrue(first.rrule!!.contains("BYDAY=WE"))
    }

    @Test
    fun rruleParsing() {
        val r = IcsParser.parseRrule("FREQ=WEEKLY;INTERVAL=2;COUNT=9")
        assertEquals(2, r.interval)
        assertEquals(9, r.count)
        assertTrue(r.parsed)

        val r2 = IcsParser.parseRrule("FREQ=WEEKLY;COUNT=16;BYDAY=MO,WE,FR")
        assertEquals(listOf(1, 3, 5), r2.byDays)
    }

    @Test
    fun importerMapsWeeklyToAllWeeks() {
        val settings = AppSettings(semesterStart = LocalDate.of(2026, 9, 1), totalWeeks = 18)
        val parsed = IcsParser.parse(sampleIcs)
        val outcome = IcsImporter.convert(parsed.events, settings)

        val math = outcome.courses.first { it.name == "高等数学" }
        assertEquals(3, math.dayOfWeek) // 周三
        assertEquals(1, math.startSection) // 08:00
        assertEquals(2, math.duration) // 2 节
        assertEquals(WeekType.ALL.code, math.weekType)
        assertEquals(1, math.startWeek) // 2026-09-02 = 第1周
        assertEquals(16, math.endWeek) // COUNT=16
        assertEquals("教学楼A301", math.location)
    }

    @Test
    fun importerMapsEveryOtherWeekToOdd() {
        val settings = AppSettings(semesterStart = LocalDate.of(2026, 9, 1), totalWeeks = 18)
        val parsed = IcsParser.parse(sampleIcs)
        val outcome = IcsImporter.convert(parsed.events, settings)

        val english = outcome.courses.first { it.name == "大学英语" }
        // 2026-09-04 是第 1 周(周5), 隔周上课 → 单周
        assertEquals(WeekType.ODD.code, english.weekType)
        assertEquals(5, english.dayOfWeek)
        assertEquals(5, english.startSection) // 14:00 → 第5节
        // COUNT=9 从第1周隔周: 1,3,5,7,9,11,13,15,17
        assertEquals(1, english.startWeek)
        assertEquals(17, english.endWeek)
    }

    @Test
    fun backupRoundTrip() {
        val settings = AppSettings(
            semesterStart = LocalDate.of(2026, 3, 1),
            totalWeeks = 12,
            periods = listOf(
                PeriodTime(LocalTime.of(8, 0), LocalTime.of(8, 45)),
                PeriodTime(LocalTime.of(9, 0), LocalTime.of(9, 45))
            )
        )
        val courses = listOf(
            com.coursetable.app.data.Course(
                id = 0,
                name = "数据结构",
                teacher = "王老师",
                location = "X302",
                dayOfWeek = 2,
                startSection = 3,
                duration = 2,
                startWeek = 1,
                endWeek = 12,
                weekType = WeekType.ALL.code,
                color = 0xFF4B6EAF
            )
        )
        val timetable = Timetable(
            name = "测试课表",
            semesterStartEpochDay = settings.semesterStart.toEpochDay(),
            totalWeeks = settings.totalWeeks,
            periodsCsv = Timetable.serializePeriods(settings.periods),
            periodDurationMinutes = settings.periodDurationMinutes
        )
        val json = BackupManager.export(listOf(TimetableBackup(timetable, courses)))
        val data = BackupManager.parse(json)
        assertEquals(1, data.timetables.size)
        assertEquals(1, data.timetables[0].courses.size)
        assertEquals("数据结构", data.timetables[0].courses[0].name)
        assertEquals(12, data.timetables[0].timetable.totalWeeks)
        assertEquals(2, data.timetables[0].timetable.periods().size)
        assertEquals(WeekType.ALL.code, data.timetables[0].courses[0].weekType)
    }

    @Test
    fun weekUtils() {
        val start = LocalDate.of(2026, 9, 1) // 周二
        assertEquals(1, WeekUtils.weekOf(start, LocalDate.of(2026, 9, 1)))
        assertEquals(2, WeekUtils.weekOf(start, LocalDate.of(2026, 9, 8)))
        assertEquals(3, WeekUtils.weekOf(start, LocalDate.of(2026, 9, 15)))
        assertTrue(WeekUtils.weekRangeHasOdd(1, 2))
        assertTrue(WeekUtils.weekRangeHasEven(1, 2))
    }

    @Test
    fun weekStartDateHandlesNonMondaySemesterStart() {
        val start = LocalDate.of(2026, 9, 1) // 周二
        assertEquals(LocalDate.of(2026, 8, 31), WeekUtils.weekStartDate(start, 1))
        assertEquals(LocalDate.of(2026, 9, 7), WeekUtils.weekStartDate(start, 2))
        assertEquals(LocalDate.of(2026, 9, 1), WeekUtils.dateOfWeek(start, 1, 2))
        assertEquals(LocalDate.of(2026, 9, 6), WeekUtils.dateOfWeek(start, 1, 7))
    }
}
