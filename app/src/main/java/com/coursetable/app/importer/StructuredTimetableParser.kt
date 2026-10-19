package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.withScheduledWeeks
import java.time.LocalTime
import kotlin.math.abs

enum class TimetableLayout { DETAILS, WEEKLY, UNKNOWN }
data class ImportRegion(val id: String, val page: Int, val left: Float, val top: Float, val right: Float, val bottom: Float, val layout: TimetableLayout)

/** Coordinates use the same bottom-up space as PDF tokens. Output regions are top-down, normalized. */
object StructuredTimetableParser {
    private val day = Regex("(?:星[期斯]|周)\\s*([一二三四五六日天1-7])")
    private val section = Regex("第?\\s*(\\d{1,2})(?:\\s*-\\s*(\\d{1,2}))?\\s*节")
    private val clock = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")
    fun normalize(s: String) = s.map { if (it in '！'..'～') (it.code - 65248).toChar() else it }.joinToString("")
        .replace(Regex("[—–−~～至]"), "-").replace("星期 ", "星期").replace("周 ", "周")
    private fun dayNumber(s: String): Int? = day.find(normalize(s))?.groupValues?.get(1)?.let {
        it.toIntOrNull() ?: ("一二三四五六日".indexOf(it).takeIf { i -> i >= 0 }?.plus(1) ?: if (it == "天") 7 else null)
    }
    fun weeks(text: String, total: Int): List<Triple<Int, Int, Int>> {
        val s = normalize(text)
        val specifications = Regex("([\\d\\s,、.-]+)周").findAll(s).map { it.groupValues[1] }.toList()
        if (specifications.isEmpty()) return listOf(Triple(1, total, 0))
        val type = when { Regex("单周|周\\s*[（(]\\s*单\\s*[）)]").containsMatchIn(s) -> 1; Regex("双周|周\\s*[（(]\\s*双\\s*[）)]").containsMatchIn(s) -> 2; else -> 0 }
        return specifications.flatMap { it.split(Regex("[,、.]")) }.mapNotNull { part ->
            val m = Regex("^\\s*(\\d{1,2})(?:\\s*-\\s*(\\d{1,2}))?\\s*$").matchEntire(part) ?: return@mapNotNull null
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toIntOrNull() ?: a
            if (a < 1 || b < a || a > total) null else Triple(a, b.coerceAtMost(total), type)
        }.distinct()
    }
    private fun sections(text: String, settings: AppSettings): Pair<Int, Int>? {
        section.find(normalize(text))?.let {
            val a = it.groupValues[1].toInt(); val b = it.groupValues[2].toIntOrNull() ?: a
            return if (a > 0 && b >= a && b <= settings.periods.size) a to b - a + 1 else null
        }
        val normalized = normalize(text)
        day.find(normalized)?.let { d ->
            val tail = normalized.substring(d.range.last + 1).trim()
            Regex("^(\\d{1,2})\\s*-\\s*(\\d{1,2})(?:节|\\s|$)").find(tail)?.let { m ->
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                return if (a > 0 && b >= a && b <= settings.periods.size) a to b - a + 1 else null
            }
        }
        val m = clock.find(normalized) ?: return null
        val values = m.groupValues.drop(1).map { it.toInt() }
        val start = runCatching { LocalTime.of(values[0], values[1]) }.getOrNull() ?: return null
        val end = runCatching { LocalTime.of(values[2], values[3]) }.getOrNull() ?: return null
        val a = settings.periods.indexOfFirst { abs(it.start.toSecondOfDay() - start.toSecondOfDay()) <= 300 }
        val b = settings.periods.indexOfFirst { abs(it.end.toSecondOfDay() - end.toSecondOfDay()) <= 300 }
        return if (a >= 0 && b >= a) a + 1 to b - a + 1 else null
    }
    private fun lines(tokens: List<TextToken>): List<String> {
        val bands = mutableListOf<MutableList<TextToken>>()
        for (t in tokens.sortedByDescending { it.cy }) {
            val band = bands.lastOrNull()
            if (band != null && abs(band.map { it.cy }.average() - t.cy) < t.height * .65) band.add(t)
            else bands.add(mutableListOf(t))
        }
        return bands.map { b -> b.sortedBy { it.x0 }.joinToString(" ") { it.text } }
    }
    fun extract(tokens: List<TextToken>, width: Float, height: Float, settings: AppSettings, page: Int = 0, inherited: List<TextToken> = emptyList(), rowEdges: List<Float> = emptyList(), visualCells: List<ImportRegion> = emptyList(), knownTeachers: List<String> = emptyList()): PdfParseResult {
        val clean = tokens.filter { it.text.isNotBlank() }
        val headers = clean.filter { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) }
        val nameHeader = headers.firstOrNull() ?: inherited.firstOrNull { TimetableHeaders.matches(it.text, TimetableHeaders.Field.NAME) }
        val headerSource = if (headers.isEmpty()) inherited else clean
        val timeHeader = nameHeader?.let { name -> headerSource.firstOrNull { it != name && abs(it.cy - name.cy) < name.height * 1.5 && TimetableHeaders.matches(it.text, TimetableHeaders.Field.TIME) } }
        val hasWeekdays = clean.any { day.matches(normalize(it.text).trim()) }
        val continuedHeaders = if (!hasWeekdays) inherited.filter { day.matches(normalize(it.text).trim()) }.map { it.copy(y0 = height + 5, y1 = height + 20) } else emptyList()
        val weeklyTokens = clean + continuedHeaders
        val routes = mutableListOf<Pair<TimetableRoute, () -> PdfParseResult>>()
        LabeledTimetableParser.extract(tokens, width, height, settings, page, inherited, knownTeachers)?.let { labeled ->
            routes += TimetableRoute.LABELED to { labeled }
        }
        if (nameHeader != null && timeHeader != null) routes += TimetableRoute.DETAILS to {
            val tableHeaders = headers.filter { kotlin.math.abs(it.cx - nameHeader.cx) < nameHeader.height * 3 }
                .filter { n -> clean.any { abs(it.cy - n.cy) < n.height * 1.5 && TimetableHeaders.matches(it.text, TimetableHeaders.Field.TIME) } }
                .sortedByDescending { it.cy }
            if (tableHeaders.size <= 1) details(clean, nameHeader, timeHeader, headerSource, width, height, settings, page, rowEdges, headers.isEmpty())
            else {
                // Repeated table headers delimit screenshots/pages stitched into one image.
                val topMargin = height - tableHeaders.first().y1
                val pieces = tableHeaders.mapIndexed { index, n ->
                    val lower = tableHeaders.getOrNull(index + 1)?.let { it.y1 + topMargin } ?: 0f
                    val upper = if (index == 0) height else n.y1 + topMargin
                    val slice = clean.filter { it.cy in lower..upper }
                    val t = slice.first { abs(it.cy - n.cy) < n.height * 1.5 && TimetableHeaders.matches(it.text, TimetableHeaders.Field.TIME) }
                    details(slice, n, t, slice, width, height, settings, page, rowEdges.filter { it in lower..upper } + lower, false, "$page-details-table$index")
                }
                PdfParseResult(pieces.flatMap { it.candidates }, pieces.flatMap { it.warnings }.distinct(), pieces.flatMap { it.regions })
            }
        }
        routes += TimetableRoute.WEEKLY to { weekly(weeklyTokens, width, height, settings, page, visualCells) }
        if (visualCells.isNotEmpty()) routes += TimetableRoute.COORDINATE to { weekly(weeklyTokens, width, height, settings, page, emptyList()) }
        val expected = clean.count { Regex("\\d.*周").containsMatchIn(normalize(it.text)) }
        val attempts = mutableListOf<RouteAttempt>()
        var selected: PdfParseResult? = null
        var best: RouteAttempt? = null
        for ((route, parse) in routes) {
            val result = enrich(parse(), clean, width, height)
            val assessment = TimetableConfidence.assess(route, result, settings, expected)
            attempts += assessment
            val current = best
            // A retry must improve confidence without discarding already valid meetings.
            if (current == null || assessment.confidence > current.confidence + .05f && assessment.valid >= current.valid) {
                selected = result; best = assessment
            }
            if (best!!.confidence >= TimetableConfidence.RETRY_THRESHOLD) break
        }
        val result = selected!!
        val warnings = result.warnings + if (best!!.confidence < TimetableConfidence.RETRY_THRESHOLD) listOf("课表结构或字段识别存疑，请逐项校对；也可使用粘贴导入") else emptyList()
        return result.copy(warnings = warnings.distinct(), routing = TimetableRouting(best!!.route, attempts))
    }
    private fun enrich(result: PdfParseResult, clean: List<TextToken>, width: Float, height: Float): PdfParseResult {
        return result.copy(candidates = result.candidates.map { candidate ->
            val region = result.regions.firstOrNull { it.id == candidate.sourceRegion }
            val local = if (region == null) emptyList() else clean.filter { it.cx / width in region.left..region.right && (height - it.cy) / height in region.top..region.bottom }
            val missingWeeks = candidate.rawLines.none { Regex("\\d.*周").containsMatchIn(normalize(it)) }
            val raw = normalize(candidate.rawLines.joinToString(" "))
            val claimedDay = dayNumber(raw)
            val contradiction = region?.layout == TimetableLayout.WEEKLY && claimedDay != null && claimedDay != candidate.dayOfWeek
            val alternatives = local.flatMap { it.alternatives }.distinct()
            val fields = candidate.pendingReviewFields() + alternatives.pendingReviewFields() +
                (if (missingWeeks) setOf(ReviewField.WEEKS) else emptySet()) +
                (if (contradiction) setOf(ReviewField.DAY_OF_WEEK) else emptySet())
            candidate.copy(startWeek = if(missingWeeks) 0 else candidate.startWeek, endWeek = if(missingWeeks) 0 else candidate.endWeek,
                reviewFields = fields,
                needsReview = candidate.needsReview || missingWeeks || contradiction || alternatives.isNotEmpty(), alternatives = (candidate.alternatives + alternatives).distinct(),
                issues = (candidate.issues + alternatives.map { it.reason } + (if(missingWeeks) listOf("周次缺失，请选择实际上课周次") else emptyList<String>()) + (if(contradiction) listOf("星期文字与表格位置不一致") else emptyList<String>())).distinct())
        })
    }
    private fun details(tokens: List<TextToken>, nameH: TextToken, timeH: TextToken, headers: List<TextToken>, w: Float, h: Float, settings: AppSettings, page: Int, rowEdges: List<Float>, continuation: Boolean, prefix: String = "$page-details"): PdfParseResult {
        val headerY = nameH.cy
        val headerRow = headers.filter { abs(it.cy - headerY) < nameH.height * 1.5 }.sortedBy { it.cx }
        fun column(header: TextToken): Pair<Float, Float> {
            val i = headerRow.indexOf(header)
            return (headerRow.getOrNull(i - 1)?.let { (it.cx + header.cx) / 2 } ?: 0f) to
                (headerRow.getOrNull(i + 1)?.let { (it.cx + header.cx) / 2 } ?: w)
        }
        val nr = column(nameH); val tr = column(timeH)
        val teacherH = headerRow.firstOrNull { TimetableHeaders.matches(it.text, TimetableHeaders.Field.TEACHER) }
        val teacherR = teacherH?.let { column(it) }
        val locationR = headerRow.firstOrNull { TimetableHeaders.matches(it.text, TimetableHeaders.Field.LOCATION) }?.let { column(it) }
        val bodyTop = if (continuation) h else headerY - nameH.height
        val body = tokens.filter { it.cy < bodyTop }
        val nameTokens = body.filter { it.cx in nr.first..nr.second }
        // Independent name blocks tolerate wrapped names and rows containing several meetings.
        val groups = mutableListOf<MutableList<TextToken>>()
        for (t in nameTokens.sortedByDescending { it.cy }) {
            val last = groups.lastOrNull()
            if (last != null && last.last().cy - t.cy < t.height * 2.0) last.add(t) else groups.add(mutableListOf(t))
        }
        val spans = rowEdges.filter { it < bodyTop }.sortedDescending().zipWithNext().filter { (upper, lower) ->
            upper - lower > nameH.height * 1.5 && body.any { it.cy in lower..upper && it.cx in tr.first..tr.second }
        }
        if (spans.isNotEmpty()) {
            groups.clear()
            spans.forEach { (upper, lower) -> groups.add(nameTokens.filter { it.cy in lower..upper }.toMutableList()) }
        }
        val courses = mutableListOf<CandidateCourse>(); val regions = mutableListOf<ImportRegion>(); val warnings = mutableListOf<String>()
        groups.forEachIndexed { index, group ->
            val upper = spans.getOrNull(index)?.first ?: rowEdges.filter { it > group.maxOf { t -> t.y1 } && it < headerY }.minOrNull()
                ?: groups.getOrNull(index - 1)?.let { (it.minOf { t -> t.y0 } + group.maxOf { t -> t.y1 }) / 2 } ?: bodyTop
            val lower = spans.getOrNull(index)?.second ?: rowEdges.filter { it < group.minOf { t -> t.y0 } }.maxOrNull()
                ?: groups.getOrNull(index + 1)?.let { (group.minOf { t -> t.y0 } + it.maxOf { t -> t.y1 }) / 2 } ?: 0f
            // Prefer row metadata centers (course codes/credits) where available, and keep all time lines in the row.
            val row = body.filter { it.cy in lower..upper }
            val id = "$prefix-$index"
            regions.add(ImportRegion(id, page, 0f, (h - upper) / h, 1f, (h - lower) / h, TimetableLayout.DETAILS))
            val name = lines(group).joinToString("").let { OcrDecisions.normalize(it) }.trim('|')
            val teacher = teacherR?.let { r -> lines(row.filter { it.cx in r.first..r.second }).joinToString("、") } ?: ""
            val timeLines = lines(row.filter { it.cx in tr.first..tr.second })
            val rowFields = buildSet {
                if (name.isBlank() || group.any { it.needsReview }) add(ReviewField.NAME)
                if (teacherR != null && row.any { it.cx in teacherR.first..teacherR.second && it.needsReview }) add(ReviewField.TEACHER)
                if (locationR != null && row.any { it.cx in locationR.first..locationR.second && it.needsReview }) add(ReviewField.LOCATION)
                if (row.any { it.cx in tr.first..tr.second && it.needsReview }) add(ReviewField.TIME)
                if (isEmpty() && row.any { it.needsReview }) add(ReviewField.GENERAL)
            }
            val starts = timeLines.indices.filter { day.containsMatchIn(normalize(timeLines[it])) }
            if (starts.isEmpty()) courses.add(CandidateCourse(name, teacher = teacher, dayOfWeek = 0, startSection = 0, duration = 1, startWeek = 1, endWeek = settings.totalWeeks, weekType = 0, sourceRegion = id, needsReview = true, reviewFields = rowFields + ReviewField.TIME))
            starts.forEachIndexed { i, start ->
                val meeting = timeLines.subList(start, starts.getOrNull(i + 1) ?: timeLines.size)
                val text = normalize(meeting.joinToString(" "))
                val sec = sections(text, settings)
                val timeEnd = section.find(normalize(meeting.first()))?.range?.last ?: clock.find(normalize(meeting.first()))?.range?.last
                val inlineLocation = timeEnd?.let { normalize(meeting.first()).substring(it + 1).trim() }.orEmpty()
                val separateLocation = locationR?.let { r -> lines(row.filter { it.cx in r.first..r.second }).joinToString(" ") }.orEmpty()
                val location = separateLocation.ifBlank { (listOf(inlineLocation).filter { it.isNotBlank() } + meeting.drop(1)).joinToString(" ") }
                if (!Regex("\\d.*周").containsMatchIn(text)) warnings.add("$name 未标注周次，请校对")
                val ranges = weeks(text, settings.totalWeeks)
                val fields = rowFields + (if (sec == null) setOf(ReviewField.SECTIONS) else emptySet()) +
                    (if (ranges.isEmpty()) setOf(ReviewField.WEEKS) else emptySet()) +
                    (if (text.contains("星斯")) setOf(ReviewField.DAY_OF_WEEK) else emptySet())
                val base = CandidateCourse(name, location, teacher, dayNumber(text) ?: 0, sec?.first ?: 0, sec?.second ?: 1, 0, 0, 0, meeting, id, fields.isNotEmpty(), reviewFields = fields)
                courses.add(withWeekRanges(base, ranges))
            }
        }
        return PdfParseResult(courses, warnings.distinct(), regions)
    }
    private fun weekly(tokens: List<TextToken>, w: Float, h: Float, settings: AppSettings, page: Int, visualCells: List<ImportRegion>): PdfParseResult {
        val labels = tokens.filter { day.matches(normalize(it.text).trim()) }
        val bands = labels.map { center -> labels.filter { abs(it.cy - center.cy) < center.height * 1.5 } }
        val header = bands.filter { it.mapNotNull { t -> dayNumber(t.text) }.distinct().size >= 2 }.maxByOrNull { it.size }
            ?: return PdfParseResult(emptyList(), listOf("表格结构不明确，请补框并校对"))
        val cols = header.sortedBy { it.cx }
        val left = cols.first().cx - (cols[1].cx - cols[0].cx) / 2
        val anchors = tokens.filter { it.cx < left && it.cy < header.minOf { t -> t.y0 } }.mapNotNull { t ->
            val n = section.find(normalize(t.text))?.groupValues?.get(1)?.toIntOrNull() ?: normalize(t.text).trim().toIntOrNull()
            val sec = n ?: sections(t.text, settings)?.first
            sec?.let { t to it }
        }.sortedByDescending { it.first.cy }.distinctBy { it.second }
        if (anchors.isEmpty()) return PdfParseResult(emptyList(), listOf("无法确定节次，请补框并校对"))
        val courses = mutableListOf<CandidateCourse>(); val regions = mutableListOf<ImportRegion>()
        val used = mutableSetOf<TextToken>()
        for ((i, cell) in visualCells.withIndex()) {
            val x = (cell.left + cell.right) * w / 2
            val col = cols.minByOrNull { abs(it.cx - x) } ?: continue
            if (abs(col.cx - x) > (cols[1].cx - cols[0].cx) / 2) continue
            val top = (1 - cell.top) * h; val bottom = (1 - cell.bottom) * h
            if (top >= header.minOf { it.y0 }) continue
            val inside = tokens.filter { it !in used && it.cx / w in cell.left..cell.right && (h - it.cy) / h in cell.top..cell.bottom }
            if (inside.isEmpty()) continue
            val covered = anchors.filter { it.first.cy in bottom..top }
            if (covered.isEmpty()) continue
            val id = "$page-block-$i"; val ls = lines(inside)
            val parsedRecords = PdfCellParser.parseMany(ls, dayNumber(col.text)!!, covered.first().second)
            for (parsed in parsedRecords) {
                val content = parsed.rawLines.joinToString(" ")
                val duration = if (section.containsMatchIn(normalize(content))) parsed.duration else covered.last().second - covered.first().second + 1
                val ambiguousSpan = parsedRecords.size > 1 && covered.size > 1 && !section.containsMatchIn(normalize(content))
                courses.add(withWeekRanges(parsed.copy(duration = duration, sourceRegion = id,
                    needsReview = parsed.needsReview || inside.any { it.needsReview } || ambiguousSpan,
                    issues = parsed.issues + if (ambiguousSpan) listOf("同一色块含多个上课记录，节次跨度需确认") else emptyList()), weeks(content, settings.totalWeeks)))
            }
            regions.add(cell.copy(id = id, page = page, layout = TimetableLayout.WEEKLY)); used.addAll(inside)
        }
        cols.forEachIndexed { ci, col ->
            val x0 = if (ci == 0) left else (cols[ci - 1].cx + col.cx) / 2
            val x1 = cols.getOrNull(ci + 1)?.let { (it.cx + col.cx) / 2 } ?: (col.cx + (col.cx - cols[ci - 1].cx) / 2).coerceAtMost(w)
            anchors.forEachIndexed { ai, anchor ->
                val y1 = anchors.getOrNull(ai - 1)?.let { (it.first.cy + anchor.first.cy) / 2 } ?: header.minOf { it.y0 }
                val y0 = anchors.getOrNull(ai + 1)?.let { (it.first.cy + anchor.first.cy) / 2 } ?: (anchor.first.cy - (y1 - anchor.first.cy)).coerceAtLeast(0f)
                val cell = tokens.filter { it !in used && it.cx in x0..x1 && it.cy > y0 && it.cy <= y1 }
                if (cell.isEmpty()) return@forEachIndexed
                val id = "$page-weekly-$ci-$ai"
                val ls = lines(cell)
                val parsedRecords = PdfCellParser.parseMany(ls, dayNumber(col.text)!!, anchor.second)
                val labelDuration = sections(anchor.first.text, settings)?.second ?: 1
                // Without a visual block/row edge, a text band cannot prove where a spanning course ends.
                for (parsed in parsedRecords) {
                    val content = parsed.rawLines.joinToString(" ")
                    courses.add(withWeekRanges(parsed.copy(duration = if (section.containsMatchIn(normalize(content))) parsed.duration else labelDuration, sourceRegion = id, needsReview = parsed.needsReview || cell.any { it.needsReview } || !section.containsMatchIn(normalize(content)) && labelDuration == 1), weeks(content, settings.totalWeeks)))
                }
                regions.add(ImportRegion(id, page, x0 / w, (h - y1) / h, x1 / w, (h - y0) / h, TimetableLayout.WEEKLY))
            }
        }
        return PdfParseResult(courses, if (courses.any { c -> c.rawLines.none { Regex("\\d.*周").containsMatchIn(it) } }) listOf("未标注周次的课程按当前学期处理，请校对") else emptyList(), regions)
    }
    private fun withWeekRanges(candidate: CandidateCourse, ranges: List<Triple<Int, Int, Int>>): CandidateCourse {
        val weeks = ranges.flatMap { (a, b, type) -> (a..b).filter { type == 0 || it % 2 == type % 2 } }.distinct().sorted()
        if (weeks.isEmpty()) return candidate.copy(startWeek = 0, endWeek = 0, needsReview = true)
        val schedule = candidate.asCourse().withScheduledWeeks(weeks)
        return candidate.copy(startWeek = schedule.startWeek, endWeek = schedule.endWeek, weekType = schedule.weekType, selectedWeeksCsv = schedule.selectedWeeksCsv)
    }
}
