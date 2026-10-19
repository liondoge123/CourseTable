package com.coursetable.app.importer

/** Whole-cell matching keeps course titles and meeting text out of the header classifier. */
object TimetableHeaders {
    enum class Field { NAME, TIME, TEACHER, LOCATION }
    private val aliases = mapOf(
        Field.NAME to listOf("课程名称", "课程名", "课程", "科目名称", "科目"),
        Field.TIME to listOf("上课时间地点", "上课时间", "时间地点", "教学时间", "授课时间", "时间"),
        Field.TEACHER to listOf("任课教师", "授课教师", "教师姓名", "教师", "老师"),
        Field.LOCATION to listOf("上课地点", "教学地点", "地点", "教室", "场地")
    )
    fun matches(text: String, field: Field): Boolean {
        val value = StructuredTimetableParser.normalize(text).replace(Regex("\\s+"), "").trim(':')
        val names = aliases.getValue(field)
        if (value in names) return true
        // Only longer labels tolerate one OCR substitution; short ambiguous labels stay exact.
        return value.length in 4..9 && names.any { it.length >= 4 && kotlin.math.abs(value.length - it.length) <= 1 && editDistance(value, it) == 1 }
    }

    fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val next = IntArray(b.length + 1)
            next[0] = i + 1
            for (j in b.indices) next[j + 1] = minOf(next[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = next
        }
        return previous.last()
    }
}
