package com.coursetable.app.ui

import androidx.compose.runtime.saveable.listSaver
import com.coursetable.app.data.Course

/** Bundle-compatible values only; database review snapshots are refreshed on save. */
internal object CourseDraftState {
    private fun session(course: Course): ArrayList<Any> = arrayListOf(
        course.id, course.timetableId, course.name, course.teacher, course.location,
        course.dayOfWeek, course.startSection, course.duration, course.startWeek,
        course.endWeek, course.weekType, course.color, course.selectedWeeksCsv, course.courseId
    )

    private fun restoreSession(values: List<*>): Course = Course(
        id = values[0] as Long, timetableId = values[1] as Long,
        name = values[2] as String, teacher = values[3] as String, location = values[4] as String,
        dayOfWeek = values[5] as Int, startSection = values[6] as Int, duration = values[7] as Int,
        startWeek = values[8] as Int, endWeek = values[9] as Int, weekType = values[10] as Int,
        color = values[11] as Long, selectedWeeksCsv = values[12] as String, courseId = values[13] as Long
    )

    fun save(course: Course): ArrayList<Any> = session(course).apply {
        add(course.additionalSessions != null)
        add(ArrayList(course.additionalSessions.orEmpty().map(::session)))
    }

    fun restore(values: List<*>): Course = restoreSession(values).also { course ->
        if (values[14] as Boolean) {
            course.additionalSessions = (values[15] as List<*>).map { restoreSession(it as List<*>) }
        }
    }
}

internal val CourseDraftSaver = listSaver<Course?, Any>(
    save = { it?.let(CourseDraftState::save) ?: emptyList() },
    restore = { if (it.isEmpty()) null else CourseDraftState.restore(it) }
)

internal val CourseSessionDraftsSaver = listSaver<List<Course>, Any>(
    save = { courses -> courses.map(CourseDraftState::save) },
    restore = { rows -> rows.map { CourseDraftState.restore(it as List<*>) } }
)
