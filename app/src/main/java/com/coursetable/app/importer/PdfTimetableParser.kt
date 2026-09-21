package com.coursetable.app.importer

import com.coursetable.app.data.WeekType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayInputStream
import java.io.File

/**
 * PDF 文字层读取器：用 pdfbox-android 提取带坐标的文字块。
 * 仅用于「PDF 有文字层」时的直读，任何异常都返回空结果（由调用方决定是否走 OCR 兜底）。
 */
object PdfTextReader {

    fun extract(bytes: ByteArray): List<TextToken> = extractByPage(bytes).flatten()

    fun extractByPage(bytes: ByteArray): List<List<TextToken>> = extractByPage {
        PDDocument.load(
            ByteArrayInputStream(bytes),
            MemoryUsageSetting.setupMixed(8L * 1024 * 1024, 128L * 1024 * 1024)
        )
    }

    fun extractByPage(file: File): List<List<TextToken>> = extractByPage {
        PDDocument.load(
            file,
            MemoryUsageSetting.setupMixed(8L * 1024 * 1024, 128L * 1024 * 1024)
        )
    }

    private inline fun extractByPage(load: () -> PDDocument): List<List<TextToken>> {
        val pages = mutableListOf<List<TextToken>>()
        try {
            load().use { doc ->
                if (doc.numberOfPages > ImportPolicy.MAX_PDF_PAGES) return emptyList()
                for (i in 0 until doc.numberOfPages) {
                    val page = doc.getPage(i)
                    val rotation = page.rotation
                    val mb = page.mediaBox
                    val displayHeight = if (rotation == 90 || rotation == 270) mb.width else mb.height
                    val stripper = TokenStripper(displayHeight)
                    stripper.sortByPosition = true
                    stripper.startPage = i + 1
                    stripper.endPage = i + 1
                    try {
                        stripper.getText(doc)
                    } catch (t: Throwable) {
                        pages.add(emptyList())
                        continue
                    }
                    pages.add(stripper.tokens)
                }
            }
        } catch (t: Throwable) {
            return emptyList()
        }
        return pages
    }

    private class TokenStripper(private val displayHeight: Float) : PDFTextStripper() {
        val tokens = mutableListOf<TextToken>()
        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (text.isBlank() || textPositions.isEmpty()) return
            val first = textPositions.first()
            val last = textPositions.last()
            val x0 = first.xDirAdj
            val x1 = last.xDirAdj + last.widthDirAdj
            val yTopDown = first.yDirAdj
            val yBottomDown = first.yDirAdj + first.heightDir
            val yTop = displayHeight - yTopDown
            val yBottom = displayHeight - yBottomDown
            tokens.add(TextToken(x0, yBottom, x1, yTop, text))
        }
    }
}

/**
 * PDF/图片课表解析的文字块（统一使用 y 向上坐标系：cell 越靠上 y 值越大）
 */
data class TextToken(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val text: String, val needsReview: Boolean = false) {
    val cx: Float get() = (x0 + x1) / 2f
    val cy: Float get() = (y0 + y1) / 2f
    val height: Float get() = (y1 - y0).coerceAtLeast(4f)
}

/**
 * 候选课程（OCR/PDF 解析结果，导入前供预览）
 */
data class CandidateCourse(
    val name: String,
    val location: String = "",
    val teacher: String = "",
    val dayOfWeek: Int,
    val startSection: Int,
    val duration: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int,
    val rawLines: List<String> = emptyList(),
    val sourceRegion: String? = null,
    val needsReview: Boolean = false,
    val sourceRegions: Set<String> = emptySet(),
    val sourceRecords: List<CandidateCourse> = emptyList(),
    val draftId: String? = null
) {
    fun belongsTo(id: String?) = id != null && (sourceRegion == id || id in sourceRegions)
    fun excludingRegion(id: String): List<CandidateCourse> = if (!belongsTo(id)) listOf(this)
        else if (sourceRecords.isNotEmpty()) sourceRecords.flatMap { it.excludingRegion(id) } else emptyList()
}

data class PdfParseResult(val candidates: List<CandidateCourse>, val warnings: List<String>, val regions: List<ImportRegion> = emptyList())

