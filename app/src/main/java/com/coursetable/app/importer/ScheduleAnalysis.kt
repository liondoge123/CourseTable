package com.coursetable.app.importer

import com.coursetable.app.data.*
import java.text.Normalizer

enum class ImportMode(val label: String) { APPEND("追加"), NEW("新建课表"), OVERWRITE("覆盖") }
enum class ImportStatus(val label: String) { NORMAL("正常"), DUPLICATE("重复"), CONFLICT("冲突"), INVALID("无效") }
data class ParsedImport(val candidates: List<CandidateCourse>, val warnings: List<String> = emptyList(), val timetable: Timetable? = null)
data class ScheduleConflict(val course: Course, val sections: IntRange, val weeks: List<Int>) {
    fun description(): String = "${course.name} · 周${"一二三四五六日".getOrNull(course.dayOfWeek - 1)} · 第${sections.first}–${sections.last}节 · 第${weeks.joinToString("、")}周"
}
data class AnalyzedRow(val candidate: CandidateCourse, val status: ImportStatus, val duplicate: Course?, val mergeTargets: List<Course>, val conflicts: List<ScheduleConflict>) {
    val included get() = candidate.included ?: (status != ImportStatus.DUPLICATE)
    val autoMergeTarget get() = uniqueTeacherMatch(mergeTargets, candidate.teacher)
    val requiresTarget get() = included && mergeTargets.isNotEmpty() && candidate.targetCourseId == null && !candidate.separateCourse &&
        autoMergeTarget == null && duplicate == null
}
private val nameWhitespace = Regex("\\s+")
fun normalizedName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).replace(nameWhitespace, "").lowercase(java.util.Locale.ROOT)
private val teacherSeparator = Regex("[,，、;；]")
private fun teacherNames(value: String) = value.split(teacherSeparator).map(::normalizedName).filter { it.isNotEmpty() }.sorted()
fun teachersCompatible(a: String, b: String) = a.isBlank() || b.isBlank() || teacherNames(a) == teacherNames(b)

/** Stop as soon as a second compatible parent makes automatic grouping ambiguous. */
internal fun uniqueTeacherMatch(parents: Collection<Course>, teacher: String): Course? {
    if (teacher.isBlank()) return parents.singleOrNull()
    val names = teacherNames(teacher)
    return parents.singleOrNull { it.teacher.isBlank() || teacherNames(it.teacher) == names }
}
fun scheduleFingerprint(c: Course): String = listOf(normalizedName(c.name), c.dayOfWeek, c.startSection, c.duration, c.scheduledWeeks().joinToString(",")).joinToString("|")
fun scheduleConflicts(course: Course, others: List<Course>): List<ScheduleConflict> = others.mapNotNull { other ->
    if (course.id > 0 && course.id == other.id || course.dayOfWeek != other.dayOfWeek) return@mapNotNull null
    val start = maxOf(course.startSection, other.startSection)
    val end = minOf(course.startSection + course.duration - 1, other.startSection + other.duration - 1)
    val weeks = course.scheduledWeeks().intersect(other.scheduledWeeks().toSet()).sorted()
    if (start <= end && weeks.isNotEmpty()) ScheduleConflict(other, start..end, weeks) else null
}

object ScheduleAnalysis {
    fun analyze(candidates: List<CandidateCourse>, existing: List<Course>, settings: AppSettings, mode: ImportMode): List<AnalyzedRow> {
        val baseline = if (mode == ImportMode.APPEND) existing else emptyList()
        val index = ScheduleIndex()
        val parentsByName = baseline.map(::PreparedSchedule).also { prepared -> prepared.forEach { index.add(it, baseline = true) } }
            .groupBy { it.name }.mapValues { (_, rows) -> rows.map { it.course }.distinctBy { it.courseId } }
        return candidates.map { candidate ->
            val course = candidate.asCourse(settings.timetableId)
            val prepared = PreparedSchedule(course)
            val duplicate = if (candidate.separateCourse) null else index.duplicate(prepared, candidate.groupKey, candidate.targetCourseId)
            val targets = parentsByName[prepared.name].orEmpty()
            val errors = candidate.fieldErrors(settings)
            val conflicts = if (errors.isEmpty()) index.conflicts(prepared).filterNot { it.course == duplicate } else emptyList()
            val status = when { errors.isNotEmpty() -> ImportStatus.INVALID; duplicate != null -> ImportStatus.DUPLICATE; conflicts.isNotEmpty() -> ImportStatus.CONFLICT; else -> ImportStatus.NORMAL }
            val row = AnalyzedRow(candidate, status, duplicate, targets, conflicts)
            if (row.included && status != ImportStatus.INVALID && (duplicate == null || candidate.updateDuplicate || candidate.separateCourse)) index.add(prepared, candidate.groupKey)
            row
        }
    }
}

fun CandidateCourse.asCourse(timetableId: Long = 0): Course = Course(name = name, teacher = teacher, location = location,
    dayOfWeek = dayOfWeek, startSection = startSection, duration = duration, startWeek = startWeek, endWeek = endWeek,
    weekType = weekType, selectedWeeksCsv = selectedWeeksCsv, timetableId = timetableId, color = color ?: 0xFF0A84FF)

data class ImportRequest(val mode: ImportMode, val candidates: List<CandidateCourse>, val reviewedExisting: List<Course>, val timetable: Timetable? = null)
data class ImportSummary(val timetableId: Long, val courses: Int, val sessions: Int, val updated: Int, val skipped: Int)
class ImportChangedException : IllegalStateException("课表数据已变化，请重新确认重复和冲突")
