package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.scheduledWeeks
import com.coursetable.app.data.withScheduledWeeks
import org.json.JSONArray
import org.json.JSONObject

/** A course-list interchange format, deliberately independent from database backups. */
object PasteTimetableImporter {
    const val MAX_CHARACTERS = 512_000
    const val SHARE_PREFIX = "CourseTable课表分享 v1\n"

    fun parse(input: String, settings: AppSettings): PdfParseResult {
        if (input.length > MAX_CHARACTERS) throw ImportRejectedException("粘贴内容过长，请分批导入")
        // Keep leading/trailing tabs: the first weekday header often follows an empty section cell.
        var text = input.replace("\r\n", "\n").replace('\r', '\n').trim(' ', '\n', '\uFEFF')
        if (text.isBlank()) throw ImportRejectedException("请先粘贴课表内容")
        if (text.startsWith("CourseTable课表分享")) {
            if (!text.startsWith(SHARE_PREFIX)) throw ImportRejectedException("暂不支持此课表分享版本")
            text = text.removePrefix(SHARE_PREFIX).trim()
        }
        if (text.startsWith("```")) {
            text = Regex("^```(?:json)?\\s*\\n([\\s\\S]*?)\\n```$", RegexOption.IGNORE_CASE)
                .matchEntire(text)?.groupValues?.get(1)?.trim()
                ?: throw ImportRejectedException("请粘贴完整的 JSON 代码块")
        }
        val json = text.trim()
        val result = if (json.startsWith("{") || json.startsWith("[")) parseJson(json, settings) else parseText(text, settings)
        if (result.candidates.size > ImportPolicy.MAX_OUTPUT_COURSES) throw ImportRejectedException("课程数量超过 ${ImportPolicy.MAX_OUTPUT_COURSES} 条，请分批导入")
        if (result.candidates.any { listOf(it.name, it.teacher, it.location).any { field -> field.length > ImportPolicy.MAX_TEXT_FIELD } }) throw ImportRejectedException("课程文字字段过长，请缩短后重试")
        return result
    }