/**
 * 网格重建（纯逻辑，便于单元测试）
 *
 * 策略：
 * 1. 定位星期表头行，确定各星期列的范围；
 * 2. 用左侧的节次/时间标签作为行锚点（若无则按文字行聚类兜底）；
 * 3. 每个 (行, 列) 格子的文字解析成候选课程。
 */
object PdfGridExtractor {

    private val DAY_RE = Regex("(星期|周)[一二三四五六日天]")
    private val DAY_NUM = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7
    )
    private val SECTION_LABEL_RE = Regex("第?\\s*(\\d{1,2})\\s*节")

    fun extract(tokens: List<TextToken>): PdfParseResult {
        val warnings = mutableListOf<String>()
        val words = tokens.filter { it.text.isNotBlank() }
        if (words.isEmpty()) return PdfParseResult(emptyList(), warnings)

        // 1) 星期表头 → 列
        val dayWords = words.filter { DAY_RE.containsMatchIn(it.text) }
        val headerBand = clusterByY(dayWords).maxByOrNull { it.size } ?: dayWords
        val dayX = mutableMapOf<Int, Float>()
        for (w in headerBand) {
            extractDay(w.text)?.let { dayX[it] = w.cx }
        }
        if (dayX.isEmpty()) {
            return PdfParseResult(emptyList(), warnings + "未找到星期表头，无法确定列。")
        }
        val colRanges = buildColRanges(dayX)

        // 2) 教务系统「标签格式」直读：字段以 / 分隔（场地:/教师:/教学班:…）
        if (words.any { it.text.contains("教学班:") || it.text.contains("课程性质") }) {
            val labeled = extractLabeledPages(listOf(words))
            if (labeled != null && labeled.candidates.isNotEmpty()) {
                return labeled
            }
        }

        val firstColLeft = colRanges.first().second.first

        // 3) 数据区（表头下方）
        val headerTopY = headerBand.maxOf { it.y1 }
        val dataWords = words.filter { it.y1 <= headerTopY + 2f }

        // 4) 行锚点：左侧节次标签
        val leftWords = dataWords.filter { it.cx < firstColLeft }
        val anchorBands = clusterByY(leftWords).filter { band ->
            band.isNotEmpty() && (SECTION_LABEL_RE.containsMatchIn(band.joinToString("") { it.text }) ||
                band.any { Regex("\\d{1,2}:\\d{2}").containsMatchIn(it.text) })
        }
        val rowAnchors: List<RowAnchor> = if (anchorBands.size >= 2) {
            anchorBands.sortedBy { it.minOf { t -> t.cy } }.mapIndexed { idx, band ->
                val labelText = band.sortedBy { it.cx }.joinToString("") { it.text }
                val section = SECTION_LABEL_RE.find(labelText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: (idx + 1)
                RowAnchor(centerY = band.map { it.cy }.average().toFloat(), section = section, pitch = 0f)
            }.let { computePitch(it) }
        } else {
            // 兜底：数据文字按 y 聚类成行
            warnings.add("未找到左侧节次标签，按文字行粗略分行。")
            clusterByY(dataWords).sortedBy { it.minOf { t -> t.cy } }.mapIndexed { idx, band ->
                RowAnchor(centerY = band.map { it.cy }.average().toFloat(), section = idx + 1, pitch = 0f)
            }.let { computePitch(it) }
        }
        if (rowAnchors.isEmpty()) {
            return PdfParseResult(emptyList(), warnings + "未识别到课表行。")
        }

        // 5) 逐格解析
        val candidates = mutableListOf<CandidateCourse>()
        for (anchor in rowAnchors) {
            val yMin = anchor.centerY - anchor.pitch / 2f
            val yMax = anchor.centerY + anchor.pitch / 2f
            for ((day, range) in colRanges) {
                val cellWords = dataWords.filter {
                    it.cy in yMin..yMax && it.cx in range.first..range.second
                }
                if (cellWords.isEmpty()) continue
                val lines = buildLines(cellWords)
                val parsed = PdfCellParser.parse(lines, day, anchor.section)
                if (parsed != null) candidates.add(parsed)
            }
        }

        val merged = mergeConsecutiveRows(candidates)
        return PdfParseResult(merged, warnings)
    }

    /** 多页教务系统 PDF：页 0 有星期表头，后续页只有续行；按页分别解析后汇总 */
    fun extractLabeledPages(pages: List<List<TextToken>>): PdfParseResult? {
        val all = pages.flatten().filter { it.text.isNotBlank() }
        if (all.none { it.text.contains("教学班:") || it.text.contains("课程性质") }) return null
        val dayWords = all.filter { DAY_RE.containsMatchIn(it.text) }
        val headerBand = clusterByY(dayWords).maxByOrNull { it.size } ?: dayWords
        val dayX = mutableMapOf<Int, Float>()
        for (w in headerBand) {
            extractDay(w.text)?.let { dayX[it] = w.cx }
        }
        if (dayX.isEmpty()) return null
        val colRanges = buildColRanges(dayX)
        val headerTopY = headerBand.maxOf { it.y1 }

        val candidates = mutableListOf<CandidateCourse>()
        for (page in pages) {
            val hasHeader = page.any { DAY_RE.containsMatchIn(it.text) }
            val cutoff = if (hasHeader) headerTopY + 2f else Float.POSITIVE_INFINITY
            val usable = page.filter { it.y1 <= cutoff }
            extractLabeled(listOf(usable), colRanges, candidates)
        }
        return if (candidates.isEmpty()) PdfParseResult(emptyList(), emptyList()) else PdfParseResult(candidates, emptyList())
    }

    private fun buildColRanges(dayX: Map<Int, Float>): List<Pair<Int, Pair<Float, Float>>> {
        val ordered = reorderColumns(dayX.toList().sortedBy { it.second })
        return ordered.mapIndexed { i, (day, cx) ->
            val left = if (i == 0) {
                val n = ordered.getOrNull(1)?.second ?: cx
                cx - (n - cx) / 2f
            } else {
                (cx + ordered[i - 1].second) / 2f
            }
            val right = if (i == ordered.lastIndex) {
                val p = ordered.getOrNull(i - 1)?.second ?: cx
                cx + (cx - p) / 2f
            } else {
                (cx + ordered[i + 1].second) / 2f
            }
            day to (left to right)
        }
    }

    /** 教务系统「标签格式」：按星期列把换行拆分的长字段重新拼成完整课程 */
    private fun extractLabeled(
        pages: List<List<TextToken>>,
        colRanges: List<Pair<Int, Pair<Float, Float>>>,
        out: MutableList<CandidateCourse> = mutableListOf()
    ): PdfParseResult {
        val candidates = out
        for (page in pages) {
            for ((day, range) in colRanges) {
                val colWords = page.filter {
                    it.cx in range.first..range.second &&
                        !DAY_RE.containsMatchIn(it.text) &&
                        !it.text.contains("打印时间") &&
                        !it.text.contains("实践课程")
                }.sortedByDescending { it.y1 }

                // 按「课程名行」切块：课程名不含 / : ；等分隔符、不是纯数字
                val blocks = mutableListOf<MutableList<String>>()
                for (w in colWords) {
                    val t = w.text.trim()
                    if (t.isEmpty()) continue
                    if (isNameLine(t)) {
                        blocks.add(mutableListOf(t))
                    } else {
                        if (blocks.isEmpty()) blocks.add(mutableListOf())
                        blocks.last().add(t)
                    }
                }

                for (block in blocks) {
                    if (block.isEmpty()) continue
                    val parsed = PdfCellParser.parseLabeled(block, day)
                    if (parsed != null) candidates.add(parsed)
                }
            }
        }
        return PdfParseResult(candidates, emptyList())
    }

    private fun isNameLine(t: String): Boolean {
        if (t.length < 2) return false
        if (t.any { it == '/' || it == ':' || it == '：' || it == ';' || it == '；' }) return false
        if (t.all { it.isDigit() || it.isWhitespace() }) return false
        if (t.startsWith("(") || t.startsWith("（")) return false
        if (t.contains("节") || t.contains("周") || t.contains("学分") || t.contains("教学班")) return false
        return t.any { it in '\u4e00'..'\u9fa5' } || t.any { it.isLetter() }
    }

    private data class RowAnchor(val centerY: Float, val section: Int, val pitch: Float)

    private fun computePitch(anchors: List<RowAnchor>): List<RowAnchor> {
        if (anchors.size < 2) return anchors
        val gaps = anchors.zipWithNext().map { (a, b) -> b.centerY - a.centerY }
        val pitch = gaps.sorted()[gaps.size / 2].coerceAtLeast(20f)
        return anchors.map { it.copy(pitch = pitch) }
    }

    /** 若表从周日开始（7 在最左），重排为周一到周日 */
    private fun reorderColumns(cols: List<Pair<Int, Float>>): List<Pair<Int, Float>> {
        if (cols.isEmpty()) return cols
        if (cols.first().first == 7 && cols.any { it.first != 7 }) {
            val idx = cols.indexOfFirst { it.first == 7 }
            return cols.subList(idx + 1, cols.size) + cols.subList(0, idx + 1)
        }
        return cols
    }

    private fun extractDay(text: String): Int? {
        val m = DAY_RE.find(text.trim()) ?: return null
        return DAY_NUM[m.value.last().toString()]
    }

    /** 细粒度按 y 聚类成行带 */
    private fun clusterByY(words: List<TextToken>): List<List<TextToken>> {
        if (words.isEmpty()) return emptyList()
        val sorted = words.sortedBy { it.cy }
        val bands = mutableListOf<MutableList<TextToken>>()
        var current = mutableListOf<TextToken>()
        var currentCenter = sorted.first().cy
        for (w in sorted) {
            val h = w.height
            if (current.isNotEmpty() && kotlin.math.abs(w.cy - currentCenter) > h * 1.2f) {
                bands.add(current)
                current = mutableListOf()
            }
            current.add(w)
            currentCenter = current.map { it.cy }.average().toFloat()
        }
        if (current.isNotEmpty()) bands.add(current)
        return bands
    }

    /** 单元格内文字按行重建（每行按 x 排序拼接） */
    private fun buildLines(words: List<TextToken>): List<String> {
        if (words.isEmpty()) return emptyList()
        val bands = clusterByY(words).sortedBy { it.minOf { t -> t.cy } }
        return bands.map { band ->
            band.sortedBy { it.x0 }.joinToString("") { it.text }
        }
    }

    /** 同一天同节次、名称相同的课程合并周次范围 */
    private fun mergeConsecutiveRows(candidates: List<CandidateCourse>): List<CandidateCourse> {
        val seen = mutableMapOf<Pair<Int, Int>, CandidateCourse>()
        for (c in candidates) {
            val key = c.dayOfWeek to c.startSection
            val existing = seen[key]
            if (existing != null && existing.name == c.name) {
                seen[key] = existing.copy(
                    startWeek = minOf(existing.startWeek, c.startWeek),
                    endWeek = maxOf(existing.endWeek, c.endWeek)
                )
            } else {
                seen[key] = c
            }
        }
        return seen.values.toList()
    }
}

