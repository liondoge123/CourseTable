package com.coursetable.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupGroupingTest {
    private fun row(name: String, key: String? = null) = JSONObject()
        .put("name", name).put("dayOfWeek", 1).put("startSection", 1).put("duration", 1)
        .put("startWeek", 1).put("endWeek", 18).apply { if (key != null) put("courseKey", key) }

    private fun parse(rows: List<JSONObject>) = BackupManager.parse(JSONObject()
        .put("app", "CourseTable").put("version", 3)
        .put("timetables", JSONArray().put(JSONObject().put("name", "课表")
            .put("semesterStart", "2026-09-01").put("courses", JSONArray(rows))))
        .toString()).timetables.single().courses

    @Test fun anonymousRecordsCannotCollideWithExplicitGroups() {
        val courses = parse(listOf(row("A", "group-a"), row("独立一"), row("独立二"),
            row("A", "group-a"), row("B", "group-b")))
        assertEquals(courses[0].courseId, courses[3].courseId)
        assertEquals(4, courses.map { it.courseId }.toSet().size)
        assertTrue(courses.all { it.courseId > 0 })
    }

    @Test fun maximumSizedBackupRetainsEveryGroupAndSessionInOrder() {
        val courses = parse((0 until ImportPolicy.MAX_OUTPUT_COURSES).map { i ->
            row("课程 ${i % 137}", "key-${i % 137}")
        })
        assertEquals(ImportPolicy.MAX_OUTPUT_COURSES, courses.size)
        assertEquals(137, courses.map { it.courseId }.toSet().size)
        courses.forEachIndexed { i, course ->
            assertEquals("课程 ${i % 137}", course.name)
            assertEquals(i % 137 + 1L, course.courseId)
        }
    }
}
