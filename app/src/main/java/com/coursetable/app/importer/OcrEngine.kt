package com.coursetable.app.importer

import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 基于 ML Kit 中文 OCR 的课表识别。
 *
 * OCR 结果（文字块 + 包围盒）会被转换为 [TextToken] 列表，直接喂给 [PdfGridExtractor]
 * 做网格重建，因此在图片坐标系与课表坐标系转换后，可复用已有的位置聚类逻辑。
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
        val imageHeight: Float
    ) {
        /** 转为课表坐标系（y 向上，越靠上 y 越大），供 PdfGridExtractor 使用 */
        fun toTextToken(): TextToken {
            val x0 = left
            val x1 = right
            val yTopPdf = imageHeight - top
            val yBottomPdf = imageHeight - bottom
            return TextToken(x0, yBottomPdf, x1, yTopPdf, text)
        }
    }

    /** 识别 Bitmap，返回文字块列表（不抛异常） */
    suspend fun recognize(bitmap: Bitmap): List<OcrWord> {
        val recognizer: TextRecognizer =
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = await(recognizer.process(image))
            val words = mutableListOf<OcrWord>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.trim()
                    if (text.isEmpty()) continue
                    val box = line.boundingBox
                    if (box == null) continue
                    words.add(
                        OcrWord(
                            text = text,
                            left = box.left.toFloat(),
                            top = box.top.toFloat(),
                            right = box.right.toFloat(),
                            bottom = box.bottom.toFloat(),
                            imageWidth = bitmap.width.toFloat(),
                            imageHeight = bitmap.height.toFloat()
                        )
                    )
                }
            }
            return words
        } finally {
            recognizer.close()
        }
    }

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
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
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: context.contentResolver.openInputStream(uri)?.use { ins ->
                    val tmp = java.io.File(context.cacheDir, "pdf_tmp_${System.currentTimeMillis()}.pdf")
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
        } catch (e: Exception) {
            return warnings + "PDF 渲染失败：${e.javaClass.simpleName} ${e.message}"
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
        // 1) 尝试文字层直读
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes != null && bytes.isNotEmpty()) {
            val pages = PdfTextReader.extractByPage(bytes)
            if (pages.flatten().count { it.text.isNotBlank() } >= 10) {
                val labeled = PdfGridExtractor.extractLabeledPages(pages)
                if (labeled != null && labeled.candidates.isNotEmpty()) {
                    return Triple(labeled, "text", emptyList())
                }
                val result = PdfGridExtractor.extract(pages.flatten())
                if (result.candidates.isNotEmpty()) {
                    return Triple(result, "text", emptyList())
                }
                // 有文字但网格重建失败：仅说明，继续尝试 OCR
            }
        }
        // 2) OCR 兜底
        val result = ImageTimetableOcr.parsePdfPages(context, uri)
        if (result.candidates.isNotEmpty()) {
            return Triple(result, "ocr", result.warnings)
        }
        return Triple(result, "ocr", result.warnings)
    }
}

/**
 * 统一 OCR 管线：把 Bitmap 图片识别成课程候选
 */
object ImageTimetableOcr {
    suspend fun parseImage(bitmap: Bitmap): PdfParseResult {
        val words = OcrEngine.recognize(bitmap)
        val tokens = words.map { it.toTextToken() }
        return PdfGridExtractor.extract(tokens)
    }

    /** 全页面 OCR：把多页 bitmap 逐页识别，汇总课程 */
    suspend fun parsePages(bitmaps: List<Bitmap>): PdfParseResult {
        val allCandidates = mutableListOf<CandidateCourse>()
        val warnings = mutableListOf<String>()
        bitmaps.forEachIndexed { idx, bmp ->
            val pageResult = parseImage(bmp)
            allCandidates.addAll(pageResult.candidates)
            warnings.addAll(pageResult.warnings.map { "第${idx + 1}页：$it" })
        }
        return PdfParseResult(mergeAcrossPages(allCandidates), warnings)
    }

    /** 逐页渲染 + OCR：渲染一页识别一页并立即回收，避免多页位图常驻内存 */
    suspend fun parsePdfPages(context: android.content.Context, uri: Uri): PdfParseResult {
        val allCandidates = mutableListOf<CandidateCourse>()
        val warnings = mutableListOf<String>()
        val renderWarnings = PdfRendererUtil.forEachPage(context, uri) { idx, bmp ->
            val pageResult = parseImage(bmp)
            allCandidates.addAll(pageResult.candidates)
            warnings.addAll(pageResult.warnings.map { "第${idx + 1}页：$it" })
        }
        return PdfParseResult(mergeAcrossPages(allCandidates), warnings + renderWarnings)
    }

    /** 跨页合并：同一课程（同名+同星期+同节次）合并周次范围 */
    fun mergeAcrossPages(candidates: List<CandidateCourse>): List<CandidateCourse> {
        val grouped = candidates.groupBy { Triple(it.name, it.dayOfWeek, it.startSection) }
        return grouped.values.map { list ->
            if (list.size == 1) {
                list.first()
            } else {
                val first = list.first()
                first.copy(
                    startWeek = list.minOf { it.startWeek },
                    endWeek = list.maxOf { it.endWeek },
                    duration = list.maxOf { it.duration },
                    location = list.firstOrNull { it.location.isNotBlank() }?.location ?: first.location,
                    teacher = list.firstOrNull { it.teacher.isNotBlank() }?.teacher ?: first.teacher
                )
            }
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
    }
}