/**
 * 单元格文字解析
 */
object PdfCellParser {

    private val WEEK_RANGE_RE = Regex("(\\d{1,2})\\s*[-~～至]\\s*(\\d{1,2})\\s*周")
    private val WEEK_SINGLE_RE = Regex("(\\d{1,2})\\s*周")
    private val SECTION_RANGE_RE = Regex("第?\\s*(\\d{1,2})\\s*[-~]\\s*(\\d{1,2})\\s*节")
    private val SECTION_SINGLE_RE = Regex("第?\\s*(\\d{1,2})\\s*节")
    private val TIME_RE = Regex("\\d{1,2}:\\d{2}\\s*[-~]\\s*\\d{1,2}:\\d{2}")
    private val WEEKDAY_RE = Regex("星期[一二三四五六日天]|周[一二三四五六日天]")

    private val LOCATION_KEYWORDS = listOf(
        "教学楼", "楼", "馆", "教室", "室", "园", "区", "栋", "幢",
        "阶梯", "实验楼", "实训楼", "图书馆", "中心", "场地", "操场", "操场"
    )
    private val ROOM_CODE_RE = Regex("^[A-Za-z]?\\d{1,4}(-\\d{1,4})?[A-Za-z]?$")
    private val BUILDING_ROOM_RE = Regex("^[一二三四五六七八九十]?\\d*号?楼\\d{1,4}$")
    private val CAMPUS_ROOM_RE = Regex("^[\\u4e00-\\u9fa5A-Za-z0-9]{1,14}[楼栋馆室][A-Za-z]?\\d{1,4}$")

