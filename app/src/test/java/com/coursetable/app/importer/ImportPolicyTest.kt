package com.coursetable.app.importer

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPolicyTest {

    @Test
    fun detectsTypesFromContentBeforeExtension() {
        assertEquals(
            DetectedImportType.PDF,
            ImportPolicy.detect("%PDF-1.7".toByteArray(), "text/plain", "fake.txt")
        )
        assertEquals(
            DetectedImportType.JSON,
            ImportPolicy.detect("  {\"app\":\"CourseTable\"}".toByteArray(), "text/plain", "backup.txt")
        )
        assertEquals(
            DetectedImportType.ICS,
            ImportPolicy.detect("BEGIN:VCALENDAR\nVERSION:2.0".toByteArray(), null, "unknown")
        )
    }

    @Test
    fun rejectsSpoofedStructuredFiles() {
        assertThrows(ImportRejectedException::class.java) {
            ImportPolicy.detect("not a pdf".toByteArray(), "application/pdf", "attack.pdf")
        }
        assertThrows(ImportRejectedException::class.java) {
            ImportPolicy.detect("not a zip".toByteArray(), null, "attack.xlsx")
        }
    }

    @Test
    fun boundedReaderRejectsUnknownLengthStreams() {
        assertThrows(ImportRejectedException::class.java) {
            ImportPolicy.run {
                ByteArrayInputStream(ByteArray(33)).readBytesLimited(32)
            }
        }
    }

    @Test
    fun backupCapsTablesCoursesAndTextFields() {
        val root = org.json.JSONObject().put("app", "CourseTable")
        val tables = org.json.JSONArray()
        val courses = org.json.JSONArray()
        repeat(ImportPolicy.MAX_OUTPUT_COURSES + 1) {
            courses.put(
                org.json.JSONObject()
                    .put("name", "课".repeat(ImportPolicy.MAX_TEXT_FIELD + 20))
                    .put("dayOfWeek", 1)
            )
        }
        tables.put(org.json.JSONObject().put("name", "测试").put("courses", courses))
        root.put("timetables", tables)

        val parsed = BackupManager.parse(root.toString())
        assertEquals(ImportPolicy.MAX_OUTPUT_COURSES, parsed.timetables.single().courses.size)
        assertEquals(ImportPolicy.MAX_TEXT_FIELD, parsed.timetables.single().courses.first().name.length)
        assertTrue(parsed.warnings.any { it.contains("课程数量超过") })
    }
}
