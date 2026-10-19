package com.coursetable.app.importer

import kotlin.math.abs

/** Conflicting views keep the full image. Two agreeing crop views can propose a marked correction. */
internal fun reviewOriginalTokens(original: List<TextToken>, reread: List<TextToken>, confirmation: List<TextToken> = emptyList()): List<TextToken> = original.map { token ->
    val other = reread.minByOrNull { abs(it.cx - token.cx) + abs(it.cy - token.cy) }
        ?.takeIf { abs(it.cy - token.cy) < maxOf(it.height, token.height) && abs(it.cx - token.cx) < maxOf(it.x1 - it.x0, token.x1 - token.x0) }
    if (other == null) token
    else {
        val different = OcrDecisions.normalize(token.text) != OcrDecisions.normalize(other.text)
        val agreeing = confirmation.any { normalizedName(it.text) == normalizedName(other.text) && abs(it.cy - other.cy) < other.height }
        val sameBox = abs(other.cx - token.cx) < (token.x1 - token.x0) * .2 &&
            other.x1 - other.x0 in (token.x1 - token.x0) * .7f..(token.x1 - token.x0) * 1.3f
        val temporal = Regex("\\d.*[周节]|\\d+:\\d+").containsMatchIn(other.text)
        fun weeks(text: String) = if (Regex("\\d.*周").containsMatchIn(text)) StructuredTimetableParser.weeks(text, 60)
            .flatMap { (a, b, parity) -> (a..b).filter { parity == 0 || it % 2 == parity % 2 } }.toSet() else emptySet()
        val unchangedWeeks = weeks(token.text) == weeks(other.text)
        val lostSectionRange = Regex("\\d+\\s*-\\s*\\d+\\s*节").containsMatchIn(token.text) &&
            !Regex("\\d+\\s*-\\s*\\d+\\s*节").containsMatchIn(other.text)
        val selectCrop = different && agreeing && sameBox && temporal && unchangedWeeks && !lostSectionRange
        val selected = if (selectCrop) other.text else token.text
        val alternate = if (selectCrop) token.text else other.text
        val differences = other.alternatives + if (different) listOf(OcrAlternative(selected, alternate, "整图与时间单元格识别不同，请确认", field = OcrField.TIME)) else emptyList()
        token.copy(text = selected, needsReview = token.needsReview || other.needsReview || differences.isNotEmpty(), alternatives = (token.alternatives + differences).distinct())
    }
}