    private val TEACHER_SUFFIX_RE = Regex("老师|教授|讲师|助教|教师|导师")
    private val TEACHER_PREFIX_RE = Regex("教师|任课|主讲|授课")
    private val PURE_NAME_RE = Regex("^[\\u4e00-\\u9fa5]{2,4}$")
    private val LABEL_LOCATION_RE = Regex("(?:场地|教室|地点)[:：]([^/]+)")
    private val LABEL_TEACHER_RE = Regex("(?:教师|任课教师)[:：]([^/]+)")
    private val TEACHER_EXCLUDE_WORDS = listOf(
        "校区", "楼", "馆", "室", "周", "节", "程", "息", "网",
        "大", "项", "管", "场", "教", "课", "院", "系", "学", "操"
    )
    private val SKIP_WORDS = listOf("上午", "下午", "晚上", "学分", "学时", "周学时")
    private val NON_NAMING_RE = Regex("^[0-9\\s:.~\\-（）()]+$")

    fun parse(lines: List<String>, dayOfWeek: Int, defaultSection: Int): CandidateCourse? {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        val text = clean.joinToString("\n")

        // 教务系统导出格式：字段用 / 分隔（如 场地:xxx/教师:xxx），且可能被换行拆散
        tryParseLabeled(clean, dayOfWeek, defaultSection)?.let { return it }

        val (startWeek, endWeek) = parseWeeks(text)
        val (startSection, duration) = parseSections(text, defaultSection)
        var name = findName(clean)
        var teacher = findTeacher(clean, name.orEmpty())
        var location = findLocation(clean, name.orEmpty())

        // OCR 常把多个字段合成一行，按空白分词后逐 token 归类兜底
        if (name == null || teacher.isBlank() || location.isBlank() || isDirtyName(name)) {
            val merged = classifyTokens(tokenize(clean))
            if (isDirtyName(name) && merged.name != null) name = merged.name
            if (name == null && merged.name != null) name = merged.name
            if (teacher.isBlank()) teacher = merged.teacher
            if (location.isBlank()) location = merged.location
        }

        val finalName = name ?: return null
        val weekType = when {
            text.contains("单周") || text.contains("(单)") || text.contains("奇数周") -> WeekType.ODD.code
            text.contains("双周") || text.contains("(双)") || text.contains("偶数周") -> WeekType.EVEN.code
            else -> WeekType.ALL.code
        }

        return CandidateCourse(
            name = finalName,
            location = location,
            teacher = teacher,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            duration = duration,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = weekType,
            rawLines = clean
        )
    }

