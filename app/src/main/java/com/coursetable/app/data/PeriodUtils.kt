package com.coursetable.app.data

import java.time.LocalTime

object PeriodUtils {

    fun durationMinutes(period: PeriodTime): Int = java.time.Duration.between(period.start, period.end).toMinutes().toInt()

    /** The persisted start/end times are authoritative, regardless of the default duration. */
    fun validatePeriods(periods: List<PeriodTime>): String? {
        if (periods.isEmpty()) return "至少保留一个节次"
        periods.forEachIndexed { index, period ->
            if (period.end <= period.start) return "第 ${index + 1} 节下课时间必须晚于上课时间，不支持跨午夜"
            if (index > 0) {
                val previous = periods[index - 1]
                if (period.start <= previous.start) return "第 ${index + 1} 节上课时间必须晚于第 $index 节上课时间"
                if (period.start < previous.end) {
                    val minutes = java.time.Duration.between(period.start, previous.end).toMinutes()
                    return "第 $index 节与第 ${index + 1} 节重叠 $minutes 分钟"
                }
            }
        }
        return null
    }

    /** Reject wrapping instead of silently rolling a shifted period into another day. */
    private fun shift(period: PeriodTime, minutes: Int): PeriodTime {
        fun shifted(time: LocalTime): LocalTime {
            val value = time.hour * 60 + time.minute + minutes
            require(value in 0 until 1440) { "调整后时间跨越午夜，请缩小调整范围或修改时间" }
            return LocalTime.of(value / 60, value % 60)
        }
        return PeriodTime(shifted(period.start), shifted(period.end))
    }

    fun replacePeriod(periods: List<PeriodTime>, index: Int, replacement: PeriodTime, shiftThrough: Int? = null): List<PeriodTime> {
        require(index in periods.indices)
        require(shiftThrough == null || shiftThrough in (index + 1)..periods.lastIndex)
        val delta = java.time.Duration.between(periods[index].end, replacement.end).toMinutes().toInt()
        return periods.mapIndexed { position, period ->
            when {
                position == index -> replacement
                shiftThrough != null && position in (index + 1)..shiftThrough -> shift(period, delta)
                else -> period
            }
        }
    }

    fun withUniformDuration(periods: List<PeriodTime>, durationMinutes: Int): List<PeriodTime> {
        require(durationMinutes > 0)
        return periods.map { period ->
            val end = period.start.hour * 60 + period.start.minute + durationMinutes
            require(end < 1440) { "统一课时长后时间跨越午夜" }
            PeriodTime(period.start, LocalTime.of(end / 60, end % 60))
        }
    }

    fun appendPeriod(periods: List<PeriodTime>, durationMinutes: Int): List<PeriodTime> {
        require(durationMinutes > 0)
        val start = periods.lastOrNull()?.end?.let { it.hour * 60 + it.minute + 10 } ?: 8 * 60
        val end = start + durationMinutes
        require(start in 0 until 1440 && end < 1440) { "当天已没有足够时间添加节次" }
        return periods + PeriodTime(LocalTime.of(start / 60, start % 60), LocalTime.of(end / 60, end % 60))
    }

    /** 按开始时间和指定时长生成节次，用于初始化；已有节次以实际起止时间为准。 */
    fun build(starts: List<LocalTime>, durationMinutes: Int): List<PeriodTime> =
        starts.map { PeriodTime(it, it.plusMinutes(durationMinutes.toLong())) }

    /** 校验：开始时间严格递增且不重叠；合法返回 null，否则返回错误提示 */
    fun validate(starts: List<LocalTime>, durationMinutes: Int): String? {
        if (durationMinutes <= 0) return "时长必须大于 0"
        if (starts.isEmpty()) return "至少保留一个节次"
        if (durationMinutes >= 24 * 60) return "节次时间不能跨越午夜"
        val periods = build(starts, durationMinutes)
        periods.forEachIndexed { index, period ->
            if (period.end <= period.start) return "第 ${index + 1} 节结束时间不能跨越午夜"
        }
        for (i in 1 until periods.size) {
            if (periods[i].start.isBefore(periods[i - 1].end)) {
                return "第 ${i + 1} 节开始时间早于第 ${i} 节结束时间"
            }
        }
        return null
    }

    /**
     * 计算指定节次区间在当前时间的进行进度（0f..1f）。
     * 若未处于上课时间区间内、节次不合法或 periods 为空，返回 null。
     */
    fun calculateCourseProgress(
        startSection: Int,
        duration: Int,
        periods: List<PeriodTime>,
        now: LocalTime
    ): Float? {
        if (periods.isEmpty() || startSection <= 0 || duration <= 0) return null
        val startIndex = startSection - 1
        val endIndex = (startSection + duration - 2).coerceAtMost(periods.size - 1)
        if (startIndex !in periods.indices || endIndex !in periods.indices || startIndex > endIndex) return null
        val startTime = periods[startIndex].start
        val endTime = periods[endIndex].end
        if (endTime <= startTime) return null
        if (now >= startTime && now < endTime) {
            val total = java.time.Duration.between(startTime, endTime).toMinutes().coerceAtLeast(1)
            val passed = java.time.Duration.between(startTime, now).toMinutes()
            return (passed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
        return null
    }
}
