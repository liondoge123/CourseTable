package com.coursetable.app.importer

import com.coursetable.app.data.Course

/** Dictionaries are restricted to the target timetable, so schools and users are never mixed. */
object TimetablePostProcessor {
    fun apply(result: PdfParseResult, history: List<Course>): PdfParseResult {
        val names = history.map { it.name }.filter { it.isNotBlank() }.distinct()
        return result.copy(candidates = result.candidates.map { original ->
            var candidate = original
            val issues = original.issues.toMutableList()
            val alternatives = original.alternatives.toMutableList()
            if (original.name.length >= 4 && original.name !in names) {
                val matches = names.filter { oneHanSubstitution(original.name, it) }
                if (matches.size == 1) {
                    val corrected = matches.single()
                    val reason = "课程名按当前课表历史修正，请确认：${original.name} → $corrected"
                    candidate = candidate.copy(name = corrected)
                    issues += reason
                    alternatives += OcrAlternative(original.name, corrected, reason)
                }
            }
            // Person names cannot be safely corrected by distance alone; retain the original and offer a check.
            val teachers = history.filter { it.name == candidate.name }.map { it.teacher }.filter { it.isNotBlank() }.distinct()
            if (candidate.teacher.isNotBlank() && candidate.teacher !in teachers) {
                val matches = teachers.filter { oneHanSubstitution(candidate.teacher, it) }
                if (matches.size == 1) {
                    val reason = "教师名与当前课表历史相近，请确认"
                    issues += reason
                    alternatives += OcrAlternative(candidate.teacher, matches.single(), reason)
                }
            }
            val room = candidate.location.trim()
            val familiarVenue = Regex("线上|在线|网络|待定|另行|操场|体育场|运动场|体育馆|游泳|网球|篮球|排球|田径").containsMatchIn(room)
            val roomPattern = Regex("[\\p{IsHan}A-Za-z].*\\d{2,6}|^\\d{2,6}$").containsMatchIn(room)
            if (room.isNotBlank() && (!familiarVenue && !roomPattern || Regex("星期|\\d.*[周节]").containsMatchIn(room))) issues += "地点格式存疑，请对照原图确认"
            candidate.copy(needsReview = candidate.needsReview || issues.isNotEmpty(), issues = issues.distinct(), alternatives = alternatives.distinct())
        })
    }

    private fun oneHanSubstitution(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        val changes = a.indices.filter { a[it] != b[it] }
        return changes.size == 1 && changes.all { Character.UnicodeScript.of(a[it].code) == Character.UnicodeScript.HAN && Character.UnicodeScript.of(b[it].code) == Character.UnicodeScript.HAN }
    }
}
