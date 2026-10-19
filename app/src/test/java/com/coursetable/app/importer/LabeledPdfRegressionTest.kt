package com.coursetable.app.importer

import com.coursetable.app.data.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class LabeledPdfRegressionTest {
    private fun pages(): List<List<TextToken>> {
        val fixture = javaClass.classLoader!!.getResourceAsStream("fixtures/labeled-grid-pdf-tokens.json")!!.bufferedReader().use { it.readText() }
        val pages = JSONArray(fixture)
        return (0 until pages.length()).map { index ->
            val tokens = pages.getJSONObject(index).getJSONArray("tokens")
            (0 until tokens.length()).map { tokens.getJSONObject(it).let { t -> TextToken(t.getDouble("x0").toFloat(), t.getDouble("y0").toFloat(), t.getDouble("x1").toFloat(), t.getDouble("y1").toFloat(), t.getString("text")) } }
        }
    }
    private fun parsed(totalWeeks: Int = 20): List<CandidateCourse> {
        val pages = pages(); val headers = LabeledTimetableParser.headers(pages.first())
        val result = mutableListOf<CandidateCourse>()
        pages.forEachIndexed { index, tokens -> result += StructuredTimetableParser.extract(tokens, 842f, 595f, AppSettings(totalWeeks = totalWeeks), index, if(index == 0) emptyList() else headers, knownTeachers = result.map { it.teacher }).candidates }
        return result
    }
    @Test fun originalPdfHasExactlyFifteenScheduledMeetingsAndThreeIncompletePractices() {
        val all = parsed()
        assertEquals(18, all.size)
        val regular = all.filter { it.dayOfWeek in 1..7 }
        assertEquals(15, regular.size); assertEquals(10, regular.map { it.name }.distinct().size)
        assertTrue(regular.all { it.fieldErrors(AppSettings(totalWeeks = 20)).isEmpty() })
        assertTrue(regular.none { it.name.contains("教学班") || it.name.contains("学分") })
        assertEquals(3, all.count { it.dayOfWeek == 0 && it.startSection == 0 })
    }
    @Test fun everyScheduledFieldMatchesThePdfIncludingWrappedTeachersAndLocations() {
        data class Expected(val name: String, val day: Int, val start: Int, val duration: Int, val weeks: List<Int>, val teacher: String, val room: String)
        val golden = listOf(
            Expected("通信电路",1,3,2,(1..15 step 2).toList(),"夏景","京江3号楼3205"),
            Expected("通信软件Java开发基础",1,5,2,(1..14).toList(),"单田华","计算机楼103"),
            Expected("移动互联网开发",1,9,3,(3..14).toList(),"单田华","三山楼202"),
            Expected("通信原理",2,1,2,(1..16).toList(),"郑召文,朱轶","计算机楼113"),
            Expected("现代交换技术",2,5,2,(1..8).toList(),"江晓明","计算机楼106"),
            Expected("智能通信技术基础",3,3,2,(1..12).toList(),"张文策","三山楼203"),
            Expected("模拟电子技术",3,9,2,(1..12).toList(),"刘战胜","电气楼105"),
            Expected("通信电路",4,1,2,(1..16).toList(),"夏景","京江2号楼2102"),
            Expected("通信软件Java开发基础",4,5,2,(1..14).toList(),"单田华","计算机楼103"),
            Expected("现代交换技术",4,7,2,(1..8).toList(),"江晓明","计算机楼106"),
            Expected("数字信号处理",4,9,3,(1..16).toList(),"宋雪桦","京江2号楼2111"),
            Expected("通信原理",5,3,2,(1..16).toList(),"朱轶,郑召文","电气楼105"),
            Expected("模拟电子技术",5,5,2,(1..12).toList(),"刘战胜","计算机楼113"),
            Expected("大学体育(V)(选项)",5,5,2,(9..16).toList(),"梁瑛","西山操场"),
            Expected("创业管理",6,6,1,listOf(7,13),"张海斌","讲堂群102")
        )
        val actual = parsed().filter { it.dayOfWeek in 1..7 }.map { Expected(it.name,it.dayOfWeek,it.startSection,it.duration,it.asCourse().scheduledWeeks(),it.teacher,it.location) }
        assertEquals(golden.toSet(),actual.toSet())
    }
    @Test fun sparseWeeksAndPracticesNeverBecomeAContinuousOrFabricatedSchedule() {
        val all = parsed(18)
        assertEquals(listOf(7,13),all.single { it.name == "创业管理" }.asCourse().scheduledWeeks())
        val practice = all.filter { it.dayOfWeek == 0 }
        assertEquals(setOf("通信原理课程设计","通信工程实践(5):可编程网络技术","通信工程实践(6):移动应用开发实践"),practice.map { it.name }.toSet())
        assertEquals(listOf(19,20),practice.last().asCourse().scheduledWeeks())
        assertEquals("单田华,朱轶",practice.last().teacher)
        assertTrue(practice.all { it.fieldErrors(AppSettings()).isNotEmpty() && it.issues.isNotEmpty() })
    }
    @Test fun teacherNamesNeverImplyOddWeeksAndTeacherOrderDoesNotSplitACourse() {
        assertEquals(listOf(Triple(1,14,0)),StructuredTimetableParser.weeks("1-14周/教师:单田华",20))
        assertTrue(teachersCompatible("郑召文,朱轶","朱轶、郑召文"))
        assertFalse(teachersCompatible("朱轶","郑召文,朱轶"))
    }
    @Test fun completeTextAndUnscheduledPracticesDoNotTriggerOcrForEmptyCells() {
        val source = pages(); val inherited = LabeledTimetableParser.headers(source.first())
        val all = mutableListOf<CandidateCourse>()
        source.forEachIndexed { index,tokens ->
            val result = StructuredTimetableParser.extract(tokens,842f,595f,AppSettings(),index,if(index==0) emptyList() else inherited,knownTeachers=all.map { it.teacher })
            val emptyCell = ImportRegion("empty",index,.88f,.8f,.98f,.9f,TimetableLayout.WEEKLY)
            assertFalse(VisualTimetableImporter.requiresOcrSupplement(tokens,result,listOf(emptyCell),842f,595f,AppSettings()))
            all += result.candidates
        }
    }
}
