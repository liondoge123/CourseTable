package com.coursetable.app.ui

import com.coursetable.app.importer.*
import java.util.IdentityHashMap

/** Derived from one analysis snapshot; grouped cards retain these candidate instances. */
internal class ImportReviewPresentation(rows: List<AnalyzedRow>) {
    private val byCandidate = IdentityHashMap<CandidateCourse, AnalyzedRow>()
    private val byDraftId = mutableMapOf<String, AnalyzedRow>()
    val courses = rows.map { it.candidate }
    val errors: List<CandidateCourse>
    val suggestions: List<CandidateCourse>
    val queue: List<CandidateCourse>
    var unresolved = 0; private set
    var selectedErrors = 0; private set
    var included = 0; private set
    var hasConflicts = false; private set

    init {
        val invalidCourses = mutableListOf<CandidateCourse>()
        val suggestedCourses = mutableListOf<CandidateCourse>()
        val otherReviews = mutableListOf<CandidateCourse>()
        for (row in rows) {
            val c = row.candidate
            byCandidate.putIfAbsent(c, row)
            c.draftId?.let { byDraftId.putIfAbsent(it, row) }
            val invalid = row.status == ImportStatus.INVALID
            if (invalid) invalidCourses += c else {
                if (c.needsReview) suggestedCourses += c
                if (c.issues.isNotEmpty() || c.reviewPrompts().isNotEmpty()) otherReviews += c
            }
            if (row.requiresTarget) unresolved++
            if (row.included) {
                included++
                if (invalid) selectedErrors++
                if (row.conflicts.isNotEmpty()) hasConflicts = true
            }
        }
        errors = invalidCourses
        suggestions = suggestedCourses
        queue = invalidCourses + otherReviews
    }

    fun rowFor(candidate: CandidateCourse): AnalyzedRow = checkNotNull(byCandidate[candidate])
    fun rowForDraft(id: String?): AnalyzedRow? = id?.let(byDraftId::get)
}
