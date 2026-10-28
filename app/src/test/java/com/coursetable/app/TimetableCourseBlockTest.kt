package com.coursetable.app

import com.coursetable.app.data.Course
import com.coursetable.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class TimetableCourseBlockTest {
    private fun item(id: Long, start: Int, duration: Int, day: Int = 1) = TimetableCourseItem(
        Course(id = id, name = "同名课程", dayOfWeek = day, startSection = start, duration = duration,
            startWeek = 1, endWeek = 18), true)

    @Test fun emptyAndSingleCourseKeepTheirGeometryAndIdentity() {
        assertTrue(timetableCourseBlocks(emptyList()).isEmpty())
        val original = item(9, 4, 3, 7)
        val block = timetableCourseBlocks(listOf(original)).single()
        assertEquals(listOf(original), block.items)
        assertEquals(7, block.dayOfWeek)
        assertEquals(4, block.startSection)
        assertEquals(7, block.endSectionExclusive)
        assertEquals(3, block.duration)
    }

    @Test fun exactContainedAndPartialOverlapsProduceOneBlockWithTheUnionSpan() {
        val base = item(1, 3, 2)
        for ((start, duration) in listOf(3 to 2, 2 to 4, 3 to 1, 2 to 2, 4 to 2)) {
            val other = item(2, start, duration)
            val block = timetableCourseBlocks(listOf(base, other)).single()
            assertEquals(setOf(1L, 2L), block.items.map { it.course.id }.toSet())
            assertEquals(minOf(3, start), block.startSection)
            assertEquals(maxOf(5, start + duration), block.endSectionExclusive)
        }
    }

    @Test fun chainedOverlapMergesTransitivelyButTouchingEdgesAndOtherDaysDoNot() {
        val items = listOf(item(1, 1, 2), item(2, 2, 2), item(3, 3, 2), item(4, 5, 2), item(5, 1, 4, 2))
        val blocks = timetableCourseBlocks(items.reversed())
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(4L), listOf(5L)), blocks.map { it.items.map { it.course.id } })
        assertEquals(1, blocks.first().startSection)
        assertEquals(5, blocks.first().endSectionExclusive)
    }

    @Test fun equalStartsUseEndThenSessionIdAndDoNotMergeSameNamedSessionsByName() {
        val items = listOf(item(3, 1, 3), item(2, 1, 2), item(1, 1, 2))
        val expected = timetableCourseBlocks(items)
        repeat(20) { seed ->
            assertEquals(expected, timetableCourseBlocks(items.shuffled(kotlin.random.Random(seed))))
        }
        assertEquals(listOf(1L, 2L, 3L), expected.single().items.map { it.course.id })
        assertEquals("1:1,2,3", expected.single().key)
        // A changed member must invalidate an already-open chooser snapshot.
        assertNotEquals(expected, timetableCourseBlocks(items.map {
            if (it.course.id == 2L) it.copy(course = it.course.copy(location = "新教室")) else it
        }))
    }

    @Test fun groupingPreservesWeekParitySparseSelectionsAndInactiveSuppression() {
        val odd = item(1, 1, 2).course.copy(weekType = 1)
        val sparse = item(2, 2, 2).course.copy(selectedWeeksCsv = "1,4,8")
        val future = item(3, 1, 3).course.copy(startWeek = 9)
        val free = item(4, 4, 2).course.copy(startWeek = 9)
        val index = TimetableCourseIndex(listOf(future, free, sparse, odd))
        val first = timetableCourseBlocks(index.forWeek(1, true, WeekFilter.ALL))
        assertEquals(listOf(listOf(1L, 2L), listOf(4L)), first.map { it.items.map { it.course.id } })
        assertFalse(first.last().items.single().scheduledThisWeek)
        assertEquals(listOf(listOf(2L)), timetableCourseBlocks(index.forWeek(4, false, WeekFilter.ALL))
            .map { it.items.map { it.course.id } })
        assertEquals(listOf(1L), timetableCourseBlocks(index.forWeek(3, false, WeekFilter.ODD))
            .single().items.map { it.course.id })
        assertEquals(listOf(2L), timetableCourseBlocks(index.forWeek(4, false, WeekFilter.EVEN))
            .single().items.map { it.course.id })
    }
}
