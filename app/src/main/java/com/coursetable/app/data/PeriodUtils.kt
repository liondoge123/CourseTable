package com.coursetable.app.data

import java.time.LocalTime

object PeriodUtils {

    /** 按开始时间 + 全局时长生成节次 */
    fun build(starts: List<LocalTime>, durationMinutes: Int): List<PeriodTime> =
        starts.map { PeriodTime(it, it.plusMinutes(durationMinutes.toLong())) }

    /** 校验：开始时间严格递增且不重叠；合法返回 null，否则返回错误提示 */
    fun validate(starts: List<LocalTime>, durationMinutes: Int): String? {
        if (durationMinutes <= 0) return "时长必须大于 0"
        val periods = build(starts, durationMinutes)
        for (i in 1 until periods.size) {
            if (periods[i].start.isBefore(periods[i - 1].end)) {
                return "第 ${i} 节开始时间早于第 ${i - 1} 节结束时间"
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
