package com.coursetable.app.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object WeekUtils {

    /** 计算 date 相对学期开始日期是第几周（第 1 周起，可为负数） */
    fun weekOf(semesterStart: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(semesterStart, date)
        return (days / 7).toInt() + 1
    }

    fun clampWeek(week: Int, totalWeeks: Int): Int = week.coerceIn(1, totalWeeks)

    /** 第 week 周的周一日期（兼容开学日不是周一） */
    fun weekStartDate(semesterStart: LocalDate, week: Int): LocalDate {
        val mondayOffset = semesterStart.dayOfWeek.value - 1
        return semesterStart.plusDays(((week - 1) * 7 - mondayOffset).toLong())
    }

    /** 第 week 周、星期 dayOfWeek（1=周一）的日期 */
    fun dateOfWeek(semesterStart: LocalDate, week: Int, dayOfWeek: Int): LocalDate =
        weekStartDate(semesterStart, week).plusDays((dayOfWeek - 1).toLong())

    fun weekRangeHasOdd(start: Int, end: Int): Boolean {
        val s = start.coerceAtLeast(1)
        for (w in s..end) if (w % 2 == 1) return true
        return false
    }

    fun weekRangeHasEven(start: Int, end: Int): Boolean {
        val s = start.coerceAtLeast(1)
        for (w in s..end) if (w % 2 == 0) return true
        return false
    }
}