    fun parseLabeled(lines: List<String>, dayOfWeek: Int): CandidateCourse? {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        return tryParseLabeled(clean, dayOfWeek, 1)
    }

    /** 解析「教务系统导出」标签格式：课程名一行 + / 分隔的 场地:/教师:/周次/节次 字段 */
    private fun tryParseLabeled(
        clean: List<String>,
        dayOfWeek: Int,
        defaultSection: Int
    ): CandidateCourse? {
        val joined = clean.joinToString("")
        val labeled = joined.contains("/") &&
            (joined.contains("场地:") || joined.contains("教室:") || joined.contains("地点:") || joined.contains("教师:"))
        if (!labeled) return null

        val name = clean.firstOrNull { !it.contains("/") && !it.contains(":") && !it.contains("：") }
            ?.trim() ?: return null
        val (startWeek, endWeek) = parseWeeks(joined)
        val (startSection, duration) = parseSections(joined, defaultSection)
        val location = LABEL_LOCATION_RE.find(joined)?.groupValues?.get(1)?.trim().orEmpty()
        val teacher = LABEL_TEACHER_RE.find(joined)?.groupValues?.get(1)?.trim().orEmpty()
        val weekType = when {
            joined.contains("单周") || joined.contains("(单)") || joined.contains("奇数周") -> WeekType.ODD.code
            joined.contains("双周") || joined.contains("(双)") || joined.contains("偶数周") -> WeekType.EVEN.code
            else -> WeekType.ALL.code
        }
        return CandidateCourse(
            name = name,
            location = location,
            teacher = teacher,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            duration = duration,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = weekType,
            rawLines = clean
        )
    }

