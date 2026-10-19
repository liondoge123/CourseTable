package com.coursetable.app.importer

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Recognize complete text lines, rather than ML Kit's fragmented word boxes. */
internal suspend fun refineDetailNames(bitmap: Bitmap, tokens: List<TextToken>, parsed: PdfParseResult): PdfParseResult {
    val headers = tokens.filter { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) }
    if (headers.isEmpty()) return parsed
    val vertical = TableVisualEvidence.verticalEdges(bitmap)
    val names = mutableMapOf<String, OcrDecision>()
    for (region in parsed.regions.filter { it.layout == TimetableLayout.DETAILS }.take(64)) {
        currentCoroutineContext().ensureActive()
        val row = parsed.candidates.firstOrNull { it.sourceRegion == region.id } ?: continue
        val top = region.top * bitmap.height
        val header = headers.filter { bitmap.height - it.cy < top }.maxByOrNull { bitmap.height - it.cy } ?: continue
        val band = tokens.filter { kotlin.math.abs(it.cy - header.cy) < header.height * 1.5 }.sortedBy { it.cx }
        val index = band.indexOf(header)
        val left = vertical.filter { it < header.cx }.maxOrNull()
            ?: band.getOrNull(index - 1)?.let { (it.cx + header.cx) / 2 } ?: header.x0
        val right = vertical.filter { it > header.cx }.minOrNull()
            ?: band.getOrNull(index + 1)?.let { (it.cx + header.cx) / 2 } ?: header.x1
        // Header midpoints are only estimates, especially after deskewing. Keep visible name ink.
        val nameWords = tokens.filter { it.cx in left..right && (bitmap.height - it.cy) / bitmap.height in region.top..region.bottom &&
            it.x1 - it.x0 < (right - left) * 1.5 }
        val inkLeft = minOf(left + 2, nameWords.minOfOrNull { it.x0 - 3 } ?: left + 2)
        val inkRight = maxOf(right - 2, nameWords.maxOfOrNull { it.x1 + 3 } ?: right - 2)
        val rect = Rect(inkLeft.toInt().coerceIn(0, bitmap.width - 1),
            (top + 2).toInt().coerceIn(0, bitmap.height - 1),
            inkRight.toInt().coerceIn(1, bitmap.width),
            (region.bottom * bitmap.height - 2).toInt().coerceIn(1, bitmap.height))
        if (rect.width() < 12 || rect.height() < 8) continue
        val lines = OcrImagePreprocessor.inkLines(bitmap, rect)
        if (lines.isEmpty() || lines.size > 4) continue
        val readings = mutableListOf<TinyTextRecognizer.Prediction>()
        val corroborated = mutableListOf<String>()
        for (line in lines) {
            currentCoroutineContext().ensureActive()
            val crop = Bitmap.createBitmap(bitmap, line.left, line.top, line.width(), line.height())
            try {
                val prediction = TinyTextRecognizer.recognize(crop) ?: break
                readings += prediction
                // Low-confidence short wrapped tails require an independent reading.
                var confirmation = ""
                if (prediction.confidence < .92f) {
                    suspend fun read(input: Bitmap) = OcrEngine.recognize(input, maximumScale = 4f, useTiny = false)
                        .sortedBy { it.left }.joinToString("") { it.text }.trim('|')
                    confirmation = read(crop)
                    if (normalizedName(confirmation) != normalizedName(prediction.text)) {
                        val enhanced = OcrImagePreprocessor.highContrast(crop)
                        try {
                            val second = read(enhanced)
                            if (normalizedName(second) == normalizedName(prediction.text)) confirmation = second
                        } finally { enhanced.recycle() }
                    }
                }
                corroborated += confirmation
            } finally { if (crop !== bitmap) crop.recycle() }
        }
        if (readings.size == lines.size) names[region.id] = reviewedCellName(row.name, readings, corroborated, TinyTextRecognizer.supportedCodePoints)
    }
    return parsed.copy(candidates = parsed.candidates.map { candidate ->
        val decision = names[candidate.sourceRegion] ?: return@map candidate
        candidate.copy(name = decision.text, needsReview = candidate.needsReview || decision.alternative != null,
            reviewFields = candidate.pendingReviewFields() + if (decision.alternative != null) setOf(ReviewField.NAME) else emptySet(),
            alternatives = (candidate.alternatives + listOfNotNull(decision.alternative)).distinct(),
            issues = (candidate.issues + listOfNotNull(decision.alternative?.reason)).distinct())
    })
}
