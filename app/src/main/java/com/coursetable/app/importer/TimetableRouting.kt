package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings

enum class TimetableRoute { LABELED, DETAILS, WEEKLY, COORDINATE }
data class RouteAttempt(val route: TimetableRoute, val confidence: Float, val candidates: Int, val valid: Int)
data class TimetableRouting(val selected: TimetableRoute, val attempts: List<RouteAttempt>)

object TimetableConfidence {
    const val RETRY_THRESHOLD = .85f
    fun assess(route: TimetableRoute, result: PdfParseResult, settings: AppSettings, expected: Int): RouteAttempt {
        val courses = result.candidates
        if (courses.isEmpty()) return RouteAttempt(route, 0f, 0, 0)
        val valid = courses.count { it.fieldErrors(settings).isEmpty() }
        val completeness = valid.toFloat() / courses.size
        val certain = courses.count { !it.needsReview }.toFloat() / courses.size
        val coverage = (courses.size.toFloat() / maxOf(expected, courses.size, 1)).coerceAtMost(1f)
        return RouteAttempt(route, .5f * completeness + .2f * certain + .3f * coverage, courses.size, valid)
    }
}
