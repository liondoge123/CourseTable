package com.coursetable.app.importer

/** Cell-level recognition proposes a name, never erases the other reading. */
internal fun reviewedCellName(
    original: String,
    lines: List<TinyTextRecognizer.Prediction>,
    corroborated: List<String>,
    supported: Set<Int>?
): OcrDecision {
    if (lines.isEmpty() || lines.size != corroborated.size) return OcrDecision(original)
    val proposed = lines.joinToString("") { it.text }.trim()
    if (proposed.isBlank() || proposed.length > ImportPolicy.MAX_TEXT_FIELD) return OcrDecision(original)
    val same = normalizedName(proposed) == normalizedName(original)
    if (same) return OcrDecision(original)
    val supportedOriginal = supported != null && original.codePoints().allMatch { Character.isWhitespace(it) || it in supported }
    val reliable = lines.indices.all { index ->
        lines[index].confidence >= .92f || lines[index].confidence >= .7f &&
            normalizedName(lines[index].text) == normalizedName(corroborated[index])
    }
    return if (supportedOriginal && reliable) {
        OcrDecision(proposed, OcrAlternative(proposed, original, "课程名已按完整文字行复核，原识别结果供确认", lines.minOf { it.confidence }, OcrField.NAME))
    } else {
        OcrDecision(original, OcrAlternative(original, proposed, "课程名单元格识别存在分歧，请确认", lines.minOf { it.confidence }, OcrField.NAME))
    }
}

/** Exact schedules only: distinct weeks, teachers and rooms must remain separate. */
internal fun deduplicateOcrCandidates(candidates: List<CandidateCourse>): List<CandidateCourse> =
    candidates.withIndex().groupBy { (index, c) ->
        val resolved = c.name.isNotBlank() && c.dayOfWeek in 1..7 && c.startSection > 0 && c.duration > 0 && c.startWeek > 0 && c.endWeek >= c.startWeek
        listOf(scheduleFingerprint(c.asCourse()), normalizedName(c.teacher), normalizedName(c.location), if (resolved) "" else "unresolved-$index")
    }.values.map { group ->
        val copies = group.map { it.value }
        val first = copies.first()
        first.copy(needsReview = copies.any { it.needsReview },
            reviewFields = copies.flatMap { it.pendingReviewFields() }.toSet(),
            issues = copies.flatMap { it.issues }.distinct(), alternatives = copies.flatMap { it.alternatives }.distinct(),
            sourceRegions = copies.flatMap { it.sourceRegionIds() }.toSet(),
            sourceRecords = if (copies.size == 1) first.sourceRecords else copies.flatMap { it.sourceRecords.ifEmpty { listOf(it) } })
    }
