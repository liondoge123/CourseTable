package com.coursetable.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.coursetable.app.data.AppSettings
import java.io.File
import kotlinx.coroutines.ensureActive

data class ImportPage(val index: Int, val image: File, val width: Int, val height: Int, val tokens: List<TextToken>, val recognitionImage: File = image)
class VisualImportSession(val directory: File, val pages: List<ImportPage>, val result: PdfParseResult, val path: String, val sourceImage: PreparedImportImage? = null, val selection: ImageSelection? = null) : java.io.Closeable {
    override fun close() { directory.deleteRecursively(); TinyTextRecognizer.release(); OcrEngine.release() }
}

object TableVisualEvidence {
    /** Full-height table rules locate column edges more accurately than header midpoints. */
    fun verticalEdges(bitmap: Bitmap): List<Float> {
        val edges = mutableListOf<Float>()
        val current = IntArray(bitmap.height)
        val before = IntArray(bitmap.height)
        val after = IntArray(bitmap.height)
        var start = -1
        for (x in 1 until bitmap.width - 1) {
            bitmap.getPixels(current, 0, 1, x, 0, 1, bitmap.height)
            bitmap.getPixels(before, 0, 1, (x - 3).coerceAtLeast(0), 0, 1, bitmap.height)
            bitmap.getPixels(after, 0, 1, (x + 3).coerceAtMost(bitmap.width - 1), 0, 1, bitmap.height)
            var count = 0
            for (y in bitmap.height / 40 until bitmap.height * 39 / 40 step 2) {
                val shade = gray(current[y])
                if (shade <= 252 && maxOf(gray(before[y]), gray(after[y])) - shade >= 2 && absColor(current[y]) < 20) count++
            }
            if (count > bitmap.height * .32) { if (start < 0) start = x }
            else if (start >= 0) {
                if (x - start <= 8) edges += (start + x - 1) / 2f
                start = -1
            }
        }
        return edges
    }
    fun ruledCells(bitmap: Bitmap, tokens: List<TextToken>): List<ImportRegion> {
        val labels = tokens.filter { Regex("(?:星期|周)[一二三四五六日天]").matches(it.text.trim()) }
        val band = labels.map { center -> labels.filter { kotlin.math.abs(it.cy - center.cy) < center.height } }.maxByOrNull { it.size }.orEmpty().sortedBy { it.cx }
        if (band.size < 2) return emptyList()
        val result = mutableListOf<ImportRegion>()
        band.forEachIndexed { i, t ->
            val x0 = (band.getOrNull(i - 1)?.let { (it.cx + t.cx) / 2 } ?: (t.cx - (band[1].cx - t.cx) / 2)).toInt().coerceIn(0, bitmap.width - 1)
            val x1 = (band.getOrNull(i + 1)?.let { (it.cx + t.cx) / 2 } ?: (t.cx + (t.cx - band[i - 1].cx) / 2)).toInt().coerceIn(x0 + 1, bitmap.width)
            val edges = mutableListOf<Int>(); var runStart = -1
            for (y in (bitmap.height - t.y0).toInt().coerceAtLeast(0) until bitmap.height) {
                var count = 0; var total = 0
                for (x in x0 + 2 until x1 - 2 step 2) {
                    val p = bitmap.getPixel(x, y); total++
                    if ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3 <= 245 && absColor(p) < 20) count++
                }
                val line = total > 0 && count > total * .8
                if (line && runStart < 0) runStart = y
                if (!line && runStart >= 0) { if (y - runStart <= 5) edges.add((runStart + y) / 2); runStart = -1 }
            }
            edges.zipWithNext().filter { it.second - it.first > t.height * 1.5 }.forEach { (a, b) ->
                result.add(ImportRegion("rule-${result.size}", 0, x0.toFloat() / bitmap.width, a.toFloat() / bitmap.height, x1.toFloat() / bitmap.width, b.toFloat() / bitmap.height, TimetableLayout.WEEKLY))
            }
        }
        return result
    }
    /** Connected colored cell fills give spanning-course boundaries even when grid lines are absent. */
    fun coloredCells(bitmap: Bitmap): List<ImportRegion> {
        val step = maxOf(2, maxOf(bitmap.width, bitmap.height) / 500)
        val w = bitmap.width / step; val h = bitmap.height / step
        val marked = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = bitmap.getPixel(x * step, y * step)
            marked[y * w + x] = absColor(p) > 25 && minOf(Color.red(p), Color.green(p), Color.blue(p)) > 100
        }
        val result = mutableListOf<ImportRegion>()
        val queue = IntArray(w * h)
        for (i in marked.indices) {
            if (!marked[i]) continue
            var tail = 1; var head = 0; queue[0] = i; marked[i] = false
            var x0 = i % w; var x1 = x0; var y0 = i / w; var y1 = y0
            while (head < tail) {
                val n = queue[head++]; val x = n % w; val y = n / w
                x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y)
                for (next in intArrayOf(if (x > 0) n - 1 else -1, if (x < w - 1) n + 1 else -1, if (y > 0) n - w else -1, if (y < h - 1) n + w else -1)) {
                    if (next >= 0 && marked[next]) { marked[next] = false; queue[tail++] = next }
                }
            }
            if (x1 - x0 > w * .055 && y1 - y0 > h * .018 && tail > (x1 - x0) * (y1 - y0) * .4)
                result.add(ImportRegion("fill-${result.size}", 0, x0.toFloat() / w, y0.toFloat() / h, (x1 + 1f) / w, (y1 + 1f) / h, TimetableLayout.WEEKLY))
        }
        return result
    }
    /** Long pale/dark horizontal lines are row boundaries, not OCR text baselines. */
    fun horizontalEdges(bitmap: Bitmap): List<Float> {
        val result = mutableListOf<Float>()
        val pixels = IntArray(bitmap.width)
        val above = IntArray(bitmap.width)
        val below = IntArray(bitmap.width)
        var start = -1
        fun finish(y: Int) {
            if (start >= 0 && y - start <= 8) result.add(bitmap.height - (start + y - 1) / 2f)
            start = -1
        }
        for (y in 1 until bitmap.height - 1) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            bitmap.getPixels(above, 0, bitmap.width, 0, (y - 3).coerceAtLeast(0), bitmap.width, 1)
            bitmap.getPixels(below, 0, bitmap.width, 0, (y + 3).coerceAtMost(bitmap.height - 1), bitmap.width, 1)
            var count = 0
            for (x in bitmap.width / 40 until bitmap.width * 39 / 40 step 2) {
                val p = pixels[x]; val gray = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
                val background = maxOf(gray(above[x]), gray(below[x]))
                // Faint rules survive white overlays; a flat pale background is not a rule.
                if (gray <= 252 && background - gray >= 2 && absColor(p) < 20) count++
            }
            if (count > bitmap.width * .32) { if (start < 0) start = y }
            else finish(y)
        }
        finish(bitmap.height - 1)
        return result
    }
    private fun gray(p: Int) = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
    private fun absColor(p: Int) = maxOf(Color.red(p), Color.green(p), Color.blue(p)) - minOf(Color.red(p), Color.green(p), Color.blue(p))
}

