package com.coursetable.app.importer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class IcsEvent(
    val summary: String = "",
    val location: String = "",
    val description: String = "",
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val rrule: String? = null,
    val exdates: List<LocalDate> = emptyList(),
    val isAllDay: Boolean = false
)

data class Rrule(
    val interval: Int = 1,
    val byDays: List<Int> = emptyList(),
    val until: LocalDate? = null,
    val count: Int? = null,
    val parsed: Boolean = false
)

data class IcsParseResult(val events: List<IcsEvent>, val warnings: List<String>)

object IcsParser {

    private val DAY_MAP = mapOf(
        "MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7
    )

    fun parse(content: String): IcsParseResult {
        val warnings = mutableListOf<String>()
        val unfolded = unfold(content)
        val components = splitComponents(unfolded)
        val events = mutableListOf<IcsEvent>()
        collectEvents(components, events, warnings)
        return IcsParseResult(events, warnings)
    }

    fun parseRrule(lineValue: String): Rrule {
        val tokens = lineValue.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        var interval = 1
        var until: LocalDate? = null
        var count: Int? = null
        var byDays = emptyList<Int>()
        var anyFreq = false
        for (t in tokens) {
            val eq = t.indexOf('=')
            if (eq <= 0) continue
            val key = t.substring(0, eq).uppercase()
            val value = t.substring(eq + 1)
            when (key) {
                "FREQ" -> if (value.equals("WEEKLY", true)) anyFreq = true
                "INTERVAL" -> interval = value.toIntOrNull() ?: 1
                "UNTIL" -> until = parseDatePart(value)
                "COUNT" -> count = value.toIntOrNull()
                "BYDAY" -> {
                    val parsed = mutableListOf<Int>()
                    for (d in value.split(",")) {
                        val cleaned = d.trim().uppercase().replace(Regex("^[+-]?\\d+"), "")
                        DAY_MAP[cleaned]?.let { parsed.add(it) }
                    }
                    byDays = parsed.distinct()
                }
            }
        }
        return Rrule(interval, byDays, until, count, parsed = anyFreq)
    }

    private fun collectEvents(
        components: List<Component>,
        out: MutableList<IcsEvent>,
        warnings: MutableList<String>
    ) {
        for (comp in components) {
            when (comp.name) {
                "VCALENDAR" -> collectEvents(comp.children, out, warnings)
                "VEVENT", "VTODO" -> {
                    val props = comp.properties
                    val startProp = props["DTSTART"]?.firstOrNull()
                    val endProp = props["DTEND"]?.firstOrNull()
                    val parsedStart = parseDt(startProp?.value, startProp?.params?.get("TZID"))
                    val parsedEnd = parseDt(endProp?.value, endProp?.params?.get("TZID"))

                    if (parsedStart == null) {
                        warnings.add("跳过无开始时间的日程：${props["SUMMARY"]?.firstOrNull()?.value.orEmpty()}")
                        continue
                    }
                    val allDay = parsedStart.time == null
                    if (allDay) {
                        warnings.add("跳过全天日程（无具体上课时间）：${props["SUMMARY"]?.firstOrNull()?.value.orEmpty()}")
                        continue
                    }

                    val date = parsedStart.date
                    if (date == null) {
                        warnings.add("跳过日期无效的日程：${props["SUMMARY"]?.firstOrNull()?.value.orEmpty()}")
                        continue
                    }

                    val event = IcsEvent(
                        summary = props["SUMMARY"]?.firstOrNull()?.value.orEmpty(),
                        location = props["LOCATION"]?.firstOrNull()?.value.orEmpty(),
                        description = props["DESCRIPTION"]?.firstOrNull()?.value.orEmpty(),
                        date = date,
                        startTime = parsedStart.time,
                        endTime = parsedEnd?.time,
                        rrule = props["RRULE"]?.firstOrNull()?.value,
                        exdates = splitExdates(props["EXDATE"]?.firstOrNull()?.value),
                        isAllDay = allDay
                    )
                    out.add(event)
                }
                else -> collectEvents(comp.children, out, warnings)
            }
        }
    }

