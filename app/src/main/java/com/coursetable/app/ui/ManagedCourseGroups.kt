package com.coursetable.app.ui

import com.coursetable.app.data.Course

internal fun groupedManagedCourses(courses: List<Course>): Map<Int, List<Course>> =
    courses.groupBy { it.courseId.takeIf { id -> id > 0 } ?: -it.id }.values.map { sessions ->
        sessions.first().copy().also { it.additionalSessions = sessions.drop(1) }
    }.sortedWith(compareBy<Course> { it.dayOfWeek }.thenBy { it.startSection })
        .groupBy { it.dayOfWeek.coerceIn(1, 7) }