object VisualTimetableImporter {
    internal fun requiresOcrSupplement(tokens: List<TextToken>, parsed: PdfParseResult, cells: List<ImportRegion>, width: Float, height: Float, settings: AppSettings): Boolean {
        if (parsed.candidates.isEmpty()) return true
        val unscheduled = parsed.regions.filter { it.layout == TimetableLayout.UNKNOWN }.map { it.id }.toSet()
        if (parsed.candidates.any { it.sourceRegion !in unscheduled && it.fieldErrors(settings).isNotEmpty() }) return true
        // Empty timetable cells are expected; they are not evidence of OCR failure.
        return cells.any { cell ->
            val contents = tokens.filter { it.cx / width in cell.left..cell.right && (height-it.cy) / height in cell.top..cell.bottom &&
                it.text.any { char -> char.isLetter() } && !Regex("^(?:星期|周)[一二三四五六日天]|^打印时间|^实践课程").containsMatchIn(it.text.trim()) }
            contents.any { token -> parsed.regions.none { r -> token.cx / width in r.left..r.right && (height-token.cy) / height in r.top..r.bottom } }
        }
    }

    private fun compatibleColumns(headers: List<TextToken>, tokens: List<TextToken>): Boolean {
        val name = headers.firstOrNull { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) }
        val time = headers.firstOrNull { TimetableHeaders.matches(it.text, TimetableHeaders.Field.TIME) }
        if (name != null && time != null) {
            val sorted = headers.sortedBy { it.cx }
            val i = sorted.indexOf(name)
            val left = sorted.getOrNull(i - 1)?.let { (it.cx + name.cx) / 2 } ?: 0f
            val right = sorted.getOrNull(i + 1)?.let { (it.cx + name.cx) / 2 } ?: time.x0
            return tokens.any { it.cx in left..right && it.text.any { c -> c.isLetter() } } &&
                tokens.any { it.cx > (time.cx + (sorted.getOrNull(sorted.indexOf(time) - 1)?.cx ?: name.cx)) / 2 && Regex("(?:星期|周)[一二三四五六日天1-7]").containsMatchIn(it.text.replace(" ", "")) }
        }
        val days = headers.filter { Regex("(?:星期|周)[一二三四五六日天]").matches(it.text.trim()) }
        return days.size >= 2 && tokens.count { it.cx < days.minOf { d -> d.x0 } && Regex("^\\d{1,2}$|\\d.*节|\\d.*:.*\\d").containsMatchIn(it.text.trim()) } >= 2
    }
    suspend fun open(context: Context, uri: Uri, pdf: Boolean, settings: AppSettings, onProgress: (String) -> Unit = {}): VisualImportSession {
        val dir = File(context.cacheDir, "timetable-review-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val pages = mutableListOf<ImportPage>(); val candidates = mutableListOf<CandidateCourse>()
        val regions = mutableListOf<ImportRegion>(); val warnings = mutableListOf<String>(); var usedText = false; var usedOcr = false
        try {
            val sourceUri = if (pdf) {
                val source = File(dir, "source.pdf")
                ImportPolicy.copyToFile(context, uri, source, DetectedImportType.PDF)
                Uri.fromFile(source)
            } else uri
            val textPages = if (pdf) {
                PdfTextReader.extractByPage(File(sourceUri.path ?: error("PDF 路径无效")))
            } else emptyList()
            var inherited = emptyList<TextToken>(); var previousWidth = 0
            suspend fun parse(index: Int, original: Bitmap) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                onProgress("正在识别第 ${index + 1} 页")
                val text = textPages.getOrNull(index).orEmpty()
                val bmp = if (text.size < 10) OcrImagePreprocessor.deskew(original) else original
                try {
                // PDF tokens and rendered bitmaps share normalized display coordinates (renderer scale = 2).
                var tokens = text.map { it.copy(x0 = it.x0 * 2, x1 = it.x1 * 2, y0 = it.y0 * 2, y1 = it.y1 * 2) }
                val hasText = tokens.size >= 10
                if (hasText) usedText = true else usedOcr = true
                val repeated = if (!hasText) ImageTimetableOcr.recognizeRepeatedDetails(bmp, settings, index,
                    if (previousWidth == bmp.width) inherited else emptyList()) else null
                if (!hasText) tokens = repeated?.first ?: OcrEngine.recognize(bmp, useTiny = false).map { it.toTextToken() }
                val compatible = inherited.isNotEmpty() && previousWidth == bmp.width && compatibleColumns(inherited, tokens)
                val edges = if (repeated == null) TableVisualEvidence.horizontalEdges(bmp) else emptyList()
                val cells = if (repeated == null) TableVisualEvidence.coloredCells(bmp) + TableVisualEvidence.ruledCells(bmp, tokens) else emptyList()
                var parsed = repeated?.second ?: StructuredTimetableParser.extract(tokens, bmp.width.toFloat(), bmp.height.toFloat(), settings, index, if (compatible) inherited else emptyList(), edges, cells, candidates.map { it.teacher })
                if (repeated == null && (!hasText || requiresOcrSupplement(tokens, parsed, cells, bmp.width.toFloat(), bmp.height.toFloat(), settings))) {
                    usedOcr = true
                    val recognized = ImageTimetableOcr.recognizeStructured(bmp, settings, index, if (compatible) inherited else emptyList(), if (!hasText) tokens else null)
                    if (hasText && parsed.candidates.isNotEmpty()) {
                        val supplemental = recognized.second.candidates.filter { candidate -> parsed.candidates.none { existing -> scheduleFingerprint(existing.asCourse()) == scheduleFingerprint(candidate.asCourse()) } }
                        parsed = parsed.copy(candidates = parsed.candidates + supplemental, regions = (parsed.regions + recognized.second.regions).distinctBy { it.id }, warnings = parsed.warnings + recognized.second.warnings)
                        tokens = (tokens + recognized.first).distinctBy { listOf(it.text, it.x0.toInt(), it.y0.toInt()) }
                    } else { tokens = recognized.first; parsed = recognized.second }
                } else if (hasText) usedText = true
                val name = tokens.firstOrNull { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) }
                if (name != null) {
                    inherited = tokens.filter { kotlin.math.abs(it.cy - name.cy) < name.height * 1.5 }
                    previousWidth = bmp.width
                } else {
                    val weekdays = tokens.filter { Regex("(?:星期|周)[一二三四五六日天]").matches(it.text.trim()) }
                    val header = weekdays.map { t -> weekdays.filter { kotlin.math.abs(it.cy - t.cy) < t.height } }.maxByOrNull { it.size }.orEmpty()
                    if (header.size >= 2) { inherited = header; previousWidth = bmp.width }
                    else if (!compatible) inherited = emptyList()
                }
                val file = File(dir, "page-$index.jpg")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                val recognition = File(dir, "page-$index.png")
                recognition.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                pages.add(ImportPage(index, file, bmp.width, bmp.height, tokens, recognition))
                onProgress("正在解析第 ${index + 1} 页")
                val remaining = (ImportPolicy.MAX_OUTPUT_COURSES - candidates.size).coerceAtLeast(0)
                candidates.addAll(parsed.candidates.take(remaining))
                if (parsed.candidates.size > remaining && warnings.none { it.contains("课程数量超过") }) {
                    warnings.add("课程数量超过 ${ImportPolicy.MAX_OUTPUT_COURSES} 门，已停止追加")
                }
                regions.addAll(parsed.regions)
                warnings.addAll(parsed.warnings.map { "第${index + 1}页：$it" })
                } finally { if (bmp !== original) bmp.recycle() }
            }
            if (pdf) warnings.addAll(PdfRendererUtil.forEachPage(context, sourceUri, onPage = { i, bmp -> parse(i, bmp) }))
            else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 16384 || bounds.outWidth.toLong() * bounds.outHeight / sample / sample > ImportPolicy.MAX_RENDER_PIXELS) sample *= 2
                val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                    ?: error("无法读取图片")
                try { parse(0, bmp) } finally { bmp.recycle() }
            }
            if (pages.isEmpty()) error(warnings.joinToString("；").ifBlank { "没有可读取的页面" })
            return VisualImportSession(dir, pages, PdfParseResult(ImageTimetableOcr.mergeAcrossPages(candidates), warnings, regions), when { usedText && usedOcr -> "文字层 + OCR 补充"; usedText -> "文字层直读"; else -> "OCR 识别" })
        } catch (e: Exception) { dir.deleteRecursively(); TinyTextRecognizer.release(); OcrEngine.release(); throw e }
    }

    suspend fun recognizeRegion(page: ImportPage, region: ImportRegion, settings: AppSettings): List<CandidateCourse> {
        val original = BitmapFactory.decodeFile(page.recognitionImage.absolutePath) ?: error("无法读取识别原图")
        try {
            val x = (region.left * original.width).toInt().coerceIn(0, original.width - 1)
            val y = (region.top * original.height).toInt().coerceIn(0, original.height - 1)
            val width = ((region.right - region.left) * original.width).toInt().coerceIn(1, original.width - x)
            val height = ((region.bottom - region.top) * original.height).toInt().coerceIn(1, original.height - y)
            val crop = Bitmap.createBitmap(original, x, y, width, height)
            val tokens = try { OcrEngine.recognize(crop).map { word -> word.toTextToken().let { it.copy(x0 = it.x0 + x, x1 = it.x1 + x, y0 = original.height - y - height + it.y0, y1 = original.height - y - height + it.y1) } } } finally { if (crop !== original) crop.recycle() }
            val retained = page.tokens.filterNot { it.cx / original.width in region.left..region.right && (original.height - it.cy) / original.height in region.top..region.bottom }
            val parsed = StructuredTimetableParser.extract(retained + tokens, original.width.toFloat(), original.height.toFloat(), settings, page.index, rowEdges = TableVisualEvidence.horizontalEdges(original), visualCells = TableVisualEvidence.coloredCells(original))
            val ids = parsed.regions.filter {
                val overlap = minOf(it.bottom, region.bottom) - maxOf(it.top, region.top)
                it.left < region.right && it.right > region.left && overlap > minOf(it.bottom - it.top, region.bottom - region.top) * .5f
            }.map { it.id }.toSet()
            return parsed.candidates.filter { it.sourceRegion in ids }.map { it.copy(sourceRegion = region.id) }.ifEmpty {
                listOf(CandidateCourse("", dayOfWeek = 0, startSection = 0, duration = 1, startWeek = 1, endWeek = settings.totalWeeks, weekType = 0, rawLines = tokens.map { it.text }, sourceRegion = region.id, needsReview = true))
            }
        } finally { original.recycle() }
    }

    fun applyAlternative(page: ImportPage, region: ImportRegion, settings: AppSettings, alternative: OcrAlternative): List<CandidateCourse> {
        val bitmap = BitmapFactory.decodeFile(page.recognitionImage.absolutePath) ?: error("识别原图不可用")
        try {
            val tokens = page.tokens.map { token ->
                if(token.cx / page.width in region.left..region.right && (page.height - token.cy) / page.height in region.top..region.bottom &&
                    OcrDecisions.normalize(token.text) == OcrDecisions.normalize(alternative.original)) token.copy(text = alternative.candidate, needsReview = false, alternatives = emptyList()) else token
            }
            val result = StructuredTimetableParser.extract(tokens, page.width.toFloat(), page.height.toFloat(), settings, page.index,
                rowEdges = TableVisualEvidence.horizontalEdges(bitmap), visualCells = TableVisualEvidence.coloredCells(bitmap) + TableVisualEvidence.ruledCells(bitmap, tokens))
            val ids = result.regions.filter { it.left < region.right && it.right > region.left && it.top < region.bottom && it.bottom > region.top }.map { it.id }.toSet()
            return result.candidates.filter { it.sourceRegion in ids }.map {
                it.copy(sourceRegion = region.id, name = if (alternative.field == OcrField.NAME) alternative.candidate else it.name)
            }
        } finally { bitmap.recycle() }
    }
}
