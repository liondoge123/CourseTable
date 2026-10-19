package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ScheduleIndexRegressionTest {
    @Test fun indexedAnalysisMatchesPreviousTraversalAcrossModesGroupsAndInvalidRows() {
        repeat(80) { seed ->
            val random = Random(seed)
            fun candidate(index: Int) = CandidateCourse(
                name = if (index % 17 == 0) "" else listOf("数学", "ＭＡＴＨ", "math", "英语")[random.nextInt(4)],
                teacher = listOf("", "张三", "李四")[random.nextInt(3)], dayOfWeek = random.nextInt(0, 8),
                startSection = random.nextInt(0, 12), duration = random.nextInt(1, 4), startWeek = random.nextInt(1, 10),
                endWeek = random.nextInt(8, 19), weekType = random.nextInt(3),
                selectedWeeksCsv = if (random.nextBoolean()) "1,4,8,16" else "", draftId = "$seed-$index",
                groupKey = if (random.nextBoolean()) "g${random.nextInt(3)}" else null,
                included = listOf(null, true, false)[random.nextInt(3)], separateCourse = random.nextInt(8) == 0,
                updateDuplicate = random.nextInt(7) == 0, targetCourseId = if (random.nextInt(5) == 0) random.nextLong(1, 7) else null)
            val existing = (0 until 18).map { candidate(it).asCourse(1).copy(id = it + 1L, courseId = it / 2 + 1L) }
            val candidates = (0 until 45).map { candidate(it) }
            for (mode in ImportMode.entries) assertEquals("seed=$seed mode=$mode",
                ReferenceScheduleAnalysis.analyze(candidates, existing, AppSettings(), mode),
                ScheduleAnalysis.analyze(candidates, existing, AppSettings(), mode))
        }
    }

    @Test fun benchmarkSparseBulkAnalysisWithoutWallClockAssertions() {
        val settings = AppSettings(totalWeeks = 30)
        fun input(size: Int) = (0 until size).map { i ->
            val week = i / (7 * settings.periods.size) + 1
            CandidateCourse("课程$i", dayOfWeek = i % 7 + 1, startSection = i / 7 % settings.periods.size + 1,
                duration = 1, startWeek = week, endWeek = week, weekType = 0)
        }
        repeat(2) {
            ReferenceScheduleAnalysis.analyze(input(40), emptyList(), settings, ImportMode.NEW)
            ScheduleAnalysis.analyze(input(40), emptyList(), settings, ImportMode.NEW)
        }
        for (size in listOf(500, 2000)) {
            val candidates = input(size)
            val before = System.nanoTime()
            val expected = ReferenceScheduleAnalysis.analyze(candidates, emptyList(), settings, ImportMode.NEW)
            val middle = System.nanoTime()
            val actual = ScheduleAnalysis.analyze(candidates, emptyList(), settings, ImportMode.NEW)
            val after = System.nanoTime()
            assertEquals(expected, actual)
            assertTrue(actual.all { it.status == ImportStatus.NORMAL })
            println("ANALYSIS_BENCHMARK {\"records\":$size,\"legacyMs\":${(middle-before)/1_000_000},\"indexedMs\":${(after-middle)/1_000_000}}")
        }
    }
}
