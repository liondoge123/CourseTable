package com.coursetable.app.ui

import com.coursetable.app.data.Course
import org.junit.Assert.*
import org.junit.Test

class ManagedCourseGroupsTest {
    @Test fun groupingPreservesLogicalCoursesSessionOrderAndOriginalSort() {
        val input = (1..300).map { i -> Course(id = i.toLong(), courseId = if (i % 3 == 0) 0 else (i / 5 + 1).toLong(),
            name = "课程$i", dayOfWeek = i % 9, startSection = i % 12 + 1, duration = 2,
            startWeek = 1, endWeek = 16, location = "教室$i") }
        val original = input.groupBy { it.courseId.takeIf { id -> id > 0 } ?: -it.id }.values.map { group ->
            group.first().copy().also { it.additionalSessions = group.drop(1) }
        }.sortedWith(compareBy<Course> { it.dayOfWeek }.thenBy { it.startSection })
            .groupBy { it.dayOfWeek.coerceIn(1, 7) }
        val actual = groupedManagedCourses(input)
        assertEquals(original, actual)
        assertEquals(original.keys.toList(), actual.keys.toList())
        original.values.flatten().zip(actual.values.flatten()).forEach { (expected, course) ->
            assertEquals(expected.additionalSessions, course.additionalSessions)
        }
        assertTrue(input.all { it.additionalSessions == null })
        assertTrue(groupedManagedCourses(emptyList()).isEmpty())
    }
}
