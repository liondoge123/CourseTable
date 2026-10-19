package com.coursetable.app.importer

import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.coursetable.app.data.scheduledWeeks
import com.coursetable.app.data.withScheduledWeeks

/**
 * 基于 ML Kit 中文 OCR 的课表识别。
 *
 * 保留文字元素与包围盒，供结构解析区分相邻列。
 */
object OcrEngine {
    private var recognizer: TextRecognizer? = null
    private fun client(): TextRecognizer = recognizer ?: TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()).also { recognizer = it }
    fun release() { recognizer?.close(); recognizer = null }

    // 单行文字在图片中的包围盒（图片坐标系：左上原点，y 向下）
    data class OcrWord(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val imageWidth: Float,
        val imageHeight: Float,
        val needsReview: Boolean = false,
        val alternative: OcrAlternative? = null
    ) {
        /** 转为课表坐标系（y 向上，越靠上 y 越大），供 PdfGridExtractor 使用 */
        fun toTextToken(): TextToken {
            val x0 = left
            val x1 = right
            val yTopPdf = imageHeight - top
            val yBottomPdf = imageHeight - bottom
            return TextToken(x0, yBottomPdf, x1, yTopPdf, text, needsReview, listOfNotNull(alternative))
        }
    }

    /** 识别 Bitmap，失败由导入界面处理。 */
    suspend fun recognize(bitmap: Bitmap, maximumScale: Float? = null, useTiny: Boolean = true): List<OcrWord> {
        if (bitmap.height <= 2304) return recognizeWhole(bitmap, maximumScale, useTiny)
        val words = mutableListOf<OcrWord>()
        var top = 0
        while (top < bitmap.height) {
            currentCoroutineContext().ensureActive()
            val height = minOf(2048, bitmap.height - top)
            val tile = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
            try {
                val found = recognizeWhole(tile, maximumScale, useTiny).map { it.copy(top = it.top + top, bottom = it.bottom + top, imageWidth = bitmap.width.toFloat(), imageHeight = bitmap.height.toFloat()) }
                for (word in found) {
                    val duplicate = words.indexOfFirst { other -> OcrDecisions.normalize(other.text) == OcrDecisions.normalize(word.text) &&
                        kotlin.math.abs(other.top - word.top) < minOf(other.bottom - other.top, word.bottom - word.top) * .6f && kotlin.math.abs(other.left - word.left) < 10 }
                    if (duplicate < 0) words += word
                }
            } finally { if(tile !== bitmap) tile.recycle() }
            if (top + height == bitmap.height) break
            top += height - 192
        }
        return words
    }

    private suspend fun recognizeWhole(bitmap: Bitmap, maximumScale: Float?, useTiny: Boolean): List<OcrWord> {
        currentCoroutineContext().ensureActive()
        val recognizer = client()
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        val scale = (2560f / maxDimension).coerceIn(1f, maximumScale ?: if (maxDimension < 700) 4f else 2f)
        val input = if (scale > 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        try {
            val image = InputImage.fromBitmap(input, 0)
            val result = await(recognizer.process(image))
            currentCoroutineContext().ensureActive()
            val words = mutableListOf<OcrWord>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    // Elements retain column separation when ML Kit merges neighboring cells into one line.
                    for (element in line.elements) {
                        currentCoroutineContext().ensureActive()
                        var text = element.text.trim()
                        if (text.isEmpty()) continue
                        val box = element.boundingBox ?: continue
                        var review = false
                        var alternative: OcrAlternative? = null
                        if (useTiny) {
                            val x = (box.left - 2).coerceIn(0, input.width - 1)
                            val y = (box.top - 2).coerceIn(0, input.height - 1)
                            val right = (box.right + 2).coerceIn(x + 1, input.width)
                            val bottom = (box.bottom + 2).coerceIn(y + 1, input.height)
                            val crop = Bitmap.createBitmap(input, x, y, right - x, bottom - y)
                            val prediction = try { TinyTextRecognizer.recognize(crop) }
                                catch (e: Exception) { android.util.Log.w("CourseTableOCR", "Tiny recognition unavailable; using ML Kit", e); null }
                                finally { if (crop !== input) crop.recycle() }
                            val decision = OcrDecisions.choose(text, prediction, TinyTextRecognizer.supportedCodePoints)
                            text = decision.text; alternative = decision.alternative; review = alternative != null
                        }
                        words.add(
                            OcrWord(
                                text = text,
                                left = box.left / scale,
                                top = box.top / scale,
                                right = box.right / scale,
                                bottom = box.bottom / scale,
                                imageWidth = bitmap.width.toFloat(),
                                imageHeight = bitmap.height.toFloat(),
                                needsReview = review,
                                alternative = alternative
                            )
                        )
                    }
                }
            }
            if (words.isEmpty() && useTiny) {
                val region = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                for (line in OcrImagePreprocessor.inkLines(bitmap, region).take(64)) {
                    currentCoroutineContext().ensureActive()
                    val crop = Bitmap.createBitmap(bitmap, line.left, line.top, line.width(), line.height())
                    val prediction = try { TinyTextRecognizer.recognize(crop) } finally { if(crop !== bitmap) crop.recycle() }
                    if(prediction != null && prediction.confidence >= .8f && prediction.text.isNotBlank()) words += OcrWord(prediction.text,
                        line.left.toFloat(), line.top.toFloat(), line.right.toFloat(), line.bottom.toFloat(), bitmap.width.toFloat(), bitmap.height.toFloat(), true,
                        OcrAlternative("", prediction.text, "补识别文字，请确认", prediction.confidence))
                }
            }
            return words
        } finally {
            if (input !== bitmap) input.recycle()
        }
    }

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCoroutine { cont ->
            task.addOnSuccessListener { cont.resume(it) }
            task.addOnFailureListener { cont.resumeWith(kotlin.Result.failure(it)) }
        }
}

