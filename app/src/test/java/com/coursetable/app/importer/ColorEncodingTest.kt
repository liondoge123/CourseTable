package com.coursetable.app.importer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.coursetable.app.data.Course
import com.coursetable.app.ui.theme.CourseColorPalette
import com.coursetable.app.ui.theme.nextUnusedCourseColor
import com.coursetable.app.ui.theme.toArgbLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ColorEncodingTest {

    @Test
    fun colorLongRoundTrip() {
        // 模型的默认颜色（Long）
        val defaultColorLong: Long = 0xFF4B6EAF
        // palette 里的颜色
        val paletteColor = Color(0xFF4B6EAF)

        println("jsy defaultColorLong=$defaultColorLong")
        println("jsy palette.value=${paletteColor.value.toLong()}")
        println("jsy palette.toArgb()=${paletteColor.toArgb()}")
        println("jsy Color(default).value=${Color(defaultColorLong).value.toLong()}")
        println("jsy Color(default).toArgb()=${Color(defaultColorLong).toArgb()}")

        // 关键断言 1：默认 Long 应能还原为同一 ARGB
        assertEquals(paletteColor.toArgb(), Color(defaultColorLong).toArgb())

        // 新路径：toArgbLong() 存库 → Color(long) 读出，必须是不透明的正确颜色
        val newStored = paletteColor.toArgbLong()
        val newRead: Int = Color(newStored).toArgb()
        val newAlpha = (newRead ushr 24) and 0xFF
        println("jsy newStored=$newStored newReadArgb=0x${newRead.toString(16)}(alpha=$newAlpha)")
        assertEquals("toArgbLong 存库后应完全还原原始颜色", paletteColor.toArgb(), newRead)
        assertEquals("toArgbLong 存库后 alpha 应为不透明", 0xFF, newAlpha)

        // 说明：旧代码用 paletteColor.value.toLong() 存储，读回后颜色错乱/透明（即导致卡片不可见的 bug）
        val oldStored = paletteColor.value.toLong()
        val oldRead: Int = Color(oldStored).toArgb()
        assertNotEquals("旧路径 value.toLong() 存储的值与 ARGB 不一致（正是缺陷）", newStored, oldStored)
    }

    @Test
    fun courseColorPaletteHas12OpaqueColors() {
        assertEquals("课程颜色调色板应包含 12 种颜色", 12, CourseColorPalette.size)
        for (paletteColor in CourseColorPalette) {
            val argb = paletteColor.toArgb()
            val alpha = (argb ushr 24) and 0xFF
            assertEquals("调色板中所有颜色必须完全不透明 (alpha=255)", 0xFF, alpha)
        }
    }

    @Test
    fun nextUnusedCourseColorPicksFirstWhenEmpty() {
        val result = nextUnusedCourseColor(emptyList())
        assertEquals(CourseColorPalette[0].toArgbLong(), result)
    }

    @Test
    fun nextUnusedCourseColorPicksFirstUnusedInOrder() {
        val color0 = CourseColorPalette[0].toArgbLong()
        val color1 = CourseColorPalette[1].toArgbLong()
        val color2 = CourseColorPalette[2].toArgbLong()

        val courseA = Course(id = 1, name = "数学", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, color = color0)
        assertEquals(color1, nextUnusedCourseColor(listOf(courseA)))

        // 跳跃使用：用了 0 和 2，应自动选择未用的 1
        val courseB = Course(id = 2, name = "英语", dayOfWeek = 2, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, color = color2)
        assertEquals(color1, nextUnusedCourseColor(listOf(courseA, courseB)))
    }

    @Test
    fun nextUnusedCourseColorDoesNotOvercountSameCourse() {
        val color0 = CourseColorPalette[0].toArgbLong()
        val color1 = CourseColorPalette[1].toArgbLong()

        // 同一门课有两次排课，不应导致其颜色权重翻倍影响下一个新课的选择
        val mathMon = Course(id = 1, name = "高数", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, color = color0)
        val mathThu = Course(id = 2, name = "高数", dayOfWeek = 4, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, color = color0)
        assertEquals(color1, nextUnusedCourseColor(listOf(mathMon, mathThu)))
    }

    @Test
    fun nextUnusedCourseColorCyclesWhenAll12Used() {
        val all12Courses = CourseColorPalette.mapIndexed { idx, c ->
            Course(id = idx.toLong() + 1, name = "课程$idx", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 16, color = c.toArgbLong())
        }
        // 当全部 12 色均被选用过一遍时，轮转回到第一个最少使用的颜色
        assertEquals(CourseColorPalette[0].toArgbLong(), nextUnusedCourseColor(all12Courses))
    }
}