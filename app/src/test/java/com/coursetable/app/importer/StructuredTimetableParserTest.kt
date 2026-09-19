package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import org.junit.Assert.*
import org.junit.Test

class StructuredTimetableParserTest {
    private val settings = AppSettings(totalWeeks = 18)
    private fun token(x: Float, top: Float, text: String) = TextToken(x - 25, 1025 - top - 18, x + 25, 1025 - top, text)
    private val headers = listOf(token(80f, 85f, "选课编号"), token(210f, 85f, "课程代码"), token(390f, 85f, "课程名称"), token(540f, 85f, "班号"), token(640f, 85f, "开课学院"), token(735f, 85f, "任课教师"), token(835f, 85f, "学分"), token(935f, 85f, "性质"), token(1120f, 85f, "上课时间地点"))
    @Test fun screenshotTranscriptionProducesFourteenRecords() {
        // Transcribed text/geometry from the supplied image; this tests structure independently of OCR accuracy.
        val names = listOf("自动控制原理B", "高电压与绝缘技术", "体育健康课程Ⅰ", "模拟集成电路设计", "嵌入式系统设计", "电力电子技术（含实", "专业应用创新实践Ⅱ", "功率半导体器件原理", "EDA技术", "形势与政策Ⅴ", "中国古代史")
        val edgesTop = listOf(114f, 183f, 252f, 321f, 390f, 460f, 583f, 708f, 777f, 847f, 916f, 986f)
        val meetings = listOf(listOf("1-17周 星期一 1-2节"), listOf("1-17周 星期二 3-4节"), listOf("1-17周 星期三 3-4节"), listOf("1-17周 星期四 3-5节"), listOf("1-17周 星期二 9-10节"), listOf("1-17周 星期二 6-7节", "1-17周 星期四 1-2节"), listOf("2-17周 星期五 6-7节"), listOf("1-17周 星期五 3-5节"), listOf("1-17周 星期三 6-7节"), listOf("4-6周 星期四 9-10节"), listOf("4、9-10、14-16周 星期三 11-12节"))
        val body = mutableListOf<TextToken>()
        names.forEachIndexed { i, name ->
            val center = (edgesTop[i] + edgesTop[i + 1]) / 2
            body.add(token(390f, center, name))
            if (i == 5) body.add(token(390f, center + 23, "验）"))
            body.add(token(735f, center, "张三"))
            meetings[i].forEachIndexed { j, time ->
                val y = if (meetings[i].size == 1) center - 20 else edgesTop[i] + 14 + j * 55
                body.add(token(1120f, y, time)); body.add(token(1120f, y + 25, "X1412[犀浦]"))
            }
        }
        val result = StructuredTimetableParser.extract(headers + body, 1280f, 1025f, settings, rowEdges = edgesTop.map { 1025 - it })
        assertEquals(14, result.candidates.size)
        assertEquals(11, result.candidates.map { it.name }.distinct().size)
        assertEquals(2, result.candidates.count { it.name.startsWith("电力电子技术") })
        assertEquals(listOf(4 to 4, 9 to 10, 14 to 16), result.candidates.filter { it.name == "中国古代史" }.map { it.startWeek to it.endWeek })
        assertTrue(result.candidates.none { it.needsReview })
        assertEquals(11, result.regions.size)
    }
    @Test fun scatteredWeeksAndFullWidthPunctuation() {
        assertEquals(listOf(Triple(4, 4, 0), Triple(9, 10, 0), Triple(14, 16, 0)), StructuredTimetableParser.weeks("４，９～１０，１４—１６周 星期三１１－１２节", 18))
        assertEquals(listOf(Triple(1, 17, 1)), StructuredTimetableParser.weeks("1-17周(单)", 18))
        assertEquals(listOf(Triple(2, 18, 2)), StructuredTimetableParser.weeks("2-18周双周", 18))
        assertEquals(listOf(Triple(1, 8, 0), Triple(10, 17, 0)), StructuredTimetableParser.weeks("1-8周、10-17周", 18))
    }
    @Test fun meetingWeekdaysAreNeverWeeklyHeaders() {
        val result = StructuredTimetableParser.extract(listOf(token(1100f, 150f, "1-17周 星期一 1-2节"), token(1100f, 220f, "1-17周 星期二 3-4节")), 1280f, 1025f, settings)
        assertTrue(result.candidates.isEmpty())
    }
    @Test fun missingSectionsRequireReviewAndClocksUseCurrentSettings() {
        val name = token(390f, 150f, "数学")
        val valid = StructuredTimetableParser.extract(headers + name + token(1120f, 140f, "1-17周 星期一 08:00-09:40"), 1280f, 1025f, settings)
        assertEquals(2, valid.candidates.single().duration)
        val invalid = StructuredTimetableParser.extract(headers + name + token(1120f, 140f, "1-17周 星期一 07:00-09:40"), 1280f, 1025f, settings)
        assertTrue(invalid.candidates.single().needsReview)
        assertEquals(0, invalid.candidates.single().startSection)
    }
    @Test fun backgroundBlockSpansTwoSections() {
        val tokens = listOf(token(400f, 80f, "星期一"), token(650f, 80f, "星期二"), token(180f, 180f, "第1节"), token(180f, 280f, "第2节"), token(180f, 380f, "第3节"), token(400f, 180f, "高等数学"), token(400f, 210f, "教师:张三"), token(400f, 250f, "1-16周"))
        val result = StructuredTimetableParser.extract(tokens, 1000f, 1025f, settings, visualCells = listOf(ImportRegion("fill", 0, .28f, .13f, .52f, .32f, TimetableLayout.WEEKLY)))
        assertEquals(1, result.candidates.size)
        assertEquals(1, result.candidates.single().startSection)
        assertEquals(2, result.candidates.single().duration)
    }
    @Test fun acrossPagesPreservesGapsLocationsDurationAndParity() {
        val a = CandidateCourse("数学", "A301", "张三", 1, 1, 2, 1, 4, 0)
        val list = listOf(a, a.copy(startWeek = 9, endWeek = 10), a.copy(location = "B301"), a.copy(duration = 3), a.copy(weekType = 1))
        assertEquals(5, ImageTimetableOcr.mergeAcrossPages(list).size)
        assertEquals(1, ImageTimetableOcr.mergeAcrossPages(listOf(a, a.copy(startWeek = 5, endWeek = 8))).size)
    }
    @Test fun continuationRowsAbovePreviousHeaderAreNotDropped() {
        val result = StructuredTimetableParser.extract(listOf(token(390f, 20f, "数学"), token(1120f, 15f, "1-17周 星期一 1-2节")), 1280f, 1025f, settings, page = 1, inherited = headers)
        assertEquals("数学", result.candidates.single().name)
        assertEquals(1, result.regions.single().page)
    }
    @Test fun reRecognitionOfMergedRegionRetainsOtherPages() {
        val a = CandidateCourse("数学", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 4, weekType = 0, sourceRegion = "a")
        val b = a.copy(startWeek = 5, endWeek = 8, sourceRegion = "b")
        val merged = ImageTimetableOcr.mergeAcrossPages(listOf(a, b)).single()
        assertTrue(merged.belongsTo("b"))
        assertEquals(listOf(b), merged.excludingRegion("a"))
    }
}
