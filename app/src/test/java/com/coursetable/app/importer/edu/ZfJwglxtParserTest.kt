package com.coursetable.app.importer.edu

import com.coursetable.app.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZfJwglxtParserTest {

    private val sampleJson = """
        {
          "kbList": [
            {"kcmc":"高等数学","xqj":"1","jcs":"1-2","zcd":"1-16周","cdmc":"A301","xm":"王老师"},
            {"kcmc":"大学英语","xqj":"3","jcs":"3-4","zcd":"1-16周(单)","cdmc":"外语楼203","xm":"李老师"},
            {"kcmc":"体育","xqj":"5","jcs":"7","zcd":"1,3,5周","cdmc":"操场","xm":""}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesKbList() {
        val result = ZfJwglxtParser.parseScheduleJson(sampleJson)
        assertTrue(result.warnings.isEmpty())
        assertEquals(3, result.candidates.size)

        val math = result.candidates.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startSection)
        assertEquals(2, math.duration)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
        assertEquals(WeekType.ALL.code, math.weekType)
        assertEquals("A301", math.location)
        assertEquals("王老师", math.teacher)

        val english = result.candidates.first { it.name == "大学英语" }
        assertEquals(3, english.dayOfWeek)
        assertEquals(3, english.startSection)
        assertEquals(WeekType.ODD.code, english.weekType)

        val pe = result.candidates.first { it.name == "体育" }
        assertEquals(1, pe.startWeek)
        assertEquals(5, pe.endWeek)
    }

    @Test
    fun parseWeeksHandlesVariousFormats() {
        assertEquals(listOf(Triple(1, 16, 0)), ZfJwglxtParser.parseWeeks("1-16周"))
        assertEquals(listOf(Triple(1, 16, 1)), ZfJwglxtParser.parseWeeks("1-16周(单)"))
        assertEquals(listOf(Triple(1, 16, 2)), ZfJwglxtParser.parseWeeks("1-16周(双)"))
        assertEquals(listOf(Triple(1, 1, 0), Triple(3, 3, 0), Triple(5, 5, 0)), ZfJwglxtParser.parseWeeks("1,3,5周"))
        assertEquals(listOf(Triple(1, 8, 0), Triple(10, 16, 0)), ZfJwglxtParser.parseWeeks("1-8周,10-16周"))
    }

    @Test
    fun emptyKbListReturnsWarning() {
        val result = ZfJwglxtParser.parseScheduleJson("{}")
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun parsesUjsJcField() {
        val json = """
            {
              "kbList": [
                {"kcmc":"嵌入式系统","xqj":"3","jc":"1-2节","zcd":"2-14周(双)","cdmc":"京江2号楼2206","xm":"刘敏,陈向益"}
              ]
            }
        """.trimIndent()
        val result = ZfJwglxtParser.parseScheduleJson(json)
        assertEquals(1, result.candidates.size)
        val c = result.candidates[0]
        assertEquals("嵌入式系统", c.name)
        assertEquals(3, c.dayOfWeek)
        assertEquals(1, c.startSection)
        assertEquals(2, c.duration)
        assertEquals(2, c.startWeek)
        assertEquals(14, c.endWeek)
        assertEquals(WeekType.EVEN.code, c.weekType)
        assertEquals("京江2号楼2206", c.location)
        assertEquals("刘敏,陈向益", c.teacher)
    }
}
