package com.coursetable.app.importer

import com.coursetable.app.data.WeekType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object ExcelTimetableImporter {

    private const val MAX_ZIP_ENTRIES = 256
    private const val MAX_XML_BYTES = 16L * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024
    private const val MAX_ROWS = 10_000
    private const val MAX_COLUMNS = 256
    private const val MAX_CELLS = 100_000

    private val DAY_NUM = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7
    )
    private val DAY_RE = Regex("(星期|周)[一二三四五六日天]")
    private val SECTION_RE = Regex("第?\\s*(\\d{1,2})\\s*节?")

    fun parseCsv(content: String): PdfParseResult {
        val warnings = mutableListOf<String>()
        val rows = splitCsv(content)
        if (rows.isEmpty()) return PdfParseResult(emptyList(), warnings + "CSV 文件为空")

        val headerIdx = rows.indices.firstOrNull { i ->
            rows[i].any { cell -> cell.contains("课程") }
        } ?: -1
        if (headerIdx < 0) {
            return PdfParseResult(emptyList(), warnings + "未找到表头（需包含「课程名称」列）")
        }
        val header = rows[headerIdx]
        val colName = header.indexOfFirst { it.contains("课程") || it.contains("名称") }
        val colDay = header.indexOfFirst { it.contains("星期") || it.contains("周几") }
        val colStart = header.indexOfFirst { it.contains("开始节") || it.contains("开始") }
        val colEnd = header.indexOfFirst { it.contains("结束节") || it.contains("结束") }
        val colTeacher = header.indexOfFirst { it.contains("老师") || it.contains("教师") }
        val colLocation = header.indexOfFirst { it.contains("地点") || it.contains("教室") }
        val colWeeks = header.indexOfFirst { it.contains("周") && it != header.getOrNull(colDay) }
        if (colName < 0 || colDay < 0 || colStart < 0 || colWeeks < 0) {
            return PdfParseResult(emptyList(), warnings + "表头缺少必要列：课程名称/星期/开始节数/周数")
        }

        val candidates = mutableListOf<CandidateCourse>()
        for (i in (headerIdx + 1) until rows.size) {
            val row = rows[i]
            val name = cellAt(row, colName).trim().take(ImportPolicy.MAX_TEXT_FIELD)
            if (name.isEmpty()) continue
            val day = parseDay(cellAt(row, colDay))
            if (day == null) {
                warnings.add("第 ${i + 1} 行星期无效：${cellAt(row, colDay)}")
                continue
            }
            val startSection = cellAt(row, colStart).trim().toIntOrNull()
            val endSection = if (colEnd >= 0) cellAt(row, colEnd).trim().toIntOrNull() else startSection
            if (startSection == null) {
                warnings.add("第 ${i + 1} 行开始节数无效，已跳过：$name")
                continue
            }
            val duration = ((endSection ?: startSection) - startSection + 1).coerceAtLeast(1)
            val teacher = if (colTeacher >= 0) cellAt(row, colTeacher).trim().take(ImportPolicy.MAX_TEXT_FIELD) else ""
            val location = if (colLocation >= 0) cellAt(row, colLocation).trim().take(ImportPolicy.MAX_TEXT_FIELD) else ""
            val teacherClean = if (teacher == "无" || teacher.isEmpty()) "" else teacher
            val locationClean = if (location == "无" || location.isEmpty()) "" else location
            val weekSegments = parseWeekSegments(cellAt(row, colWeeks))
            if (weekSegments.isEmpty()) {
                warnings.add("第 ${i + 1} 行周数无法解析，已跳过：$name")
                continue
            }
            for ((ws, we, wt) in weekSegments) {
                if (candidates.size >= ImportPolicy.MAX_OUTPUT_COURSES) {
                    warnings.add("课程数量超过 ${ImportPolicy.MAX_OUTPUT_COURSES} 门，已停止读取")
                    return PdfParseResult(candidates, warnings)
                }
                candidates.add(
                    CandidateCourse(
                        name = name,
                        location = locationClean,
                        teacher = teacherClean,
                        dayOfWeek = day,
                        startSection = startSection,
                        duration = duration,
                        startWeek = ws,
                        endWeek = we,
                        weekType = wt,
                        rawLines = row.filter { it.isNotBlank() }.take(64).map { it.take(ImportPolicy.MAX_TEXT_FIELD) }
                    )
                )
            }
        }
        return PdfParseResult(candidates, warnings)
    }

    fun parseXlsx(bytes: ByteArray): PdfParseResult {
        val warnings = mutableListOf<String>()
        val shared = mutableListOf<String>()
        var sheetXml: ByteArray? = null
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                var entryCount = 0
                var extracted = 0L
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) throw ImportRejectedException("XLSX 包含过多文件")
                    val selected = entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml"
                    val entryData = readZipEntry(zip, selected, MAX_UNCOMPRESSED_BYTES - extracted)
                    extracted += entryData.bytesRead
                    if (extracted > MAX_UNCOMPRESSED_BYTES) throw ImportRejectedException("XLSX 解压后数据过大")
                    when (entry.name) {
                        "xl/sharedStrings.xml" -> {
                            val data = entryData.content ?: throw ImportRejectedException("XLSX 字符串表无法读取")
                            shared.addAll(readSharedStrings(data))
                        }
                        "xl/worksheets/sheet1.xml" -> {
                            sheetXml = entryData.content ?: throw ImportRejectedException("XLSX 工作表无法读取")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            return PdfParseResult(emptyList(), warnings + "无法读取 xlsx 文件：${e.message}")
        }
        val sheet = sheetXml ?: return PdfParseResult(emptyList(), warnings + "xlsx 中未找到工作表")
        return try {
            val grid = readSheetGrid(sheet, shared)
            parseGrid(grid, warnings)
        } catch (_: Exception) {
            PdfParseResult(emptyList(), warnings + "xlsx 内容异常或包含不安全的 XML")
        }
    }

    internal fun parseGrid(grid: List<List<String?>>, warnings: MutableList<String> = mutableListOf()): PdfParseResult {
        val headerIdx = grid.indices.maxByOrNull { i ->
            grid[i].count { cell -> cell != null && DAY_RE.containsMatchIn(cell) }
        } ?: -1
        if (headerIdx < 0 || grid[headerIdx].none { cell -> cell != null && DAY_RE.containsMatchIn(cell) }) {
            return PdfParseResult(emptyList(), warnings + "未找到星期表头行")
        }
        val dayByCol = mutableMapOf<Int, Int>()
        grid[headerIdx].forEachIndexed { col, cell ->
            if (cell != null) {
                extractDay(cell)?.let { dayByCol[col] = it }
            }
        }
        if (dayByCol.isEmpty()) return PdfParseResult(emptyList(), warnings + "表头无法识别星期列")

        val sectionRows = mutableListOf<Pair<Int, Int>>()
        for (i in (headerIdx + 1) until grid.size) {
            val row = grid[i]
            val first = row.getOrNull(0)?.trim() ?: continue
            val section = SECTION_RE.find(first)?.groupValues?.get(1)?.toIntOrNull()
                ?: first.toIntOrNull()
            if (section != null && section in 1..30) {
                sectionRows.add(i to section)
            }
        }

        val candidates = mutableListOf<CandidateCourse>()
        for ((rowIdx, section) in sectionRows) {
            for ((col, day) in dayByCol) {
                val cell = grid[rowIdx].getOrNull(col) ?: continue
                val lines = cell.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) continue
                val parsed = PdfCellParser.parse(lines, day, section)
                if (parsed != null) {
                    if (candidates.size >= ImportPolicy.MAX_OUTPUT_COURSES) {
                        warnings.add("课程数量超过 ${ImportPolicy.MAX_OUTPUT_COURSES} 门，已停止读取")
                        return PdfParseResult(candidates, warnings)
                    }
                    candidates.add(parsed)
                }
            }
        }
        return PdfParseResult(candidates, warnings)
    }

    private fun readSharedStrings(xml: ByteArray): List<String> {
        val doc = parseXml(xml)
        val result = mutableListOf<String>()
        val siList = doc.getElementsByTagNameNS("*", "si")
        if (siList.length > MAX_CELLS) throw ImportRejectedException("XLSX 共享字符串数量过多")
        for (i in 0 until siList.length) {
            result.add(collectText(siList.item(i), StringBuilder()).toString().take(ImportPolicy.MAX_TEXT_FIELD))
        }
        return result
    }

    private fun readSheetGrid(xml: ByteArray, shared: List<String>): List<List<String?>> {
        val doc = parseXml(xml)
        val cells = doc.getElementsByTagNameNS("*", "c")
        if (cells.length > MAX_CELLS) throw ImportRejectedException("XLSX 单元格数量过多")
        val map = mutableMapOf<Pair<Int, Int>, String>()
        var maxRow = -1
        var maxCol = -1
        for (i in 0 until cells.length) {
            val c = cells.item(i) as? Element ?: continue
            val ref = parseRef(c.getAttribute("r")) ?: continue
            val type = c.getAttribute("t")
            var value = ""
            when (type) {
                "s" -> {
                    val idx = firstText(c, "v").toIntOrNull()
                    if (idx != null && idx in shared.indices) value = shared[idx]
                }
                "inlineStr" -> value = collectText(c, StringBuilder()).toString()
                else -> value = firstText(c, "v")
            }
            if (value.isNotBlank()) {
                map[ref] = value.take(ImportPolicy.MAX_TEXT_FIELD)
                if (ref.first > maxRow) maxRow = ref.first
                if (ref.second > maxCol) maxCol = ref.second
            }
        }
        if (maxRow < 0) return emptyList()
        val grid = List(maxRow + 1) { row ->
            MutableList<String?>(maxCol + 1) { null }.also { r ->
                for (col in 0..maxCol) r[col] = map[row to col]
            }
        }
        return grid
    }

    private fun parseRef(ref: String): Pair<Int, Int>? {
        val m = Regex("^([A-Z]{1,4})(\\d{1,7})$").matchEntire(ref) ?: return null
        val colLong = m.groupValues[1].fold(0L) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        val row = m.groupValues[2].toIntOrNull()?.minus(1) ?: return null
        if (row !in 0 until MAX_ROWS || colLong !in 0 until MAX_COLUMNS) return null
        return row to colLong.toInt()
    }

    private fun firstText(element: Element, tag: String): String {
        val list = element.getElementsByTagNameNS("*", tag)
        return if (list.length > 0) list.item(0).textContent ?: "" else ""
    }

    private fun collectText(node: org.w3c.dom.Node, sb: StringBuilder): StringBuilder {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeName == "t") sb.append(child.textContent ?: "")
            else collectText(child, sb)
        }
        return sb
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val raw = bytes.toString(Charsets.ISO_8859_1)
        if (raw.contains("<!DOCTYPE", ignoreCase = true) || raw.contains("<!ENTITY", ignoreCase = true)) {
            throw ImportRejectedException("XLSX XML 包含禁止的文档类型或实体")
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isXIncludeAware = false }
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private data class ZipEntryData(val content: ByteArray?, val bytesRead: Long)

    private fun readZipEntry(zip: ZipInputStream, collect: Boolean, remainingTotal: Long): ZipEntryData {
        val output = if (collect) ByteArrayOutputStream() else null
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (collect && total > MAX_XML_BYTES) throw ImportRejectedException("XLSX 内部 XML 过大")
            if (total > remainingTotal) throw ImportRejectedException("XLSX 解压后数据过大")
            output?.write(buffer, 0, read)
        }
        return ZipEntryData(output?.toByteArray(), total)
    }

    private fun parseDay(text: String): Int? {
        val t = text.trim()
        t.toIntOrNull()?.let { return if (it in 1..7) it else null }
        DAY_RE.find(t)?.let { m ->
            DAY_NUM[m.value.last().toString()]?.let { return it }
        }
        DAY_NUM[t]?.let { return it }
        return null
    }

    private fun extractDay(text: String): Int? {
        DAY_RE.find(text.trim())?.let { m ->
            return DAY_NUM[m.value.last().toString()]
        }
        return null
    }

    internal fun parseWeekSegments(spec: String): List<Triple<Int, Int, Int>> {
        val result = mutableListOf<Triple<Int, Int, Int>>()
        for (token in spec.split(Regex("[、,，]"))) {
            var t = token.trim()
            if (t.isEmpty()) continue
            var type = WeekType.ALL.code
            if (t.endsWith("单")) {
                type = WeekType.ODD.code
                t = t.dropLast(1)
            } else if (t.endsWith("双")) {
                type = WeekType.EVEN.code
                t = t.dropLast(1)
            }
            t = t.trim().removeSuffix("周").trim()
            val range = Regex("(\\d{1,2})\\s*[-~～]\\s*(\\d{1,2})").find(t)
            if (range != null) {
                val s = range.groupValues[1].toIntOrNull()
                val e = range.groupValues[2].toIntOrNull()
                if (s != null && e != null && e >= s) {
                    result.add(Triple(s, e, type))
                }
                continue
            }
            val single = Regex("\\d{1,2}").find(t)?.value?.toIntOrNull()
            if (single != null) result.add(Triple(single, single, type))
        }
        return result
    }

    private fun cellAt(row: List<String>, idx: Int): String = row.getOrNull(idx) ?: ""

    private fun splitCsv(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var cellCount = 0
        var i = 0
        while (i < content.length) {
            val ch = content[i]
            when {
                inQuotes && ch == '"' -> {
                    if (i + 1 < content.length && content[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    row.add(field.toString())
                    cellCount++
                    if (row.size > MAX_COLUMNS) throw ImportRejectedException("CSV 列数过多")
                    if (cellCount > MAX_CELLS) throw ImportRejectedException("CSV 数据量过大")
                    field = StringBuilder()
                }
                ch == '\n' || ch == '\r' -> {
                    if (ch == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    row.add(field.toString())
                    cellCount++
                    if (row.size > MAX_COLUMNS) throw ImportRejectedException("CSV 列数过多")
                    if (cellCount > MAX_CELLS) throw ImportRejectedException("CSV 数据量过大")
                    field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    if (rows.size > MAX_ROWS) throw ImportRejectedException("CSV 数据量过大")
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            cellCount++
            if (row.size > MAX_COLUMNS || cellCount > MAX_CELLS) throw ImportRejectedException("CSV 数据量过大")
            if (row.any { it.isNotBlank() }) rows.add(row)
            if (rows.size > MAX_ROWS) throw ImportRejectedException("CSV 数据量过大")
        }
        return rows
    }
}
