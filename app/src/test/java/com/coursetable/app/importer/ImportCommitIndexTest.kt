package com.coursetable.app.importer

import com.coursetable.app.data.Course
import org.junit.Assert.*
import org.junit.Test

class ImportCommitIndexTest {
    @Test fun teacherMatchingUsesCurrentParentMetadataAndPreservesAmbiguity() {
        val index = ImportCommitIndex(listOf(first, first.copy(id = 2, courseId = 2, teacher = "李四")))
        assertNull(index.matchingParent("数学", "李四")) // Blank teacher is also compatible.
        index.save(first.copy(teacher = "张三"))
        assertEquals(2L, index.matchingParent("数 学", "李四")!!.courseId)
        assertEquals(1L, index.matchingParent("数学", "张三")!!.courseId)
        assertNull(index.matchingParent("数学", ""))
        index.save(first.copy(name = "物理", teacher = "张三"))
        assertNull(index.matchingParent("数学", "张三"))
        assertEquals(1L, index.matchingParent("物理", "张三")!!.courseId)
    }
    private val first = Course(id = 1, courseId = 1, timetableId = 1, name = "数学", teacher = "", location = "A301",
        dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18, color = 123)

    @Test fun metadataUpdatesReachOtherSessionsWithoutReplacingTheirLocation() {
        val second = first.copy(id = 2, dayOfWeek = 3, location = "B202")
        val index = ImportCommitIndex(listOf(first, second))
        index.save(first.copy(teacher = "张三", location = "C303"))
        val incoming = second.copy(id = 0)
        val duplicate = index.duplicate(incoming, null, null, false)!!
        assertEquals("张三", duplicate.teacher)
        assertEquals("B202", duplicate.location)
        assertEquals(123L, duplicate.color)
        assertEquals("张三", index.named("数学").single().teacher)
    }

    @Test fun duplicateOrderMatchesParentThenSessionOrderAndGroupBoundaries() {
        val other = first.copy(id = 2, courseId = 2, teacher = "李四")
        val index = ImportCommitIndex(listOf(other))
        index.save(first.copy(id = 10))
        assertEquals(1L, index.duplicate(first, null, null, false)!!.courseId)
        assertEquals(2L, index.duplicate(first, 2, null, false)!!.courseId)
        assertNull(index.duplicate(first, null, null, true))
        assertEquals(2L, index.duplicate(first, null, 2, true)!!.courseId)
    }

    @Test fun parentRenameReindexesEverySession() {
        val second = first.copy(id = 3, dayOfWeek = 3)
        val index = ImportCommitIndex(listOf(first, second))
        index.save(first.copy(name = "物理"))
        assertTrue(index.named("数学").isEmpty())
        assertNull(index.duplicate(second, null, null, false))
        assertEquals("物理", index.duplicate(second.copy(name = "物理"), null, null, false)!!.name)
    }
}
