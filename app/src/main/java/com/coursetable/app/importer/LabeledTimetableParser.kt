package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.withScheduledWeeks
import kotlin.math.abs

/** School PDFs contain wrapped slash-separated fields, not one course per text band. */
object LabeledTimetableParser {
    private val weekday = Regex("(?:星期|周)([一二三四五六日天1-7])")
    private val meeting = Regex("^[（(]?\\s*(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s*节[）)]?")
    private val practice = Regex("实践课程[:：](.*)")

    fun extract(tokens: List<TextToken>, width: Float, height: Float, settings: AppSettings, page: Int, inherited: List<TextToken>, knownTeachers: List<String> = emptyList()): PdfParseResult? {
        val headers = headers(tokens).ifEmpty { inherited.filter { weekday.matches(it.text.trim()) } }.sortedBy { it.cx }
        if (headers.size < 2) return null
        val content = tokens.joinToString("") { StructuredTimetableParser.normalize(it.text) }
        if (!content.contains("/教学班") && !content.contains("/教师") && !content.contains("/场地")) return null
        val footerTop = tokens.firstOrNull { practice.containsMatchIn(it.text) }?.y1
        val currentHeaders = headers(tokens)
        val bodyTop = if(currentHeaders.isEmpty()) height + 1 else currentHeaders.minOf { it.y0 }
        val candidates = mutableListOf<CandidateCourse>()
        val regions = mutableListOf<ImportRegion>()
        for ((column, header) in headers.withIndex()) {
            val left = headers.getOrNull(column - 1)?.let { (it.cx + header.cx) / 2 } ?: header.cx - (headers[1].cx - header.cx) / 2
            val right = headers.getOrNull(column + 1)?.let { (it.cx + header.cx) / 2 } ?: header.cx + (header.cx - headers[column - 1].cx) / 2
            val lines = lines(tokens.filter { it.cx > left && it.cx < right && it.y0 < bodyTop && (footerTop == null || it.cy > footerTop) })
            val starts = lines.indices.filter { meeting.containsMatchIn(StructuredTimetableParser.normalize(lines[it].text)) }
            for ((position, start) in starts.withIndex()) {
                val titleIndex = (start - 1).takeIf { it >= 0 && isTitle(lines[it].text) }
                val next = starts.getOrNull(position + 1)
                val end = next?.let { if(it > 0 && isTitle(lines[it - 1].text)) it - 1 else it } ?: lines.size
                val block = lines.subList(titleIndex ?: start, end)
                val raw = block.map { it.text }
                val title = titleIndex?.let { lines[it].text.trim() }.orEmpty()
                val joined = block.drop(if(titleIndex == null) 0 else 1).joinToString("") { it.text }.let(StructuredTimetableParser::normalize)
                val prefix = joined.substringBefore('/')
                val sections = meeting.find(prefix) ?: continue
                val a = sections.groupValues[1].toInt(); val b = sections.groupValues[2].toInt()
                val weekText = prefix.substring(sections.range.last + 1)
                val hasWeeks = Regex("\\d.*周").containsMatchIn(weekText)
                val ranges = if(hasWeeks) StructuredTimetableParser.weeks(weekText, 60) else emptyList()
                val weeks = ranges.flatMap { (first, last, parity) -> (first..last).filter { parity == 0 || it % 2 == parity % 2 } }.distinct().sorted()
                val teacher = Regex("(?:^|/)教师[:：]([^/]*)").find(joined)?.groupValues?.get(1).orEmpty()
                val location = Regex("(?:^|/)(?:场地|教室|地点)[:：]([^/]*)").find(joined)?.groupValues?.get(1).orEmpty()
                val id = "$page-labeled-$column-$position"
                val top = block.maxOf { it.y1 }; val bottom = block.minOf { it.y0 }
                regions += ImportRegion(id, page, (left / width).coerceIn(0f,1f), ((height-top)/height).coerceIn(0f,1f), (right/width).coerceIn(0f,1f), ((height-bottom)/height).coerceIn(0f,1f), TimetableLayout.WEEKLY)
                val label = weekday.matchEntire(header.text.trim())!!.groupValues[1]
                val day = label.toIntOrNull() ?: ("一二三四五六日".indexOf(label).takeIf { it >= 0 }?.plus(1) ?: 7)
                val initial = CandidateCourse(title, location, teacher, day, a, b-a+1, weeks.firstOrNull() ?: 0, weeks.lastOrNull() ?: 0, 0,
                    rawLines = raw, sourceRegion = id, needsReview = block.any { it.tokens.any { t -> t.needsReview } } || title.isBlank() || weeks.isEmpty(),
                    issues = if(weeks.isEmpty()) listOf("周次无法解析，请选择实际上课周次") else emptyList(), alternatives = block.flatMap { it.tokens.flatMap { t -> t.alternatives } }.distinct())
                val schedule = if(weeks.isNotEmpty()) initial.asCourse().withScheduledWeeks(weeks) else initial.asCourse()
                candidates += initial.copy(startWeek = schedule.startWeek, endWeek = schedule.endWeek, weekType = schedule.weekType, selectedWeeksCsv = schedule.selectedWeeksCsv)
            }
        }
        if(candidates.isEmpty()) return null
        extractPractices(tokens, height, settings, page, candidates, regions, knownTeachers)
        return PdfParseResult(candidates, emptyList(), regions)
    }

