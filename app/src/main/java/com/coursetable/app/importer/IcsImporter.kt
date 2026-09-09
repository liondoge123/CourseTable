package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.WeekType
import com.coursetable.app.util.WeekUtils
import java.time.LocalTime

data class ImportOutcome(
    val courses: List<Course>,
    val warnings: List<String>
)

object IcsImporter {

    private val COLOR_PALETTE = longArrayOf(
        0xFF0A84FF, 0xFF64D2FF, 0xFF30B0C7, 0xFF30D158,
        0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A, 0xFFFF375F,
        0xFFBF5AF2, 0xFF5E5CE6, 0xFFA2845E, 0xFF8E8E93
    )

    fun convert(events: List<IcsEvent>, settings: AppSettings): ImportOutcome {
        val out = mutableListOf<Course>()
        val warnings = mutableListOf<String>()
        val totalWeeks = settings.totalWeeks
        val periodCount = settings.periods.size.coerceAtLeast(1)

        for (event in events) {
            val startDate = event.date ?: continue
            val startWeek = WeekUtils.weekOf(settings.semesterStart, startDate)

            val rrule = event.rrule?.let { IcsParser.parseRrule(it) }
            val isWeekly = rrule != null && rrule.parsed && rrule.interval in 1..2
            if (rrule != null && rrule.parsed && !isWeekly) {
                warnings.add("仅支持每周/单双周规则，跳过：${event.summary}")
                continue
            }

            val times = TimeMapper.map(event.startTime, event.endTime, settings.periods)
            val startSection = times?.getOrNull(0) ?: 1
            val sectionDuration = times?.getOrNull(1) ?: 2
            if (startSection > periodCount) {
                warnings.add("无法匹配上课时间到节次，跳过：${event.summary}")
                continue
            }

            val color = pickColor(event.summary, startDate.dayOfWeek.value, event.startTime?.hour ?: 0)

            val targetDays: List<Int> = when {
                isWeekly && rrule.byDays.isNotEmpty() -> rrule.byDays
                else -> listOf(startDate.dayOfWeek.value)
            }.filter { it in 1..7 }
            if (targetDays.isEmpty()) {
                warnings.add("无法确定星期，跳过：${event.summary}")
                continue
            }

            val step = (rrule?.interval ?: 1).coerceIn(1, 2)
            val weekType = when {
                rrule == null -> WeekType.ALL.code
                step == 2 -> if (startWeek % 2 == 1) WeekType.ODD.code else WeekType.EVEN.code
                else -> WeekType.ALL.code
            }

            val endWeek: Int = when {
                rrule == null -> startWeek
                rrule.count != null -> startWeek + (rrule.count - 1) * step
                rrule.until != null -> lastOccurrenceWeek(settings.semesterStart, startWeek, rrule.until, step)
                else -> totalWeeks
            }

            val courseStart = startWeek.coerceAtLeast(1)
            val courseEnd = endWeek.coerceAtMost(totalWeeks)
            if (courseEnd < courseStart) continue

            val excludedWeeks = event.exdates.mapNotNull { d ->
                val w = WeekUtils.weekOf(settings.semesterStart, d)
                if (w in courseStart..courseEnd) w else null
            }.toSet()
            val segments = when {
                excludedWeeks.isEmpty() -> listOf(courseStart to courseEnd)
                step == 1 -> splitByExclusions(courseStart, courseEnd, excludedWeeks)
                else -> {
                    warnings.add("单双周课程暂不支持排除日期（EXDATE），已忽略：${event.summary}")
                    listOf(courseStart to courseEnd)
                }
            }

            for ((segStart, segEnd) in segments) {
                for (day in targetDays) {
                    out.add(
                        Course(
                            name = event.summary.ifBlank { "未命名课程" },
                            teacher = event.description.orEmpty(),
                            location = event.location,
                            dayOfWeek = day,
                            startSection = startSection,
                            duration = sectionDuration,
                            startWeek = segStart,
                            endWeek = segEnd,
                            weekType = weekType,
                            color = color
                        )
                    )
                }
            }
        }

        return ImportOutcome(out, warnings)
    }

    /** 把连续周区间按排除周切成多个连续段 */
    private fun splitByExclusions(start: Int, end: Int, excluded: Set<Int>): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var segStart: Int? = null
        var prev = -1
        for (w in start..end) {
            if (w in excluded) {
                if (segStart != null && prev >= segStart) result.add(segStart to prev)
                segStart = null
                prev = -1
                continue
            }
            if (segStart == null) segStart = w
            prev = w
        }
        if (segStart != null && prev >= segStart) result.add(segStart to prev)
        return result
    }

    /** 从 startWeek 起每 step 周一次，返回不超过 until 所在周的最大发生周号 */
    private fun lastOccurrenceWeek(semesterStart: java.time.LocalDate, startWeek: Int, until: java.time.LocalDate, step: Int): Int {
        val untilWeek = WeekUtils.weekOf(semesterStart, until)
        val span = untilWeek - startWeek
        if (span < 0) return startWeek
        return startWeek + (span / step) * step
    }

    private fun pickColor(name: String, startDay: Int, hour: Int): Long {
        val hash = (name + "$" + startDay + "$" + hour).hashCode()
        val idx = if (hash == Int.MIN_VALUE) 0 else Math.abs(hash) % COLOR_PALETTE.size
        return COLOR_PALETTE[idx]
    }
}

object TimeMapper {
    /** 根据开始/结束时间匹配到节次序号(1-based)与节数 */
    fun map(start: LocalTime?, end: LocalTime?, periods: List<PeriodTime>): IntArray? {
        val s = start ?: return null
        var startIdx = -1
        for (i in periods.indices) {
            if (!periods[i].start.isAfter(s)) startIdx = i else break
        }
        if (startIdx == -1) startIdx = 0
        var dur = 1
        if (end != null && end.isAfter(s)) {
            var cursor = periods[startIdx].end
            while (cursor.isBefore(end) && startIdx + dur < periods.size) {
                cursor = maxOf(cursor, periods[startIdx + dur].end)
                dur++
            }
        }
        return intArrayOf(startIdx + 1, dur.coerceAtLeast(1))
    }
}