    /** 解析以逗号分隔的 DATE/DATETIME 列表，全部截取日期部分 */
    private fun splitExdates(value: String?): List<LocalDate> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").mapNotNull { parseDatePart(it.trim()) }
    }

    /** 解析 DTSTART/DTEND 的值：返回日期 + 时间（若格式带时间则时间为非空） */
    private fun parseDt(raw: String?, tzid: String? = null): ParsedDt? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        // 日期+时间+Z（UTC）
        val utcFmt = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
        try {
            val odt = OffsetDateTime.parse(value, utcFmt)
            val local = odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            return ParsedDt(local.toLocalDate(), local.toLocalTime())
        } catch (_: Exception) {
        }
        // 日期+时间（无 Z，可能带 TZID 参数）
        val dtFormats = listOf(
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss"),
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmm"),
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'.'SSS"),
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmm'Z'")
        )
        for (fmt in dtFormats) {
            try {
                val ldt = LocalDateTime.parse(value, fmt)
                if (!tzid.isNullOrBlank()) {
                    val zone = runCatching { ZoneId.of(tzid) }.getOrNull()
                    if (zone != null) {
                        val zoned = ldt.atZone(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                        return ParsedDt(zoned.toLocalDate(), zoned.toLocalTime())
                    }
                }
                return ParsedDt(ldt.toLocalDate(), ldt.toLocalTime())
            } catch (_: Exception) {
            }
        }
        // 仅日期
        try {
            val date = LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
            return ParsedDt(date, null)
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseDatePart(raw: String?): LocalDate? = parseDt(raw)?.date

    data class ParsedDt(val date: LocalDate?, val time: LocalTime?)

    /** 折叠续行 */
    private fun unfold(content: String): List<String> {
        val raw = content.replace("\r\n", "\n").replace("\r", "\n")
        val lines = raw.split("\n")
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (line in lines) {
            if (line.isEmpty()) continue
            if (line[0] == ' ' || line[0] == '\t') {
                sb.append(line.substring(1))
            } else {
                if (sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                sb.append(line)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }

    data class Prop(
        val name: String,
        val value: String,
        val params: Map<String, String>
    )

    data class Component(
        val name: String,
        val properties: Map<String, List<Prop>>,
        val children: List<Component>
    )

    private fun splitComponents(lines: List<String>): List<Component> {
        val stack = mutableListOf<Frame>()
        val contents = mutableListOf<Component>()

        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx < 0) continue
            val head = line.substring(0, idx).trim()
            val value = line.substring(idx + 1)
            val tokens = head.split(";")
            val name = tokens.firstOrNull()?.uppercase() ?: continue
            val params = mutableMapOf<String, String>()
            for (t in tokens.drop(1)) {
                val eq = t.indexOf('=')
                if (eq > 0) {
                    params[t.substring(0, eq).trim().uppercase()] = t.substring(eq + 1).trim()
                }
            }
            when (name) {
                "BEGIN" -> stack.add(Frame(value.uppercase(), mutableMapOf(), mutableListOf()))
                "END" -> {
                    if (stack.isEmpty()) continue
                    val frame = stack.removeAt(stack.size - 1)
                    val comp = Component(frame.name, frame.properties, frame.children)
                    if (stack.isNotEmpty()) {
                        stack.last().children.add(comp)
                    } else {
                        contents.add(comp)
                    }
                }
                else -> {
                    if (stack.isEmpty()) continue
                    stack.last().properties.getOrPut(name) { mutableListOf() }.add(Prop(name, value, params))
                }
            }
        }
        return contents
    }

    private data class Frame(
        val name: String,
        val properties: MutableMap<String, MutableList<Prop>>,
        val children: MutableList<Component>
    )
}