    fun headers(tokens: List<TextToken>): List<TextToken> {
        val words = tokens.filter { weekday.matches(it.text.trim()) }
        return words.map { center -> words.filter { abs(it.cy - center.cy) <= center.height * 1.5 }.distinctBy { it.text } }
            .maxByOrNull { it.size }.orEmpty()
    }

    private data class Line(val text: String, val tokens: List<TextToken>) {
        val y1 get() = tokens.maxOf { it.y1 }
        val y0 get() = tokens.minOf { it.y0 }
    }
    private fun lines(tokens: List<TextToken>): List<Line> {
        val bands = mutableListOf<MutableList<TextToken>>()
        for(t in tokens.sortedByDescending { it.cy }) {
            val band = bands.lastOrNull()
            if(band != null && abs(band.first().cy-t.cy) < maxOf(band.first().height,t.height)*.7f) band += t else bands += mutableListOf(t)
        }
        return bands.map { group -> Line(group.sortedBy { it.x0 }.joinToString("") { it.text }, group) }
    }
    private fun isTitle(text: String): Boolean = text.isNotBlank() && !text.any { it in "/;；" } &&
        !Regex("学分|教学班|课程性质|课备注|^\\d+$|^[（(]?\\d.*节").containsMatchIn(text)

    private fun extractPractices(tokens: List<TextToken>, height: Float, settings: AppSettings, page: Int, out: MutableList<CandidateCourse>, regions: MutableList<ImportRegion>, knownTeachers: List<String>) {
        val start = tokens.firstOrNull { practice.containsMatchIn(it.text) } ?: return
        val footer = tokens.filter { abs(it.cy-start.cy) <= start.height }.sortedBy { it.x0 }
        val text = StructuredTimetableParser.normalize(footer.joinToString("") { it.text }).substringAfter("实践课程:")
        val teachers = (knownTeachers + out.map { it.teacher }).filter { it.isNotBlank() }.flatMap { listOf(it) + it.split(Regex("[,，、]")) }.distinct().sortedByDescending { it.length }
        text.split(Regex("[;；]")).filter { it.isNotBlank() }.forEachIndexed { index, item ->
            val match = Regex("^(.*?)[(（]共\\d+周[)）]/(.*)$").find(item.trim()) ?: return@forEachIndexed
            val titleAndTeacher = match.groupValues[1]
            var name = titleAndTeacher
            val teacherParts = mutableListOf<String>()
            while (true) {
                val tail = teachers.firstOrNull { name.endsWith(it) } ?: break
                teacherParts.add(0, tail)
                name = name.removeSuffix(tail).trimEnd(',', '，', '、', ' ')
            }
            val teacher = teacherParts.joinToString(",")
            name = name.trim()
            val ranges = StructuredTimetableParser.weeks(match.groupValues[2], 60)
            val weeks = ranges.flatMap { (a,b,_) -> (a..b).toList() }.distinct().sorted()
            val id = "$page-practice-$index"
            regions += ImportRegion(id, page, 0f, ((height-footer.maxOf { it.y1 })/height).coerceIn(0f,1f), 1f, ((height-footer.minOf { it.y0 })/height).coerceIn(0f,1f), TimetableLayout.UNKNOWN)
            out += CandidateCourse(name, teacher = teacher, dayOfWeek = 0, startSection = 0, duration = 0, startWeek = weeks.firstOrNull() ?: 0, endWeek = weeks.lastOrNull() ?: 0, weekType = 0,
                selectedWeeksCsv = weeks.joinToString(","), rawLines = listOf(item), sourceRegion = id, needsReview = true,
                issues = listOf("实践课未提供星期和节次，请补齐或排除") + if(weeks.any { it > settings.totalWeeks }) listOf("实践课周次超过当前学期，请核对学期总周数") else emptyList())
        }
    }
}