    private fun tokenize(lines: List<String>): List<String> {
        val tokens = mutableListOf<String>()
        for (line in lines) {
            for (t in line.split(Regex("[\\s　、，,；;]+"))) {
                val token = t.trim()
                if (token.isNotEmpty()) tokens.add(token)
            }
        }
        return tokens
    }

    /** 把 OCR 合并行的 token 归类为课程名/教师/地点 */
    private fun classifyTokens(tokens: List<String>): MergedFields {
        var name: String? = null
        var teacher = ""
        var location = ""
        for (token in tokens) {
            if (WEEK_RANGE_RE.containsMatchIn(token) || WEEK_SINGLE_RE.containsMatchIn(token) ||
                SECTION_RANGE_RE.containsMatchIn(token) || SECTION_SINGLE_RE.containsMatchIn(token) ||
                TIME_RE.containsMatchIn(token) || WEEKDAY_RE.containsMatchIn(token) ||
                NON_NAMING_RE.matches(token) || SKIP_WORDS.any { token.contains(it) }
            ) continue
            if (isLocation(token)) {
                if (location.isEmpty()) location = token
                continue
            }
            if (isTeacherLike(token, name)) {
                if (teacher.isEmpty()) teacher = cleanTeacher(token)
                continue
            }
            if (name == null && PURE_NAME_RE.matches(token) && token.length >= 2) {
                name = token
            } else if (name == null) {
                name = token
            } else if (teacher.isEmpty() && PURE_NAME_RE.matches(token) &&
                TEACHER_EXCLUDE_WORDS.none { token.contains(it) }
            ) {
                teacher = token
            }
        }
        return MergedFields(name, teacher, location)
    }

    private data class MergedFields(val name: String?, val teacher: String, val location: String)