/**
 * PDF → 多页 Bitmap 渲染（系统内建 PdfRenderer，无需第三方依赖）
 */
object PdfRendererUtil {

    /** 打开 PDF 并逐页渲染回调，页面 Bitmap 在回调返回后立即回收，避免多页常驻内存 */
    suspend fun forEachPage(
        context: android.content.Context,
        uri: Uri,
        scale: Float = 2.0f,
        onPage: suspend (index: Int, bitmap: Bitmap) -> Unit
    ): List<String> {
        val warnings = mutableListOf<String>()
        var temporaryFile: java.io.File? = null
        try {
            val pfd = if (uri.scheme == "file") {
                android.os.ParcelFileDescriptor.open(
                    java.io.File(uri.path ?: throw ImportRejectedException("PDF 路径无效")),
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
                )
            } else context.contentResolver.openFileDescriptor(uri, "r")
                ?: context.contentResolver.openInputStream(uri)?.use { ins ->
                    val tmp = java.io.File(context.cacheDir, "pdf_tmp_${System.currentTimeMillis()}.pdf")
                    temporaryFile = tmp
                    tmp.outputStream().use { out ->
                        with(ImportPolicy) { ins.copyLimitedTo(out, DetectedImportType.PDF.maxBytes) }
                    }
                    android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                } ?: return warnings + "无法打开 PDF 文件"
            try {
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                try {
                    if (renderer.pageCount == 0) {
                        warnings.add("PDF 没有任何页面")
                    }
                    if (renderer.pageCount > ImportPolicy.MAX_PDF_PAGES) {
                        throw ImportRejectedException("PDF 页数超过 ${ImportPolicy.MAX_PDF_PAGES} 页")
                    }
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        try {
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            if (width.toLong() * height > ImportPolicy.MAX_RENDER_PIXELS) {
                                throw ImportRejectedException("PDF 页面尺寸过大")
                            }
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            val canvas = android.graphics.Canvas(bmp)
                            canvas.scale(scale, scale)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            try {
                                onPage(i, bmp)
                            } finally {
                                bmp.recycle()
                            }
                        } finally {
                            page.close()
                        }
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                pfd.close()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return warnings + "PDF 渲染失败：${e.javaClass.simpleName} ${e.message}"
        } finally {
            temporaryFile?.delete()
        }
        return warnings
    }
}

/**
 * 统一导入管线：优先 PDF 文字层直读（最准），失败再走 OCR 兜底
 */
object UnifiedPdfImporter {

    /**
     * 解析 PDF：返回 (候选课程, 采用路径, 警告)。
     * path: "text"(文字层) 或 "ocr"(渲染识别) 或 "fail"
     */
    suspend fun importPdf(
        context: android.content.Context,
        uri: Uri
    ): Triple<PdfParseResult, String, List<String>> {
        val session = VisualTimetableImporter.open(context, uri, true, com.coursetable.app.data.AppSettings())
        return try { Triple(session.result, if (session.path == "文字层直读") "text" else "ocr", session.result.warnings) }
        finally { session.close() }
    }
}

/**
 * 统一 OCR 管线：把 Bitmap 图片识别成课程候选
 */
object ImageTimetableOcr {
    /** Verify duplicate pixels before expensive full-image OCR, then recognize just one table. */
    internal suspend fun recognizeRepeatedDetails(bitmap: Bitmap, settings: com.coursetable.app.data.AppSettings,
        page: Int = 0, inherited: List<TextToken> = emptyList()): Pair<List<TextToken>, PdfParseResult>? {
        if (bitmap.height < bitmap.width * 1.5f) return null
        currentCoroutineContext().ensureActive()
        val height = OcrImagePreprocessor.exactRepeatedHeight(bitmap) ?: return null
        val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, height)
        try {
            val initial = OcrEngine.recognize(crop, useTiny = false).map { it.toTextToken() }
            if (!initial.any { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) } ||
                !initial.any { TimetableHeaders.matches(it.text, TimetableHeaders.Field.TIME) }) return null
            val (tokens, result) = recognizeStructured(crop, settings, page, inherited, initial)
            val offset = bitmap.height - height.toFloat()
            val ratio = height.toFloat() / bitmap.height
            return tokens.map { it.copy(y0 = it.y0 + offset, y1 = it.y1 + offset) } to result.copy(
                regions = result.regions.map { it.copy(top = it.top * ratio, bottom = it.bottom * ratio) },
                warnings = result.warnings + "已合并图片中逐像素一致的重复课表画面")
        } finally { crop.recycle() }
    }

