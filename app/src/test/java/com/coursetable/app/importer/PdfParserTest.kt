package com.coursetable.app.importer

import com.coursetable.app.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfParserTest {

    @Test
    fun cellParserExtractsCourseInfo() {
        val lines = listOf(
            "高等数学",
            "1-16周 周一第1-2节",
            "王教授",
            "教学楼A301"
        )
        val c = PdfCellParser.parse(lines, dayOfWeek = 1, defaultSection = 1)
        assertEquals("高等数学", c!!.name)
        assertEquals("教学楼A301", c.location)
        assertEquals("王教授", c.teacher)
        assertEquals(1, c.startSection)
        assertEquals(2, c.duration)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(WeekType.ALL.code, c.weekType)
    }

    @Test
    fun cellParserDetectsOddWeek() {
        val lines = listOf("大学英语", "单周 1-16周 周三第5-6节")
        val c = PdfCellParser.parse(lines, dayOfWeek = 3, defaultSection = 1)
        assertEquals(WeekType.ODD.code, c!!.weekType)
        assertEquals(5, c.startSection)
        assertEquals(2, c.duration)
    }

    @Test
    fun cellParserUsesDefaultSectionWhenAbsent() {
        val lines = listOf("体育")
        val c = PdfCellParser.parse(lines, dayOfWeek = 5, defaultSection = 7)
        assertEquals("体育", c!!.name)
        assertEquals(7, c.startSection)
        assertEquals(1, c.duration)
    }

    @Test
    fun gridExtractorReconstructsCoursesFromTokens() {
        // 模拟一张 2 行 × 3 列（周一到周三）的课表
        val tokens = mutableListOf<TextToken>()
        // 表头
        tokens.add(tok(100f, 700f, 140f, "星期一"))
        tokens.add(tok(200f, 700f, 240f, "星期二"))
        tokens.add(tok(300f, 700f, 340f, "星期三"))
        // 左侧节次标签
        tokens.add(tok(10f, 600f, 40f, "第1节"))
        tokens.add(tok(10f, 500f, 40f, "第2节"))
        // 第 1 节 周一：高等数学
        tokens.add(tok(100f, 600f, 130f, "高等数学"))
        tokens.add(tok(100f, 620f, 120f, "1-16周第1-2节"))
        tokens.add(tok(100f, 640f, 110f, "教学楼A101"))
        // 第 2 节 周二：大学物理
        tokens.add(tok(200f, 500f, 230f, "大学物理"))
        tokens.add(tok(200f, 520f, 220f, "1-16周第3-4节"))
        // 第 2 节 周三：大学英语（单周）
        tokens.add(tok(300f, 500f, 330f, "大学英语"))
        tokens.add(tok(300f, 520f, 320f, "单周1-16周第3-4节"))

        val result = PdfGridExtractor.extract(tokens)
        assertTrue("应识别出 3 门课，实际 ${result.candidates.size}", result.candidates.size >= 3)

        val math = result.candidates.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startSection)
        assertEquals(2, math.duration)
        assertEquals("教学楼A101", math.location)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)

        val physics = result.candidates.first { it.name == "大学物理" }
        assertEquals(2, physics.dayOfWeek)
        assertEquals(3, physics.startSection)

        val english = result.candidates.first { it.name == "大学英语" }
        assertEquals(3, english.dayOfWeek)
        assertEquals(WeekType.ODD.code, english.weekType)
    }

    @Test
    fun cellParserRecognizesCampusRoomLocation() {
        val lines = listOf("嵌入式系统", "2-14周(双) 周三第1-2节", "刘敏", "京江2号楼2206")
        val c = PdfCellParser.parse(lines, dayOfWeek = 3, defaultSection = 1)
        assertEquals("京江2号楼2206", c!!.location)
        assertEquals("刘敏", c.teacher)
        assertEquals(WeekType.EVEN.code, c.weekType)
    }

    @Test
    fun cellParserDoesNotTreatLocationAsTeacher() {
        val lines = listOf("体育", "1-16周", "操场")
        val c = PdfCellParser.parse(lines, dayOfWeek = 5, defaultSection = 7)
        assertEquals("体育", c!!.name)
        assertEquals("", c.teacher)
        assertEquals("操场", c.location)
    }

    @Test
    fun cellParserSplitsMergedSingleLine() {
        val lines = listOf("高等数学 李峰 1-16周 京江2号楼2204")
        val c = PdfCellParser.parse(lines, dayOfWeek = 1, defaultSection = 1)
        assertEquals("高等数学", c!!.name)
        assertEquals("李峰", c.teacher)
        assertEquals("京江2号楼2204", c.location)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun labeledParserExtractsWrappedFields() {
        val lines = listOf(
            "通信原理",
            "(1-2节)1-16周/校区:本部/场",
            "地:计算机楼113/教师:郑召",
            "文,朱轶/教学班:(2026-2027-",
            "1)-06620079-01/教学班组成",
            ":通信2401;通信2402;通信",
            "2403/课程性质简称:必修/选",
            "课备注:/学分:4"
        )
        val c = PdfCellParser.parseLabeled(lines, dayOfWeek = 2)
        assertEquals("通信原理", c!!.name)
        assertEquals("计算机楼113", c.location)
        assertEquals("郑召文,朱轶", c.teacher)
        assertEquals(1, c.startSection)
        assertEquals(2, c.duration)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(WeekType.ALL.code, c.weekType)
    }

    @Test
    fun labeledParserDetectsOddWeekAndMultiSection() {
        val lines = listOf(
            "通信电路",
            "(3-4节)1-15周(单)/校区:本",
            "部/场地:京江3号楼3205/教",
            "师:夏景/教学班:(2026-2027-",
            "1)-06620073-01/教学班组成",
            ":通信2401;通信2402;通信",
            "2403/课程性质简称:必修/选",
            "课备注:/学分:3"
        )
        val c = PdfCellParser.parseLabeled(lines, dayOfWeek = 1)
        assertEquals("通信电路", c!!.name)
        assertEquals("京江3号楼3205", c.location)
        assertEquals("夏景", c.teacher)
        assertEquals(3, c.startSection)
        assertEquals(2, c.duration)
        assertEquals(1, c.startWeek)
        assertEquals(15, c.endWeek)
        assertEquals(WeekType.ODD.code, c.weekType)
    }

    private fun tok(x0: Float, yBottom: Float, x1: Float, text: String): TextToken {
        return TextToken(x0, yBottom - 10f, x1, yBottom + 6f, text)
    }
}