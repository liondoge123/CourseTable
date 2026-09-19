package com.coursetable.app.importer

import android.graphics.Bitmap
import com.coursetable.app.data.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrMergeTest {

    @Test
    fun ocrWordToTextTokenFlipsY() {
        // 图片 1000x800，行位于图片坐标 (left=200, top=100, right=400, bottom=140)
        val word = OcrEngine.OcrWord(
            text = "高等数学",
            left = 200f, top = 100f, right = 400f, bottom = 140f,
            imageWidth = 1000f,
            imageHeight = 800f
        )
        val token = word.toTextToken()
        // y 翻转：top->yB=800-100=700, bottom->yT=800-140=660
        assertEquals(200f, token.x0, 0.1f)
        assertEquals(400f, token.x1, 0.1f)
        assertEquals(660f, token.y0, 0.1f)
        assertEquals(700f, token.y1, 0.1f)
    }

    @Test
    fun mergeAcrossPagesCombinesWeekRanges() {
        val a = CandidateCourse(
            name = "高数", dayOfWeek = 1, startSection = 1, duration = 2,
            startWeek = 1, endWeek = 8, weekType = WeekType.ODD.code,
            location = "A301", teacher = "王老师"
        )
        val b = CandidateCourse(
            name = "高数", dayOfWeek = 1, startSection = 1, duration = 2,
            startWeek = 9, endWeek = 16, weekType = WeekType.ODD.code,
            location = "A301", teacher = "王老师"
        )
        val merged = ImageTimetableOcr.mergeAcrossPages(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals(1, merged[0].startWeek)
        assertEquals(16, merged[0].endWeek)
        assertEquals("A301", merged[0].location)
        assertEquals("王老师", merged[0].teacher)
    }

    @Test
    fun mergeAcrossPagesKeepsDistinctCourses() {
        val a = CandidateCourse(name = "高数", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, weekType = 0, location = "")
        val b = CandidateCourse(name = "英语", dayOfWeek = 2, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, weekType = 0, location = "")
        // 同名不同节次不合并
        val c = CandidateCourse(name = "高数", dayOfWeek = 1, startSection = 3, duration = 2, startWeek = 1, endWeek = 16, weekType = 0, location = "")
        val merged = ImageTimetableOcr.mergeAcrossPages(listOf(a, b, c))
        assertEquals(3, merged.size)
        assertTrue(merged.all { it.startWeek == 1 && it.endWeek == 16 })
    }
}
