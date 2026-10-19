package com.coursetable.app.importer

import com.coursetable.app.data.Course

internal data class OcrScheduleScore(
    val expected: Int,
    val predicted: Int,
    val correct: Int,
    val incorrectFlagged: Int
) {
    val missing get() = expected - correct
    val incorrect get() = predicted - correct
    val wholeTableCorrect get() = correct == expected && predicted == expected
    val recall get() = if (expected == 0) 1.0 else correct.toDouble() / expected
    val precision get() = if (predicted == 0) (if (expected == 0) 1.0 else 0.0) else correct.toDouble() / predicted
    // A missed row cannot be flagged by a recognizer; report missing separately.
    val reviewInterception get() = if (incorrect == 0) null else incorrectFlagged.toDouble() / incorrect
}

/** Matches a multiset so duplicate predictions cannot inflate exact schedule accuracy. */
internal fun evaluateOcrSchedules(expected: List<Course>, actual: List<CandidateCourse>): OcrScheduleScore {
    val remaining = expected.groupingBy(::scheduleFingerprint).eachCount().toMutableMap()
    var correct = 0
    var flagged = 0
    // Match unflagged copies first: a duplicate flagged copy counts as intercepted error.
    for (candidate in actual.sortedBy { it.needsReview }) {
        val key = scheduleFingerprint(candidate.asCourse())
        val count = remaining[key] ?: 0
        if (count > 0) { correct++; remaining[key] = count - 1 }
        else if (candidate.needsReview) flagged++
    }
    return OcrScheduleScore(expected.size, actual.size, correct, flagged)
}
