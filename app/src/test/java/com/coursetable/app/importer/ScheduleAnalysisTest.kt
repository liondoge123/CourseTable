package com.coursetable.app.importer

import com.coursetable.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleAnalysisTest {
    private val settings = AppSettings()
    private val candidate = CandidateCourse("高等数学（实验）", teacher = "张三", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, weekType = 0)
    @Test fun duplicateDefaultsToSkipAndKeepsMeaningfulSuffix() {
        val existing = candidate.asCourse(1).copy(id = 11, courseId = 4)
        val normalized = candidate.copy(name = " 高等 数学(实验) ")
        val row = ScheduleAnalysis.analyze(listOf(normalized), listOf(existing), settings, ImportMode.APPEND).single()
        assertEquals(ImportStatus.DUPLICATE, row.status); assertFalse(row.included)
        assertNotEquals(normalizedName(candidate.name), normalizedName("高等数学"))
        assertEquals(ImportStatus.NORMAL, ScheduleAnalysis.analyze(listOf(candidate), listOf(existing), settings, ImportMode.OVERWRITE).single().status)
    }
    @Test fun equalWeekSetsAreDuplicatesAndGapsRemainDistinct() {
        val odd = candidate.copy(weekType = 1)
        val explicit = odd.copy(weekType = 0, selectedWeeksCsv = (1..16).filter { it % 2 == 1 }.joinToString(","))
        assertEquals(scheduleFingerprint(odd.asCourse()), scheduleFingerprint(explicit.asCourse()))
        assertNotEquals(scheduleFingerprint(candidate.asCourse()), scheduleFingerprint(candidate.copy(selectedWeeksCsv = "1,4,8").asCourse()))
    }
    @Test fun adjacentAndDisjointWeeksAreNotConflicts() {
        val course = candidate.asCourse()
        assertTrue(scheduleConflicts(course, listOf(course.copy(startSection = 3))).isEmpty())
        assertTrue(scheduleConflicts(course.copy(weekType = 1), listOf(course.copy(weekType = 2))).isEmpty())
        val collision = scheduleConflicts(course.withScheduledWeeks(listOf(1, 4, 8)), listOf(course.copy(startSection = 2).withScheduledWeeks(listOf(4, 5, 8)))).single()
        assertEquals(2..2, collision.sections); assertEquals(listOf(4, 8), collision.weeks)
    }
    @Test fun incompatibleOrAmbiguousParentsRequireChoice() {
        val existing = candidate.asCourse().copy(id = 1, courseId = 1)
        val later = candidate.copy(startSection = 4, teacher = "李四")
        assertTrue(ScheduleAnalysis.analyze(listOf(later), listOf(existing), settings, ImportMode.APPEND).single().requiresTarget)
        assertFalse(ScheduleAnalysis.analyze(listOf(later.copy(separateCourse = true)), listOf(existing), settings, ImportMode.APPEND).single().requiresTarget)
        assertTrue(ScheduleAnalysis.analyze(listOf(candidate.copy(startSection = 4)), listOf(existing, existing.copy(id = 2, courseId = 2)), settings, ImportMode.APPEND).single().requiresTarget)
    }
    @Test fun batchDedupAndSelectedInvalidRowsAreVisible() {
        val rows = ScheduleAnalysis.analyze(listOf(candidate, candidate, candidate.copy(dayOfWeek = 0)), emptyList(), settings, ImportMode.APPEND)
        assertEquals(listOf(ImportStatus.NORMAL, ImportStatus.DUPLICATE, ImportStatus.INVALID), rows.map { it.status })
        assertFalse(rows[1].included); assertTrue(rows[2].included)
    }
    @Test fun uniqueCompatibleTeacherIsGroupedEvenWhenAnotherSameNameCourseExists() {
        val math = candidate.asCourse().copy(id=1,courseId=1)
        val otherTeacher = math.copy(id=2,courseId=2,teacher="李四")
        val row = ScheduleAnalysis.analyze(listOf(candidate.copy(dayOfWeek=3)),listOf(math,otherTeacher),settings,ImportMode.APPEND).single()
        assertFalse(row.requiresTarget)
        assertEquals(1L,row.autoMergeTarget!!.courseId)
        assertTrue(ScheduleAnalysis.analyze(listOf(candidate.copy(dayOfWeek=3,teacher="")),listOf(math,otherTeacher),settings,ImportMode.APPEND).single().requiresTarget)
    }
    @Test fun retriesProtectOnlyEditedFieldsAndRetainIdentity() {
        val original = candidate.copy(name = "人工改名", draftId = "stable", editedFields = setOf("name", "weeks"), selectedWeeksCsv = "1,4,8")
        val recovered = preserveReviewedFields(original, candidate.copy(location = "A301", name = "识别名称"))
        assertEquals("人工改名", recovered.name); assertEquals("A301", recovered.location)
        assertEquals(listOf(1, 4, 8), recovered.asCourse().scheduledWeeks()); assertEquals("stable", recovered.draftId)
    }
    @Test fun oddWeekExclusionsAndMultipleDaysRespectOccurrenceCount() {
        val settings = AppSettings(semesterStart = LocalDate.of(2026, 9, 1), totalWeeks = 18)
        val event = IcsEvent("数学", date = LocalDate.of(2026, 9, 2), startTime = LocalTime.of(8, 0), endTime = LocalTime.of(9, 40),
            rrule = "FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=WE,FR", exdates = listOf(LocalDate.of(2026, 9, 16)))
        val result = IcsImporter.convert(listOf(event), settings)
        assertEquals(listOf(1), result.courses.first { it.dayOfWeek == 3 }.scheduledWeeks())
        assertEquals(listOf(1, 3), result.courses.first { it.dayOfWeek == 5 }.scheduledWeeks())
        assertNull(TimeMapper.map(LocalTime.of(6, 0), LocalTime.of(7, 0), settings.periods))
    }
    @Test fun unsupportedRecurrencesAndMalformedCsvStayEditable() {
        val event = IcsEvent("数学", date = LocalDate.of(2026, 9, 2), startTime = LocalTime.of(8, 0), endTime = LocalTime.of(9, 40), rrule = "FREQ=MONTHLY")
        val result = IcsImporter.convert(listOf(event), settings)
        assertEquals(1, result.courses.size); assertTrue(result.warnings.isNotEmpty())
        assertEquals(0, result.courses.single().startWeek)
        val csv = ExcelTimetableImporter.parseCsv("课程名称,星期,开始节数,结束节数,周数\n数学,周九,?,2,坏数据")
        assertEquals(1, csv.candidates.size); assertTrue(csv.candidates.single().fieldErrors(settings).isNotEmpty())
    }
    @Test fun backupRetainsGroupsAndDoesNotMergeLegacyRecords() {
        val courses = listOf(candidate.asCourse().copy(courseId = 10), candidate.asCourse().copy(courseId = 10, dayOfWeek = 3), candidate.asCourse().copy(courseId = 20, color = 123))
        val result = BackupManager.parse(BackupManager.export(listOf(TimetableBackup(Timetable(name = "测试"), courses)))).timetables.single().courses
        assertEquals(3, result.size); assertEquals(2, result.map { it.courseId }.distinct().size)
        assertEquals(result[0].courseId, result[1].courseId); assertNotEquals(result[0].courseId, result[2].courseId)
        assertEquals(123L, result[2].color)
    }
}
