package com.coursetable.app.importer

import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 基于 ML Kit 中文 OCR 的课表识别。
 *
 * 保留文字元素与包围盒，供结构解析区分相邻列。
 */
object OcrEngine {

    // 单行文字在图片中的包围盒（图片坐标系：左上原点，y 向下）
    data class OcrWord(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val imageWidth: Float,
        val imageHeight: Float,
        val needsReview: Boolean = false
    ) {
        /** 转为课表坐标系（y 向上，越靠上 y 越大），供 PdfGridExtractor 使用 */
        fun toTextToken(): TextToken {
            val x0 = left
            val x1 = right
            val yTopPdf = imageHeight - top
            val yBottomPdf = imageHeight - bottom
            return TextToken(x0, yBottomPdf, x1, yTopPdf, text, needsReview)
        }
    }

    /** 识别 Bitmap，失败由导入界面处理。 */
    suspend fun recognize(bitmap: Bitmap, maximumScale: Float? = null, useTiny: Boolean = true): List<OcrWord> {
        val recognizer: TextRecognizer =
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        val scale = (2560f / maxDimension).coerceIn(1f, maximumScale ?: if (maxDimension < 700) 4f else 2f)
        val input = if (scale > 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        try {
            val image = InputImage.fromBitmap(input, 0)
            val result = await(recognizer.process(image))
            val words = mutableListOf<OcrWord>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    // Elements retain column separation when ML Kit merges neighboring cells into one line.
                    for (element in line.elements) {
                        var text = element.text.trim()
                        if (text.isEmpty()) continue
                        val box = element.boundingBox ?: continue
                        var review = false
                        if (useTiny) {
                            val x = (box.left - 2).coerceIn(0, input.width - 1)
                            val y = (box.top - 2).coerceIn(0, input.height - 1)
                            val right = (box.right + 2).coerceIn(x + 1, input.width)
                            val bottom = (box.bottom + 2).coerceIn(y + 1, input.height)
                            val crop = Bitmap.createBitmap(input, x, y, right - x, bottom - y)
                            val prediction = try { TinyTextRecognizer.recognize(crop) }
                                catch (e: Exception) { android.util.Log.w("CourseTableOCR", "Tiny recognition unavailable; using ML Kit", e); null }
                                finally { if (crop !== input) crop.recycle() }
                            if (prediction != null && prediction.confidence >= .8f && prediction.text.isNotBlank()) {
                                fun normalized(s: String) = StructuredTimetableParser.normalize(s).replace(Regex("\\s"), "").replace('、', ',').replace('.', ',')
                                val temporal = Regex("\\d.*(?:周|节)|星期").containsMatchIn(text)
                                review = prediction.confidence < .9f || temporal && normalized(text) != normalized(prediction.text)
                                text = prediction.text
                            }
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
                                needsReview = review
                            )
                        )
                    }
                }
            }
            return words
        } finally {
            if (input !== bitmap) input.recycle()
            recognizer.close()
        }
    }

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            task.addOnFailureListener { if (cont.isActive) cont.resumeWith(kotlin.Result.failure(it)) }
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
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: context.contentResolver.openInputStream(uri)?.use { ins ->
                    val tmp = java.io.File(context.cacheDir, "pdf_tmp_${System.currentTimeMillis()}.pdf")
                    temporaryFile = tmp
                    tmp.outputStream().use { out -> ins.copyTo(out) }
                    android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                } ?: return warnings + "无法打开 PDF 文件"
            try {
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                try {
                    if (renderer.pageCount == 0) {
                        warnings.add("PDF 没有任何页面")
                    }
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        try {
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
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
    suspend fun parseImage(bitmap: Bitmap, settings: com.coursetable.app.data.AppSettings = com.coursetable.app.data.AppSettings(), page: Int = 0): PdfParseResult {
        return recognizeStructured(bitmap, settings, page).second
    }

    suspend fun recognizeStructured(bitmap: Bitmap, settings: com.coursetable.app.data.AppSettings, page: Int = 0, inherited: List<TextToken> = emptyList(), initialTokens: List<TextToken>? = null): Pair<List<TextToken>, PdfParseResult> {
        var tokens = initialTokens ?: OcrEngine.recognize(bitmap).map { it.toTextToken() }
        val edges = TableVisualEvidence.horizontalEdges(bitmap); val cells = TableVisualEvidence.coloredCells(bitmap) + TableVisualEvidence.ruledCells(bitmap, tokens)
        var parsed = StructuredTimetableParser.extract(tokens, bitmap.width.toFloat(), bitmap.height.toFloat(), settings, page, inherited, edges, cells)
        val uncertainWeeks = parsed.candidates.filter { c -> c.rawLines.any { Regex("(?:^|\\s)-\\d+周").containsMatchIn(StructuredTimetableParser.normalize(it)) } }.mapNotNull { it.sourceRegion }.toSet()
        val name = (tokens + inherited).firstOrNull { it.text.replace(" ", "") == "课程名称" }
        if (name != null && parsed.regions.any { it.layout == TimetableLayout.DETAILS }) {
            val headerSource = if (name in tokens) tokens else inherited
            val headers = headerSource.filter { kotlin.math.abs(it.cy - name.cy) < name.height * 1.5 }.sortedBy { it.cx }
            val index = headers.indexOf(name)
            val x0 = headers.getOrNull(index - 1)?.let { (it.cx + name.cx) / 2 } ?: 0f
            val x1 = headers.getOrNull(index + 1)?.let { (it.cx + name.cx) / 2 } ?: bitmap.width.toFloat()
            // A missing name is a structural failure: retry only that narrow cell, preserving the full-image pass.
            val timeHeader = headers.firstOrNull { it.text.contains("上课时间") }
            for (region in parsed.regions.filter { r -> parsed.candidates.any { it.sourceRegion == r.id && (it.name.isBlank() || it.needsReview || it.startSection >= 10 && it.duration == 1) } }) {
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
                val recovered = try { OcrEngine.recognize(crop, if (nameMissing) 2f else null).map { it.toTextToken().let { t -> t.copy(x0 = t.x0 + x, x1 = t.x1 + x, y0 = bitmap.height - y - height + t.y0, y1 = bitmap.height - y - height + t.y1) } } } finally { if (crop !== bitmap) crop.recycle() }
                if (recovered.isNotEmpty()) tokens = tokens.filterNot { it.cx in left..right && (bitmap.height - it.cy) / bitmap.height in region.top..region.bottom } + recovered
            }
            parsed = StructuredTimetableParser.extract(tokens, bitmap.width.toFloat(), bitmap.height.toFloat(), settings, page, inherited, edges, cells)
            parsed = parsed.copy(candidates = parsed.candidates.map { if (it.sourceRegion in uncertainWeeks) it.copy(needsReview = true) else it })
        }
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
                if (previous != null && c.startWeek <= previous.endWeek + 1) {
                    merged[merged.lastIndex] = previous.copy(endWeek = maxOf(previous.endWeek, c.endWeek), sourceRegions = previous.sourceRegions + c.sourceRegions + listOfNotNull(previous.sourceRegion, c.sourceRegion), sourceRecords = previous.sourceRecords.ifEmpty { listOf(previous) } + c.sourceRecords.ifEmpty { listOf(c) })
                } else merged.add(c)
            }
            merged
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }, { it.startWeek }))
    }
}
