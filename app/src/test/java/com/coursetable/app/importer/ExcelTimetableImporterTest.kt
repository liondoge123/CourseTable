package com.coursetable.app.importer

import com.coursetable.app.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelTimetableImporterTest {

    private val sampleCsv = """
        课程名称,星期,开始节数,结束节数,老师,地点,周数
        高等数学,1,1,2,张三,逸夫楼101,1-5、7-11单
        大学计算机,5,1,2,无,无,1-16
        有机化学,4,3,4,李四,逸夫楼108,1-16双
    """.trimIndent()

    @Test
    fun csvParsesWakeUpFormat() {
        val result = ExcelTimetableImporter.parseCsv(sampleCsv)
        assertTrue(result.warnings.isEmpty())
        assertTrue(result.candidates.isNotEmpty())

        val math = result.candidates.filter { it.name == "高等数学" }
        assertEquals(2, math.size)
        val seg1 = math.first { it.startWeek == 1 }
        assertEquals(5, seg1.endWeek)
        assertEquals(WeekType.ALL.code, seg1.weekType)
        val seg2 = math.first { it.startWeek == 7 }
        assertEquals(11, seg2.endWeek)
        assertEquals(WeekType.ODD.code, seg2.weekType)
        assertEquals(1, seg1.dayOfWeek)
        assertEquals(1, seg1.startSection)
        assertEquals(2, seg1.duration)
        assertEquals("逸夫楼101", seg1.location)
        assertEquals("张三", seg1.teacher)
    }

    @Test
    fun csvParsesSingleRangeAndEvenWeek() {
        val result = ExcelTimetableImporter.parseCsv(sampleCsv)
        val computer = result.candidates.first { it.name == "大学计算机" }
        assertEquals(1, computer.startWeek)
        assertEquals(16, computer.endWeek)
        assertEquals(WeekType.ALL.code, computer.weekType)
        assertEquals("", computer.location)
        assertEquals("", computer.teacher)

        val chem = result.candidates.first { it.name == "有机化学" }
        assertEquals(WeekType.EVEN.code, chem.weekType)
        assertEquals(3, chem.startSection)
        assertEquals(2, chem.duration)
    }

    @Test
    fun weekSegmentsParsing() {
        assertEquals(listOf(Triple(1, 16, 0)), ExcelTimetableImporter.parseWeekSegments("1-16"))
        assertEquals(listOf(Triple(1, 16, 1)), ExcelTimetableImporter.parseWeekSegments("1-16单"))
        assertEquals(listOf(Triple(1, 16, 2)), ExcelTimetableImporter.parseWeekSegments("1-16双"))
        assertEquals(
            listOf(Triple(1, 5, 0), Triple(7, 11, 1)),
            ExcelTimetableImporter.parseWeekSegments("1-5、7-11单")
        )
        assertEquals(listOf(Triple(7, 7, 0)), ExcelTimetableImporter.parseWeekSegments("7"))
    }

    @Test
    fun gridParsesCourses() {
        val grid = listOf(
            listOf("节次", "星期一", "星期二"),
            listOf("第1节", "高等数学\n张三\n1-16周\n逸夫楼101", null),
            listOf("第2节", null, "大学物理\n1-16周第3-4节")
        )
        val result = ExcelTimetableImporter.parseGrid(grid)
        assertTrue(result.candidates.isNotEmpty())
        val math = result.candidates.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startSection)
        assertEquals("逸夫楼101", math.location)
    }

    @Test
    fun csvRequiresHeader() {
        val result = ExcelTimetableImporter.parseCsv("1,2,3")
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }
}
