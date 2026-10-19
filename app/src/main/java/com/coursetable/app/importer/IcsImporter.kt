package com.coursetable.app.importer

import com.coursetable.app.data.*
import com.coursetable.app.util.WeekUtils
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class ImportOutcome(val courses: List<Course>, val warnings: List<String>)

object IcsImporter {
    fun convert(events: List<IcsEvent>, settings: AppSettings): ImportOutcome {
        val courses = mutableListOf<Course>()
        val warnings = mutableListOf<String>()
        events.forEachIndexed { index, event ->
            val label = "事件 ${index + 1}「${event.summary}」"
            val date = event.date
            val rule = event.rrule?.let { IcsParser.parseRrule(it) }
            val unsupported = rule != null && (!rule.parsed || rule.interval < 1 ||
                event.rrule!!.split(';').any { it.substringBefore('=').uppercase() !in setOf("FREQ", "INTERVAL", "BYDAY", "UNTIL", "COUNT", "WKST") } ||
                Regex("BYDAY=[^;]*[0-9]").containsMatchIn(event.rrule) || rule.count?.let { it <= 0 } == true)
            val time = TimeMapper.map(event.startTime, event.endTime, settings.periods)
            if (date == null || unsupported || time == null) {
                warnings += "$label：" + when { date == null -> "缺少有效开始日期"; unsupported -> "不支持此重复规则，请校对周次"; else -> "上课时间无法匹配节次，请校对" }
                courses += Course(name = event.summary, teacher = event.description, location = event.location,
                    dayOfWeek = date?.dayOfWeek?.value ?: 0, startSection = time?.get(0) ?: 0, duration = time?.get(1) ?: 0,
                    startWeek = 0, endWeek = 0)
                return@forEachIndexed
            }
            val days = if (rule != null && rule.byDays.isNotEmpty()) rule.byDays else listOf(date.dayOfWeek.value)
            val anchorMonday = date.minusDays(date.dayOfWeek.value - 1L)
            val occurrences = linkedMapOf<Int, MutableList<Int>>()
            var count = 0
            var cursor = requireNotNull(date)
            val horizon = WeekUtils.dateOfWeek(settings.semesterStart, settings.totalWeeks, 7)
            while (!cursor.isAfter(horizon)) {
                if (rule == null && cursor != date || rule?.until?.let { cursor.isAfter(it) } == true) break
                val weekOffset = ChronoUnit.WEEKS.between(anchorMonday, cursor.minusDays(cursor.dayOfWeek.value - 1L))
                if (cursor.dayOfWeek.value in days && (rule == null || weekOffset % rule.interval == 0L)) {
                    if (rule?.count?.let { count >= it } == true) break
                    count++
                    val week = WeekUtils.weekOf(settings.semesterStart, cursor)
                    if (week in 1..settings.totalWeeks && cursor !in event.exdates) occurrences.getOrPut(cursor.dayOfWeek.value) { mutableListOf() }.add(week)
                }
                if (rule == null) break
                cursor = cursor.plusDays(1)
            }
            if (occurrences.isEmpty()) warnings += "$label：本学期没有实际上课日期"
            for ((day, weeks) in occurrences) {
                courses += Course(name = event.summary, teacher = event.description, location = event.location,
                    dayOfWeek = day, startSection = time[0], duration = time[1], startWeek = weeks.first(), endWeek = weeks.last(),
                    color = palette[(normalizedName(event.summary).hashCode() and Int.MAX_VALUE) % palette.size]).withScheduledWeeks(weeks)
            }
        }
        return ImportOutcome(courses, warnings)
    }
    private val palette = longArrayOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFBF5AF2, 0xFFFF375F, 0xFF64D2FF)
}

object TimeMapper {
    fun map(start: LocalTime?, end: LocalTime?, periods: List<PeriodTime>): IntArray? {
        if (start == null || end == null || !end.isAfter(start)) return null
        val a = periods.indices.minByOrNull { kotlin.math.abs(periods[it].start.toSecondOfDay() - start.toSecondOfDay()) } ?: return null
        val b = periods.indices.minByOrNull { kotlin.math.abs(periods[it].end.toSecondOfDay() - end.toSecondOfDay()) } ?: return null
        if (b < a || kotlin.math.abs(periods[a].start.toSecondOfDay() - start.toSecondOfDay()) > 300 || kotlin.math.abs(periods[b].end.toSecondOfDay() - end.toSecondOfDay()) > 300) return null
        return intArrayOf(a + 1, b - a + 1)
    }
}
