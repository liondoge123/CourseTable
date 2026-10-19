package com.coursetable.app.importer

import androidx.room.withTransaction
import com.coursetable.app.data.*

/** Recheck and commit against one transaction snapshot. */
suspend fun AppDatabase.commitImport(request: ImportRequest, settings: AppSettings): ImportSummary = withTransaction {
    val dao = courseDao()
    val current = if (request.mode == ImportMode.NEW) emptyList() else dao.byTimetableOnce(settings.timetableId)
    if (request.mode != ImportMode.NEW && current.sortedBy { it.id } != request.reviewedExisting.sortedBy { it.id }) throw ImportChangedException()
    val target = if (request.mode == ImportMode.NEW) timetableDao().upsert((request.timetable ?: timetableDao().byId(settings.timetableId) ?: Timetable(name = "导入的课表")).copy(id = 0)) else settings.timetableId
    val targetSettings = if (request.mode == ImportMode.NEW && request.timetable != null) settings.copy(timetableId = target,
        totalWeeks = request.timetable.totalWeeks, semesterStart = request.timetable.semesterStart(), periods = request.timetable.periods()) else settings.copy(timetableId = target)
    val rows = ScheduleAnalysis.analyze(request.candidates, current, targetSettings, request.mode)
    require(rows.none { it.included && (it.status == ImportStatus.INVALID || it.requiresTarget) }) { "请修正字段或选择同名课程的归属" }
    if (request.mode == ImportMode.OVERWRITE) dao.deleteByTimetable(target)
    val index = ImportCommitIndex(if (request.mode == ImportMode.APPEND) current else emptyList())
    var courses = 0; var sessions = 0; var updated = 0; var skipped = 0
    val batchGroups = mutableMapOf<String, Long>()
    for (row in rows) {
        val c = row.candidate
        if (!row.included) { skipped++; continue }
        val incoming = c.asCourse(target)
        val duplicate = if (c.separateCourse) null else index.duplicate(incoming, c.targetCourseId, batchGroups[c.groupKey], c.groupKey != null)
        if (duplicate != null) {
            if (c.updateDuplicate) {
                val id = dao.upsert(duplicate.copy(teacher = c.teacher.ifBlank { duplicate.teacher }, location = c.location.ifBlank { duplicate.location }))
                index.save(dao.byId(id)!!)
                updated++
            } else skipped++
            continue
        }
        val explicit = c.targetCourseId?.let { id -> index.parent(id) ?: error("目标课程已删除") }
        val key = c.groupKey
        val remembered = key?.let { batchGroups[it] }?.let(index::parent)
        val parent = explicit ?: remembered ?: if (!c.separateCourse && key == null) index.matchingParent(c.name, c.teacher) else null
        val saved = if (parent == null) incoming else incoming.copy(courseId = parent.courseId, name = parent.name,
            teacher = parent.teacher.ifBlank { c.teacher }, color = parent.color)
        val id = dao.upsert(saved)
        val actual = dao.byId(id)!!
        index.save(actual)
        if (parent == null) courses++
        sessions++
        if (key != null) batchGroups[key] = actual.courseId
    }
    ImportSummary(target, courses, sessions, updated, skipped)
}