    private fun isDirtyName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        if (WEEK_RANGE_RE.containsMatchIn(name)) return true
        if (WEEK_SINGLE_RE.containsMatchIn(name)) return true
        if (SECTION_RANGE_RE.containsMatchIn(name)) return true
        if (SECTION_SINGLE_RE.containsMatchIn(name)) return true
        if (TIME_RE.containsMatchIn(name)) return true
        if (WEEKDAY_RE.containsMatchIn(name)) return true
        if (isLocation(name)) return true
        if (isTeacherLine(name)) return true
        return name.split(Regex("[\\s　、，,；;]+")).count { it.isNotBlank() } > 2
    }

    private fun isTeacherLike(token: String, name: String?): Boolean {
        if (TEACHER_SUFFIX_RE.containsMatchIn(token)) return true
        if (TEACHER_PREFIX_RE.containsMatchIn(token) && Regex("[:：]").containsMatchIn(token)) return true
        return false
    }

    private fun cleanTeacher(token: String): String {
        return token.trim()
            .removeSuffix("老师").removeSuffix("教授").removeSuffix("讲师")
            .removeSuffix("助教").removeSuffix("教师").removeSuffix("导师")
            .removePrefix("教师").removePrefix("任课").removePrefix("主讲").removePrefix("授课")
            .trim()
    }

    private fun parseWeeks(text: String): Pair<Int, Int> {
        WEEK_RANGE_RE.find(text)?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            val e = m.groupValues[2].toIntOrNull()
            if (s != null && e != null && e >= s) return s to e
        }
        WEEK_SINGLE_RE.find(text)?.let { m ->
            val w = m.groupValues[1].toIntOrNull()
            if (w != null) return w to w
        }
        return 1 to 40 // 未标注时默认整个学期（40 周兜底，导入时按学期总周数裁剪）
    }

    private fun parseSections(text: String, default: Int): Pair<Int, Int> {
        SECTION_RANGE_RE.find(text)?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            val e = m.groupValues[2].toIntOrNull()
            if (s != null && e != null && e >= s) return s to (e - s + 1)
        }
        SECTION_SINGLE_RE.find(text)?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            if (s != null) return s to 1
        }
        return default to 1
    }

    /** 课程名：第一个既不是时间/周次/节次，也不是地点/教师的行 */
    private fun findName(lines: List<String>): String? {
        for (line in lines) {
            if (line.isBlank()) continue
            if (WEEK_RANGE_RE.containsMatchIn(line)) continue
            if (WEEK_SINGLE_RE.containsMatchIn(line)) continue
            if (SECTION_RANGE_RE.containsMatchIn(line)) continue
            if (SECTION_SINGLE_RE.containsMatchIn(line)) continue
            if (TIME_RE.containsMatchIn(line)) continue
            if (WEEKDAY_RE.containsMatchIn(line)) continue
            if (isLocation(line)) continue
            if (isTeacherLine(line)) continue
            if (line.all { it.isDigit() }) continue
            if (NON_NAMING_RE.matches(line)) continue
            if (SKIP_WORDS.any { line.contains(it) }) continue
            return line.trim()
        }
        // 兜底：第一个非空行
        return lines.firstOrNull { it.isNotBlank() }?.trim()
    }

    /** 教师：优先「教师：xxx」样式，其次带后缀的行，最后是紧跟课程名的 2~4 字纯姓名行 */
    private fun findTeacher(lines: List<String>, name: String): String {
        // 1) 教师前缀样式：教师：张三 / 任课教师：王老师
        for (line in lines) {
            val m = Regex("(教师|任课|授课|主讲)[：: ]+([\\u4e00-\\u9fa5]{1,8})").find(line)
            if (m != null) return m.groupValues[2].trim()
        }
        // 2) 独立行带后缀
        for (line in lines) {
            if (line == name) continue
            if (TEACHER_SUFFIX_RE.containsMatchIn(line) && !isLocation(line)) {
                return line.trim().removePrefix("教师").trim()
            }
        }
        // 3) 同行：课程名与教师夹杂（如 "高等数学 王老师"）
        val sameLine = Regex("([\\u4e00-\\u9fa5]{2,20})\\s*(\\S{1,8}(?:老师|教授|讲师))").find(name)
        if (TEACHER_SUFFIX_RE.containsMatchIn(name)) {
            val m = Regex("^(\\S+?)[ 　,，;；]?([\\u4e00-\\u9fa5]{1,4}(?:老师|教授|讲师))$").find(name.trim())
            if (m != null) return m.groupValues[2]
            return name.trim().takeLastWhile { it != '（' && it != '(' && it != ' ' }
        }
        // 4) 2~4 字纯姓名行（排除课程名，且不在首行可能是课程名的位置）
        val linesWithoutName = lines.filter { it.trim() != name }
        for (line in linesWithoutName) {
            val t = line.trim()
            if (t.length in 2..4 && PURE_NAME_RE.matches(t) &&
                !SKIP_WORDS.any { t.contains(it) } &&
                TEACHER_EXCLUDE_WORDS.none { t.contains(it) }
            ) {
                return t
            }
        }
        return ""
    }

    private fun isTeacherLine(line: String): Boolean {
        if (TEACHER_SUFFIX_RE.containsMatchIn(line)) return true
        if (TEACHER_PREFIX_RE.containsMatchIn(line) && Regex("[:：]").containsMatchIn(line)) return true
        return false
    }

    /** 地点：带地点关键词的行，或房间号模式的行 */
    private fun findLocation(lines: List<String>, name: String): String {
        for (line in lines) {
            if (line == name) continue
            val t = line.trim()
            if (LOCATION_KEYWORDS.any { t.contains(it) }) return t
            if (ROOM_CODE_RE.matches(t) && !t.any { it.isDigit() } == false && t.any { it.isDigit() }) return t
            if (BUILDING_ROOM_RE.matches(t)) return t
            if (CAMPUS_ROOM_RE.matches(t)) return t
        }
        return ""
    }

    private fun isLocation(line: String): Boolean {
        val t = line.trim()
        if (BUILDING_ROOM_RE.matches(t)) return true
        if (CAMPUS_ROOM_RE.matches(t)) return true
        if (ROOM_CODE_RE.matches(t) && t.any { it.isDigit() }) return true
        return LOCATION_KEYWORDS.any { t.contains(it) }
    }
}