    suspend fun parseImage(bitmap: Bitmap, settings: com.coursetable.app.data.AppSettings = com.coursetable.app.data.AppSettings(), page: Int = 0): PdfParseResult {
        return recognizeStructured(bitmap, settings, page).second
    }

    suspend fun recognizeStructured(bitmap: Bitmap, settings: com.coursetable.app.data.AppSettings, page: Int = 0, inherited: List<TextToken> = emptyList(), initialTokens: List<TextToken>? = null): Pair<List<TextToken>, PdfParseResult> {
        if (initialTokens == null) recognizeRepeatedDetails(bitmap, settings, page, inherited)?.let { return it }
        // Start with geometry; review actual course fields instead of every credit/code cell.
        var tokens = initialTokens ?: OcrEngine.recognize(bitmap, useTiny = false).map { it.toTextToken() }
        val headerTokens = tokens + inherited
        val hasDetailsHeader = headerTokens.any { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) } &&
            headerTokens.any { TimetableHeaders.matches(it.text, TimetableHeaders.Field.TIME) }
        if (hasDetailsHeader && tokens.count { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) } >= 2) {
            val repeatedHeight = OcrImagePreprocessor.exactRepeatedHeight(bitmap)
            if (repeatedHeight != null) {
                val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, repeatedHeight)
                try {
                    val (uniqueTokens, uniqueResult) = recognizeStructured(crop, settings, page, inherited)
                    val offset = bitmap.height - repeatedHeight.toFloat()
                    val ratio = repeatedHeight.toFloat() / bitmap.height
                    return uniqueTokens.map { it.copy(y0 = it.y0 + offset, y1 = it.y1 + offset) } to uniqueResult.copy(
                        regions = uniqueResult.regions.map { it.copy(top = it.top * ratio, bottom = it.bottom * ratio) },
                        warnings = uniqueResult.warnings + "已合并图片中逐像素一致的重复课表画面")
                } finally { crop.recycle() }
            }
        }
        if (!hasDetailsHeader) tokens = OcrEngine.recognize(bitmap).map { it.toTextToken() }
        val edges = TableVisualEvidence.horizontalEdges(bitmap); val cells = TableVisualEvidence.coloredCells(bitmap) + TableVisualEvidence.ruledCells(bitmap, tokens)
        for (cell in cells.take(64)) {
            currentCoroutineContext().ensureActive()
            val rect = android.graphics.Rect((cell.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1),
                (cell.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1), (cell.right * bitmap.width).toInt().coerceIn(1, bitmap.width), (cell.bottom * bitmap.height).toInt().coerceIn(1, bitmap.height))
            for (line in OcrImagePreprocessor.inkLines(bitmap, rect)) {
                val covered = tokens.any { t -> (bitmap.height - t.cy) in line.top.toFloat()..line.bottom.toFloat() && t.cx in line.left.toFloat()..line.right.toFloat() }
                if (covered) continue
                val crop = Bitmap.createBitmap(bitmap, line.left, line.top, line.width(), line.height())
                val prediction = try { TinyTextRecognizer.recognize(crop) } finally { if (crop !== bitmap) crop.recycle() }
                if (prediction != null && prediction.confidence >= .8f && prediction.text.isNotBlank()) tokens += TextToken(line.left.toFloat(), (bitmap.height - line.bottom).toFloat(), line.right.toFloat(), (bitmap.height - line.top).toFloat(), prediction.text, true,
                    listOf(OcrAlternative("", prediction.text, "补识别文字，请确认", prediction.confidence)))
            }
        }
        var parsed = StructuredTimetableParser.extract(tokens, bitmap.width.toFloat(), bitmap.height.toFloat(), settings, page, inherited, edges, cells)
        val uncertainWeeks = parsed.candidates.filter { c -> c.rawLines.any { Regex("(?:^|\\s)-\\d+周").containsMatchIn(StructuredTimetableParser.normalize(it)) } }.mapNotNull { it.sourceRegion }.toSet()
        val name = (tokens + inherited).firstOrNull { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) }
        if (name != null && parsed.regions.any { it.layout == TimetableLayout.DETAILS }) {
            val headerSource = if (name in tokens) tokens else inherited
            val headers = headerSource.filter { kotlin.math.abs(it.cy - name.cy) < name.height * 1.5 }.sortedBy { it.cx }
            val index = headers.indexOf(name)
            val x0 = headers.getOrNull(index - 1)?.let { (it.cx + name.cx) / 2 } ?: 0f
            val x1 = headers.getOrNull(index + 1)?.let { (it.cx + name.cx) / 2 } ?: bitmap.width.toFloat()
            // A missing name is a structural failure: retry only that narrow cell, preserving the full-image pass.
            val timeHeader = headers.firstOrNull { TimetableHeaders.matches(it.text, TimetableHeaders.Field.TIME) }
            // A legal-looking value (e.g. 17周 instead of 1-17周) can still be wrong.
            for (region in parsed.regions.filter { r -> r.layout == TimetableLayout.DETAILS && parsed.candidates.any { it.sourceRegion == r.id } }.take(64)) {
                val nameMissing = parsed.candidates.any { it.sourceRegion == region.id && it.name.isBlank() }
                val timeIndex = headers.indexOf(timeHeader)
                val columnLeft = if (nameMissing || timeHeader == null) x0 else headers.getOrNull(timeIndex - 1)?.let { (it.cx + timeHeader.cx) / 2 } ?: 0f
                val columnRight = if (nameMissing || timeHeader == null) x1 else headers.getOrNull(timeIndex + 1)?.let { (it.cx + timeHeader.cx) / 2 } ?: bitmap.width.toFloat()
                val rowWords = tokens.filter { it.cx in columnLeft..columnRight && (bitmap.height - it.cy) / bitmap.height in region.top..region.bottom }
                // Header centers do not locate actual column edges: long week lists may extend left of the midpoint.
                val left = minOf(columnLeft, rowWords.minOfOrNull { it.x0 - 2 } ?: columnLeft).coerceAtLeast(0f)
                val right = maxOf(columnRight, rowWords.maxOfOrNull { it.x1 + 2 } ?: columnRight).coerceAtMost(bitmap.width.toFloat())
                val x = left.toInt().coerceIn(0, bitmap.width - 1); val y = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val width = (right - x).toInt().coerceIn(1, bitmap.width - x); val height = ((region.bottom - region.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
                val crop = Bitmap.createBitmap(bitmap, x, y, width, height)
                var confirmation = emptyList<TextToken>()
                val recovered = try {
                    val original = OcrEngine.recognize(crop, if (nameMissing) 2f else null)
                    val enhanced = OcrImagePreprocessor.highContrast(crop)
                    val alternate = try { OcrEngine.recognize(enhanced, 4f, useTiny = false) } finally { if(enhanced !== crop) enhanced.recycle() }
                    confirmation = alternate.map { it.toTextToken().let { t -> t.copy(x0 = t.x0 + x, x1 = t.x1 + x, y0 = bitmap.height - y - height + t.y0, y1 = bitmap.height - y - height + t.y1) } }
                    original.map { word ->
                        val other = alternate.minByOrNull { kotlin.math.abs(it.top - word.top) + kotlin.math.abs(it.left - word.left) }
                        val difference = other?.takeIf { OcrDecisions.normalize(it.text) != OcrDecisions.normalize(word.text) && kotlin.math.abs(it.top - word.top) < word.bottom - word.top }
                        val prior = rowWords.minByOrNull { kotlin.math.abs((bitmap.height - it.cy) - (word.top + word.bottom) / 2 - y) + kotlin.math.abs(it.cx - (word.left + word.right) / 2 - x) }
                            ?.takeIf { kotlin.math.abs((bitmap.height - it.cy) - (word.top + word.bottom) / 2 - y) < word.bottom - word.top &&
                                OcrDecisions.normalize(it.text) != OcrDecisions.normalize(word.text) }
                        word.copy(needsReview = word.needsReview || difference != null || prior != null, alternative = (word.alternative
                            ?: difference?.let { OcrAlternative(word.text, it.text, "增强图识别不一致") }
                            ?: prior?.let { OcrAlternative(word.text, it.text, "整图与时间单元格识别不同，请确认") })?.copy(field = if (nameMissing) OcrField.NAME else OcrField.TIME))
                    }.ifEmpty { alternate.map { it.copy(needsReview = true) } }.map { it.toTextToken().let { t -> t.copy(x0 = t.x0 + x, x1 = t.x1 + x, y0 = bitmap.height - y - height + t.y0, y1 = bitmap.height - y - height + t.y1) } }
                } finally { if (crop !== bitmap) crop.recycle() }
                if (recovered.isNotEmpty()) {
                    val originalRow = tokens.filter { it.cx in left..right && (bitmap.height - it.cy) / bitmap.height in region.top..region.bottom }
                    val keepSchedule = !nameMissing && parsed.candidates.filter { it.sourceRegion == region.id }.all { it.fieldErrors(settings).isEmpty() }
                    val replacement = if (keepSchedule) reviewOriginalTokens(originalRow, recovered, confirmation) else recovered
                    tokens = tokens - originalRow.toSet() + replacement
                }
            }
            parsed = StructuredTimetableParser.extract(tokens, bitmap.width.toFloat(), bitmap.height.toFloat(), settings, page, inherited, edges, cells)
            parsed = parsed.copy(candidates = parsed.candidates.map { if (it.sourceRegion in uncertainWeeks) it.copy(needsReview = true, reviewFields = it.pendingReviewFields() + ReviewField.WEEKS) else it })
        }
        parsed = parsed.copy(candidates = parsed.candidates.map { candidate ->
            val region = parsed.regions.firstOrNull { it.id == candidate.sourceRegion }
            val alternatives = if (region == null) emptyList() else tokens.filter { it.cx / bitmap.width in region.left..region.right && (bitmap.height - it.cy) / bitmap.height in region.top..region.bottom }.flatMap { it.alternatives }.distinct()
            candidate.copy(alternatives = alternatives, issues = (candidate.issues + alternatives.map { it.reason }).distinct(),
                reviewFields = candidate.pendingReviewFields() + alternatives.pendingReviewFields(), needsReview = candidate.needsReview || alternatives.isNotEmpty())
        })
        parsed = try { refineDetailNames(bitmap, tokens, parsed) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { parsed.copy(warnings = parsed.warnings + "课程名单元格复核暂不可用，请对照原图校对") }
        parsed = parsed.copy(candidates = deduplicateOcrCandidates(parsed.candidates))
        return tokens to parsed
    }

    /** 全页面 OCR：把多页 bitmap 逐页识别，汇总课程 */
    suspend fun parsePages(bitmaps: List<Bitmap>): PdfParseResult {
        val allCandidates = mutableListOf<CandidateCourse>()
        val warnings = mutableListOf<String>()
        val regions = mutableListOf<ImportRegion>()
        bitmaps.forEachIndexed { idx, bmp ->
            val pageResult = parseImage(bmp, page = idx)
            allCandidates.addAll(pageResult.candidates)
            regions.addAll(pageResult.regions)
            warnings.addAll(pageResult.warnings.map { "第${idx + 1}页：$it" })
        }
        return PdfParseResult(mergeAcrossPages(allCandidates), warnings, regions)
    }

    /** 逐页渲染 + OCR：渲染一页识别一页并立即回收，避免多页位图常驻内存 */
    suspend fun parsePdfPages(context: android.content.Context, uri: Uri): PdfParseResult {
        val session = VisualTimetableImporter.open(context, uri, true, com.coursetable.app.data.AppSettings())
        return try { session.result } finally { session.close() }
    }

    /** 跨页合并：同一课程（同名+同星期+同节次）合并周次范围 */
    fun mergeAcrossPages(candidates: List<CandidateCourse>): List<CandidateCourse> {
        val grouped = candidates.groupBy { listOf(it.name, it.dayOfWeek, it.startSection, it.duration, it.location, it.teacher, it.weekType, it.needsReview) }
        return grouped.values.flatMap { list ->
            val merged = mutableListOf<CandidateCourse>()
            for (c in list.sortedBy { it.startWeek }) {
                val previous = merged.lastOrNull()
                if (previous != null) {
                    val weeks = (previous.asCourse().scheduledWeeks() + c.asCourse().scheduledWeeks()).distinct().sorted()
                    val schedule = if(weeks.isEmpty()) previous.asCourse() else previous.asCourse().withScheduledWeeks(weeks)
                    merged[merged.lastIndex] = previous.copy(startWeek = schedule.startWeek, endWeek = schedule.endWeek, weekType = schedule.weekType, selectedWeeksCsv = schedule.selectedWeeksCsv,
                        reviewFields = previous.pendingReviewFields() + c.pendingReviewFields(),
                        issues = (previous.issues + c.issues).distinct(), alternatives = (previous.alternatives + c.alternatives).distinct(), sourceRegions = previous.sourceRegions + c.sourceRegions + listOfNotNull(previous.sourceRegion, c.sourceRegion), sourceRecords = previous.sourceRecords.ifEmpty { listOf(previous) } + c.sourceRecords.ifEmpty { listOf(c) })
                } else merged.add(c)
            }
            merged
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }, { it.startWeek }))
    }
}