    private fun parseJson(text: String, settings: AppSettings): PdfParseResult {
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; if (depth > 32) throw ImportRejectedException("JSON 层级过深") }
                '}', ']' -> depth--
            }
        }
        val array = try {
            if (text.startsWith("[")) JSONArray(text) else {
                val root = JSONObject(text)
                if (root.has("timetables")) throw ImportRejectedException("这是整库备份，请使用「从备份恢复」")
                if (root.has("format") && root.optString("format") != "coursetable-share") throw ImportRejectedException("不支持此课程列表格式")
                if (root.has("version") && root.optInt("version", -1) != 1) throw ImportRejectedException("暂不支持此课程列表版本")
                root.optJSONArray("courses") ?: throw ImportRejectedException("JSON 需要包含 courses 课程数组")
            }
        } catch (e: ImportRejectedException) { throw e }
        catch (_: Exception) { throw ImportRejectedException("JSON 不完整或格式有误，请检查后重试") }
        if (array.length() > ImportPolicy.MAX_OUTPUT_COURSES) throw ImportRejectedException("课程数量超过 ${ImportPolicy.MAX_OUTPUT_COURSES} 条")
        if (array.length() == 0) throw ImportRejectedException("课程列表为空")
        val candidates = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: throw ImportRejectedException("第 ${index + 1} 条课程必须是 JSON 对象")
            fun string(key: String): String {
                val value = if (item.isNull(key)) "" else item.optString(key).trim()
                if (value.length > ImportPolicy.MAX_TEXT_FIELD) throw ImportRejectedException("第 ${index + 1} 条课程的 $key 过长")
                return value
            }
            fun number(key: String): Int = item.opt(key)?.toString()?.toIntOrNull() ?: 0
            if (number("startWeek") !in 0..60 || number("endWeek") !in 0..60) throw ImportRejectedException("第 ${index + 1} 条课程的周次范围应在 1–60 内")
            val original = CandidateCourse(string("name"), string("location"), string("teacher"),
                number("dayOfWeek"), number("startSection"), number("duration"), number("startWeek"), number("endWeek"), number("weekType"),
                rawLines = listOf("粘贴课程 ${index + 1}"),
                groupKey = string("group").takeIf { it.isNotBlank() }?.let { "paste-$it" })
            val weeksValue = item.opt("weeks")
            val selected = item.opt("selectedWeeksCsv")
            val candidate = if (weeksValue != null && weeksValue != JSONObject.NULL || selected != null && selected != JSONObject.NULL) {
                val pieces = if (weeksValue is JSONArray) (0 until weeksValue.length()).map { weeksValue.opt(it).toString() }
                    else if (weeksValue == null || weeksValue == JSONObject.NULL) selected?.toString().orEmpty().split(',')
                    else throw ImportRejectedException("第 ${index + 1} 条课程的 weeks 应为整数数组")
                val weeks = pieces.map { it.trim().toIntOrNull() }
                if (weeks.isEmpty() || weeks.any { it == null || it !in 1..60 }) throw ImportRejectedException("第 ${index + 1} 条课程的周次应为 1–60 的整数")
                val schedule = original.asCourse().withScheduledWeeks(weeks.filterNotNull())
                original.copy(startWeek = schedule.startWeek, endWeek = schedule.endWeek, weekType = schedule.weekType, selectedWeeksCsv = schedule.selectedWeeksCsv)
            } else original
            val issues = candidate.fieldErrors(settings)
            candidate.copy(needsReview = issues.isNotEmpty(), issues = issues)
        }
        return PdfParseResult(candidates, listOf("请核对星期、节次和周次；缺失的时间字段不会自动补为整学期"))
    }

    private fun parseText(text: String, settings: AppSettings): PdfParseResult {
        // Browser table copies preserve tabs/newlines. Preserve those cells rather than guessing columns from spaces.
        val rows = text.lines().filter { it.isNotBlank() }
        if (rows.size > 10_000) throw ImportRejectedException("文本行数过多，请分批导入")
        val split = rows.map { it.split('\t') }
        if (split.any { it.size > 128 }) throw ImportRejectedException("文本列数过多")
        val width = maxOf(1, split.maxOfOrNull { it.size } ?: 1) * 240f
        val height = (rows.size + 2) * 32f
        val tokens = split.flatMapIndexed { row, cells -> cells.mapIndexedNotNull { column, cell ->
            cell.trim().takeIf { it.isNotBlank() }?.let { TextToken(column * 240f + 8f, height - (row + 1) * 32f,
                column * 240f + 232f, height - (row + 1) * 32f + 18f, it) }
        } }
        val structured = StructuredTimetableParser.extract(tokens, width, height, settings,
            rowEdges = rows.indices.map { height - (it + 1) * 32f - 10f })
        if (structured.candidates.isNotEmpty()) return structured
        // Also accept the documented CSV column format when pasted directly.
        val csvText = if ('\t' in text) split.joinToString("\n") { cells -> cells.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } } else text
        val csv = ExcelTimetableImporter.parseCsv(csvText, strictLimits = true)
        if (csv.candidates.isNotEmpty()) return csv
        throw ImportRejectedException("未识别到课程。请复制包含表头的完整教务表格，或使用 AI 提示词生成 JSON")
    }

    fun share(courses: List<Course>, title: String): String {
        if (courses.isEmpty()) throw ImportRejectedException("当前课表没有可分享的课程")
        if (courses.size > ImportPolicy.MAX_OUTPUT_COURSES) throw ImportRejectedException("课程数量过多")
        val array = JSONArray()
        val groups = mutableMapOf<Long, String>()
        courses.forEachIndexed { index, course ->
            array.put(JSONObject().put("name", course.name).put("teacher", course.teacher).put("location", course.location)
                .put("dayOfWeek", course.dayOfWeek).put("startSection", course.startSection).put("duration", course.duration)
                .put("weeks", JSONArray(course.scheduledWeeks()))
                .put("group", if (course.courseId > 0) groups.getOrPut(course.courseId) { "course-${groups.size + 1}" } else "row-$index"))
        }
        return (SHARE_PREFIX + JSONObject().put("format", "coursetable-share").put("version", 1)
            .put("title", title).put("courses", array).toString(2)).also {
            if (it.length > MAX_CHARACTERS) throw ImportRejectedException("课表分享内容过长，请改用文件导出")
        }
    }

    fun aiPrompt(settings: AppSettings): String = """
        请读取我提供的课表图片或文本，只输出可直接粘贴导入 CourseTable 的 JSON，不要解释或 Markdown。
        格式：{"courses":[{"name":"课程名","teacher":"教师","location":"地点","dayOfWeek":1,"startSection":1,"duration":2,"weeks":[1,2,4,9,10]}]}
        dayOfWeek：周一=1，周日=7；startSection 是开始节次，duration 是连续节数，不是结束节次。
        weeks 是实际上课周次的整数数组，展开散周、范围、单双周；当前课表有 ${settings.totalWeeks} 周、${settings.periods.size} 节。
        同一课程不同星期、节次或地点拆成多条；同一时段的散周合为一条。保留课程名中的罗马数字等特殊字符。
        无法确定的课程名、教师、地点填空字符串；无法确定的星期、节次、节数填 0；无法确定周次时省略 weeks 并将 startWeek、endWeek 填 0。不得猜测缺失信息。
        节次时间：${settings.periods.mapIndexed { i, period -> "${i + 1}节 ${period.label()}" }.joinToString("；")}。
    """.trimIndent()
}
