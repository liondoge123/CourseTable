package com.coursetable.app.data

/** Empty CSV preserves the original range/parity representation. */
fun parseSelectedWeeks(csv: String): List<Int> = csv.split(',')
    .mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..60 }.distinct().sorted()

fun Course.scheduledWeeks(): List<Int> = if (selectedWeeksCsv.isNotBlank()) {
    parseSelectedWeeks(selectedWeeksCsv)
} else {
    (startWeek..endWeek).filter {
        weekType == WeekType.ALL.code ||
            (weekType == WeekType.ODD.code && it % 2 == 1) ||
            (weekType == WeekType.EVEN.code && it % 2 == 0)
    }
}

/** Keep regular selections compatible with existing range-based imports. */
fun Course.withScheduledWeeks(weeks: List<Int>): Course {
    val sorted = weeks.filter { it in 1..60 }.distinct().sorted()
    require(sorted.isNotEmpty()) { "Select at least one teaching week" }
    val start = sorted.first()
    val end = sorted.last()
    val type = WeekType.entries.firstOrNull { rule ->
        (start..end).filter { rule == WeekType.ALL || it % 2 == rule.code % 2 } == sorted
    }
    return copy(startWeek = start, endWeek = end, weekType = (type ?: WeekType.ALL).code,
        selectedWeeksCsv = if (type == null) sorted.joinToString(",") else "")
}

fun Course.weeksLabel(): String {
    if (selectedWeeksCsv.isBlank()) {
        val rule = when (weekType) { 1 -> " · 单周"; 2 -> " · 双周"; else -> "" }
        val range = if (startWeek == endWeek) "$startWeek" else "$startWeek–$endWeek"
        return "第 $range 周$rule"
    }
    val weeks = scheduledWeeks()
    val ranges = mutableListOf<String>()
    var index = 0
    while (index < weeks.size) {
        val start = weeks[index]
        var end = start
        while (index + 1 < weeks.size && weeks[index + 1] == end + 1) end = weeks[++index]
        ranges += if (start == end) "$start" else "$start–$end"
        index++
    }
    return "第 ${ranges.joinToString("、")} 周"
}
