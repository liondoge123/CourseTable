package com.coursetable.app.importer

import com.coursetable.app.data.Course
import org.junit.Assert.*
import org.junit.Test

class TeacherMatchingTest {
    private fun parent(id: Long, teacher: String) = Course(id = id, courseId = id, name = "数学", teacher = teacher,
        dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18)
    @Test fun uniqueMatchPreservesBlankNormalizationMultiplicityAndAmbiguityRules() {
        val teachers = listOf("", "  ", "张三", "李四", "张三、李四", "李四;张三", "张三,张三", ",；", "Ａlice", "alice")
        for (teacher in teachers) {
            for (first in teachers) {
                for (second in teachers) {
                    val parents = listOf(parent(1, first), parent(2, second))
                    fun reference(a: String, b: String): Boolean {
                        fun names(s: String) = s.split(Regex("[,，、;；]")).map(::normalizedName).filter { it.isNotEmpty() }.sorted()
                        return a.isBlank() || b.isBlank() || names(a) == names(b)
                    }
                    assertEquals(parents.filter { reference(teacher, it.teacher) }.singleOrNull(), uniqueTeacherMatch(parents, teacher))
                    assertEquals(reference(teacher, first), teachersCompatible(teacher, first))
                }
            }
        }
        assertNull(uniqueTeacherMatch(emptyList(), "张三"))
        assertFalse(teachersCompatible(",；", "张三"))
        assertFalse(teachersCompatible("张三,张三", "张三"))
    }

    @Test fun ambiguousMatchingStopsBeforeInspectingRemainingParents() {
        val parents = object : AbstractList<Course>() {
            override val size = 10_000
            override fun get(index: Int): Course {
                check(index < 2) { "Ambiguous matching must stop after the second match" }
                return parent(index.toLong() + 1, "张三")
            }
        }
        assertNull(uniqueTeacherMatch(parents, "张三"))
        assertNull(uniqueTeacherMatch(parents, ""))
    }
}
