package com.coursetable.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.coursetable.app.data.AppSettings
import java.io.File

data class ImportPage(val index: Int, val image: File, val width: Int, val height: Int, val tokens: List<TextToken>)
class VisualImportSession(val directory: File, val pages: List<ImportPage>, val result: PdfParseResult, val path: String, val sourceImage: PreparedImportImage? = null, val selection: ImageSelection? = null) : java.io.Closeable {
    override fun close() { directory.deleteRecursively(); TinyTextRecognizer.release() }
}

object TableVisualEvidence {
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
        for (y in 1 until bitmap.height - 1) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            var count = 0
            for (x in bitmap.width / 40 until bitmap.width * 39 / 40 step 2) {
                val p = pixels[x]; val gray = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
                if (gray in 0..245 && absColor(p) < 20) count++
            }
            if (count > bitmap.width * .32 && (result.isEmpty() || bitmap.height - y < result.last() - 4)) result.add((bitmap.height - y).toFloat())
        }
        return result
    }
    private fun absColor(p: Int) = maxOf(Color.red(p), Color.green(p), Color.blue(p)) - minOf(Color.red(p), Color.green(p), Color.blue(p))
}

object VisualTimetableImporter {
    private fun compatibleColumns(headers: List<TextToken>, tokens: List<TextToken>): Boolean {
        val name = headers.firstOrNull { it.text.replace(" ", "") == "课程名称" }
        val time = headers.firstOrNull { it.text.contains("上课时间") }
        if (name != null && time != null) {
            val sorted = headers.sortedBy { it.cx }
            val i = sorted.indexOf(name)
            val left = sorted.getOrNull(i - 1)?.let { (it.cx + name.cx) / 2 } ?: 0f
            val right = sorted.getOrNull(i + 1)?.let { (it.cx + name.cx) / 2 } ?: time.x0
            return tokens.any { it.cx in left..right && it.text.any { c -> c.isLetter() } } &&
                tokens.any { it.cx > (time.cx + (sorted.getOrNull(sorted.indexOf(time) - 1)?.cx ?: name.cx)) / 2 && Regex("(?:星期|周)[一二三四五六日天1-7]").containsMatchIn(it.text.replace(" ", "")) }
        }
        val days = headers.filter { Regex("(?:星期|周)[一二三四五六日天]").matches(it.text.trim()) }
        return days.size >= 2 && tokens.any { it.cx < days.minOf { d -> d.x0 } && Regex("\\d.*节|\\d.*:.*\\d").containsMatchIn(it.text) }
    }
    suspend fun open(context: Context, uri: Uri, pdf: Boolean, settings: AppSettings): VisualImportSession {
        val dir = File(context.cacheDir, "timetable-review-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val pages = mutableListOf<ImportPage>(); val candidates = mutableListOf<CandidateCourse>()
        val regions = mutableListOf<ImportRegion>(); val warnings = mutableListOf<String>(); var usedText = false
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
            suspend fun parse(index: Int, bmp: Bitmap) {
                val text = textPages.getOrNull(index).orEmpty()
                // PDF tokens and rendered bitmaps share normalized display coordinates (renderer scale = 2).
                var tokens = text.map { it.copy(x0 = it.x0 * 2, x1 = it.x1 * 2, y0 = it.y0 * 2, y1 = it.y1 * 2) }
                val hasText = tokens.size >= 10
                if (!hasText) tokens = OcrEngine.recognize(bmp).map { it.toTextToken() }
                val compatible = inherited.isNotEmpty() && previousWidth == bmp.width && compatibleColumns(inherited, tokens)
                val edges = TableVisualEvidence.horizontalEdges(bmp); val cells = TableVisualEvidence.coloredCells(bmp) + TableVisualEvidence.ruledCells(bmp, tokens)
                var parsed = StructuredTimetableParser.extract(tokens, bmp.width.toFloat(), bmp.height.toFloat(), settings, index, if (compatible) inherited else emptyList(), edges, cells)
                if (!hasText || parsed.candidates.isEmpty()) {
                    val recognized = ImageTimetableOcr.recognizeStructured(bmp, settings, index, if (compatible) inherited else emptyList(), if (!hasText) tokens else null)
                    tokens = recognized.first; parsed = recognized.second
                } else usedText = true
                val name = tokens.firstOrNull { it.text.replace(" ", "") == "课程名称" }
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
                pages.add(ImportPage(index, file, bmp.width, bmp.height, tokens))
                val remaining = (ImportPolicy.MAX_OUTPUT_COURSES - candidates.size).coerceAtLeast(0)
                candidates.addAll(parsed.candidates.take(remaining))
                if (parsed.candidates.size > remaining && warnings.none { it.contains("课程数量超过") }) {
                    warnings.add("课程数量超过 ${ImportPolicy.MAX_OUTPUT_COURSES} 门，已停止追加")
                }
                regions.addAll(parsed.regions)
                warnings.addAll(parsed.warnings.map { "第${index + 1}页：$it" })
            }
            if (pdf) warnings.addAll(PdfRendererUtil.forEachPage(context, sourceUri, onPage = { i, bmp -> parse(i, bmp) }))
            else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
                val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                    ?: error("无法读取图片")
                try { parse(0, bmp) } finally { bmp.recycle() }
            }
            if (pages.isEmpty()) error(warnings.joinToString("；").ifBlank { "没有可读取的页面" })
            return VisualImportSession(dir, pages, PdfParseResult(ImageTimetableOcr.mergeAcrossPages(candidates), warnings, regions), if (usedText) "文字层直读" else "OCR 识别")
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }

    suspend fun recognizeRegion(page: ImportPage, region: ImportRegion, settings: AppSettings): List<CandidateCourse> {
        val original = BitmapFactory.decodeFile(page.image.absolutePath) ?: error("无法读取预览")
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
}
