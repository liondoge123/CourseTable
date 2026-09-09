package com.coursetable.app.importer.edu

import com.coursetable.app.data.WeekType
import com.coursetable.app.importer.CandidateCourse
import com.coursetable.app.importer.PdfParseResult
import org.json.JSONArray
import org.json.JSONObject

object ZfJwglxtParser {

    fun parseScheduleJson(json: String): PdfParseResult {
        val warnings = mutableListOf<String>()
        val candidates = mutableListOf<CandidateCourse>()
        return try {
            val root = JSONObject(json)
            val list: JSONArray = root.optJSONArray("kbList")
                ?: return PdfParseResult(emptyList(), warnings + "响应中没有 kbList 数据")
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val name = item.optString("kcmc").trim()
                if (name.isEmpty()) continue
                val day = item.optString("xqj").trim().toIntOrNull() ?: item.optInt("xqj", 0)
                if (day !in 1..7) continue
                val sectionText = item.optString("jcs").ifBlank { item.optString("jc") }
                val (startSection, duration) = parseSections(sectionText)
                if (startSection <= 0) continue
                val weeks = parseWeeks(item.optString("zcd"))
                if (weeks.isEmpty()) {
                    warnings.add("无法解析周次（${item.optString("zcd")}），已跳过：$name")
                    continue
                }
                for ((ws, we, wt) in weeks) {
                    candidates.add(
                        CandidateCourse(
                            name = name,
                            location = item.optString("cdmc").trim(),
                            teacher = item.optString("xm").trim(),
                            dayOfWeek = day,
                            startSection = startSection,
                            duration = duration,
                            startWeek = ws,
                            endWeek = we,
                            weekType = wt
                        )
                    )
                }
            }
            PdfParseResult(mergeDuplicateEntries(candidates), warnings)
        } catch (e: Exception) {
            PdfParseResult(emptyList(), warnings + "教务数据解析失败：${e.message}")
        }
    }

    private fun parseSections(jcs: String): Pair<Int, Int> {
        val cleaned = jcs.trim()
        if (cleaned.isEmpty()) return 0 to 1
        val m = Regex("(\\d{1,2})\\s*[-~]\\s*(\\d{1,2})").find(cleaned)
        if (m != null) {
            val s = m.groupValues[1].toIntOrNull() ?: return 0 to 1
            val e = m.groupValues[2].toIntOrNull() ?: s
            return s to (e - s + 1).coerceAtLeast(1)
        }
        val single = Regex("\\d{1,2}").find(cleaned)?.value?.toIntOrNull() ?: return 0 to 1
        return single to 1
    }

    internal fun parseWeeks(zcd: String): List<Triple<Int, Int, Int>> {
        val cleaned = zcd.trim().removeSuffix("周").trim()
        if (cleaned.isEmpty()) return emptyList()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        val odd = cleaned.contains("单")
        val even = cleaned.contains("双")
        val type = when {
            odd -> WeekType.ODD.code
            even -> WeekType.EVEN.code
            else -> WeekType.ALL.code
        }
        val plain = cleaned.replace(Regex("[()（）]"), "")
            .replace("单", "").replace("双", "")
        for (token in plain.split(Regex("[、,，]"))) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val range = Regex("(\\d{1,2})\\s*[-~～]\\s*(\\d{1,2})").find(t)
            if (range != null) {
                val s = range.groupValues[1].toIntOrNull()
                val e = range.groupValues[2].toIntOrNull()
                if (s != null && e != null && e >= s) result.add(Triple(s, e, type))
                continue
            }
            val single = Regex("\\d{1,2}").find(t)?.value?.toIntOrNull()
            if (single != null) result.add(Triple(single, single, type))
        }
        return result
    }

    private fun mergeDuplicateEntries(candidates: List<CandidateCourse>): List<CandidateCourse> {
        val grouped = candidates.groupBy { Triple(it.name, it.dayOfWeek, it.startSection) }
        return grouped.values.map { list ->
            if (list.size == 1) {
                list.first()
            } else {
                val first = list.first()
                first.copy(
                    startWeek = list.minOf { it.startWeek },
                    endWeek = list.maxOf { it.endWeek },
                    location = list.firstOrNull { it.location.isNotBlank() }?.location ?: first.location,
                    teacher = list.firstOrNull { it.teacher.isNotBlank() }?.teacher ?: first.teacher
                )
            }
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
    }
}
