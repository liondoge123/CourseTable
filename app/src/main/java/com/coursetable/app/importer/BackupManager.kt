package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.Timetable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/** 一个课表及其课程 */
data class TimetableBackup(
    val timetable: Timetable,
    val courses: List<Course>,
    val preserveCourseGroups: Boolean = false
)

data class BackupData(
    val timetables: List<TimetableBackup>,
    val warnings: List<String>
)

object BackupManager {

    const val FORMAT_VERSION = 4

    fun export(timetables: List<TimetableBackup>): String {
        val root = JSONObject()
        root.put("app", "CourseTable")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", LocalDateTime.now().toString())

        val tables = JSONArray()
        for (tb in timetables) {
            val t = JSONObject()
            t.put("name", tb.timetable.name)
            t.put("semesterStart", tb.timetable.semesterStart().format(DateTimeFormatter.ISO_LOCAL_DATE))
            t.put("totalWeeks", tb.timetable.totalWeeks)
            t.put("periods", JSONArray(tb.timetable.periods().map { it.label() }))
            t.put("periodDuration", tb.timetable.periodDurationMinutes)

            val arr = JSONArray()
            for (c in tb.courses) {
                val o = JSONObject()
                o.put("name", c.name)
                o.put("teacher", c.teacher)
                o.put("location", c.location)
                o.put("dayOfWeek", c.dayOfWeek)
                o.put("startSection", c.startSection)
                o.put("duration", c.duration)
                o.put("startWeek", c.startWeek)
                o.put("endWeek", c.endWeek)
                o.put("weekType", c.weekType)
                o.put("selectedWeeksCsv", c.selectedWeeksCsv)
                o.put("color", c.color)
                o.put("courseKey", c.courseId.takeIf { it > 0 }?.toString() ?: "record-${arr.length()}")
                arr.put(o)
            }
            // Group relations are explicit; local database IDs are never restored verbatim.
            val groups = JSONArray()
            for ((key, sessions) in tb.courses.withIndex().groupBy { it.value.courseId.takeIf { id -> id > 0 }?.toString() ?: "record-${it.index}" }) {
                val first = sessions.first().value
                groups.put(JSONObject().put("key", key).put("name", first.name).put("teacher", first.teacher).put("color", first.color)
                    .put("sessions", JSONArray(sessions.map { arr.getJSONObject(it.index) })))
            }
            t.put("courseGroups", groups)
            tables.put(t)
        }
        root.put("timetables", tables)
        return root.toString(2)
    }

    fun parse(content: String): BackupData {
        val warnings = mutableListOf<String>()
        if (content.length > DetectedImportType.JSON.maxBytes) {
            return BackupData(emptyList(), listOf("备份文件过大"))
        }
        val root = try {
            JSONObject(content)
        } catch (e: Exception) {
            return BackupData(emptyList(), warnings + "不是有效的 JSON 备份文件")
        }
        if (root.optString("app").isNotEmpty() && root.optString("app") != "CourseTable") {
            return BackupData(emptyList(), warnings + "不是本应用的备份文件")
        }
        if (root.optInt("version", 1) > FORMAT_VERSION) return BackupData(emptyList(), listOf("此备份版本较新，请升级应用"))

        // v2 格式：timetables 数组
        root.optJSONArray("timetables")?.let { arr ->
            if (arr.length() > ImportPolicy.MAX_TIMETABLES) {
                return BackupData(emptyList(), warnings + "备份中的课表数量超过 ${ImportPolicy.MAX_TIMETABLES} 个")
            }
            val result = mutableListOf<TimetableBackup>()
            var remainingCourses = ImportPolicy.MAX_OUTPUT_COURSES
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = bounded(o.optString("name").ifBlank { "导入的课表" })
                val timetable = parseTimetableMeta(o, name, warnings)
                val groups = o.optJSONArray("courseGroups")
                val flat = if (groups == null) o.optJSONArray("courses") else JSONArray().apply {
                    for (groupIndex in 0 until minOf(groups.length(), ImportPolicy.MAX_OUTPUT_COURSES)) {
                        val group = groups.optJSONObject(groupIndex) ?: continue
                        val sessions = group.optJSONArray("sessions") ?: continue
                        for (sessionIndex in 0 until minOf(sessions.length(), remainingCourses)) {
                            val session = sessions.optJSONObject(sessionIndex) ?: continue
                            put(JSONObject(session.toString()).put("courseKey", "group-$groupIndex").put("name", group.optString("name"))
                                .put("teacher", group.optString("teacher")).put("color", group.optLong("color", 0xFF0A84FF)))
                            if (length() >= remainingCourses) break
                        }
                        if (length() >= remainingCourses) break
                    }
                }
                val courses = parseCourses(flat, warnings, remainingCourses)
                remainingCourses -= courses.size
                result.add(TimetableBackup(timetable, courses, preserveCourseGroups = groups != null))
            }
            if (result.isEmpty()) warnings.add("备份中没有课表数据")
            return BackupData(result, warnings)
        }

        // 兼容 v1 旧格式：settings + courses → 归入一个课表
        val legacy = parseLegacy(root, warnings)
        return BackupData(legacy, warnings)
    }

    private fun parseTimetableMeta(o: JSONObject, name: String, warnings: MutableList<String>): Timetable {
        val startStr = o.optString("semesterStart")
        val start = try {
            LocalDate.parse(startStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            warnings.add("课表「$name」的学期开始日期无效，已使用默认值")
            AppSettings().semesterStart
        }
        val periods = parsePeriods(o.optJSONArray("periods"))
        return Timetable(
            id = 0,
            name = name,
            semesterStartEpochDay = start.toEpochDay(),
            totalWeeks = o.optInt("totalWeeks", 18).coerceIn(1, 60),
            periodsCsv = Timetable.serializePeriods(
                if (periods.isNotEmpty()) periods else AppSettings.defaultPeriods()
            ),
            periodDurationMinutes = o.optInt("periodDuration", 45).coerceIn(20, 90)
        )
    }

    private fun parseCourses(
        arr: JSONArray?,
        warnings: MutableList<String>,
        limit: Int = ImportPolicy.MAX_OUTPUT_COURSES
    ): List<Course> {
        val courses = mutableListOf<Course>()
        if (arr == null) return courses
        val courseIds = mutableMapOf<String, Long>()
        var nextCourseId = 1L
        if (arr.length() > limit) warnings.add("课程数量超过上限，仅保留前 $limit 门")
        for (i in 0 until minOf(arr.length(), limit)) {
            val o = arr.optJSONObject(i) ?: continue
            val name = bounded(o.optString("name").trim())
            if (name.isEmpty()) warnings.add("第 ${i + 1} 条课程名称为空，请校对")
            val startWeek = o.optInt("startWeek", 0)
            val endWeek = o.optInt("endWeek", 0)
            courses.add(
                Course(
                    id = 0,
                    name = name,
                    teacher = bounded(o.optString("teacher")),
                    location = bounded(o.optString("location")),
                    dayOfWeek = o.optInt("dayOfWeek", 0),
                    startSection = o.optInt("startSection", 0),
                    duration = o.optInt("duration", 0),
                    startWeek = startWeek,
                    endWeek = endWeek,
                    weekType = o.optInt("weekType", 0),
                    color = o.optLong("color", 0xFF0A84FF),
                    selectedWeeksCsv = o.optString("selectedWeeksCsv").take(240),
                    courseId = o.optString("courseKey").takeIf { it.isNotBlank() }?.let { key ->
                        courseIds.getOrPut(key) { nextCourseId++ }
                    } ?: nextCourseId++
                )
            )
        }
        return courses
    }

    /** 兼容 v1：settings + courses */
    private fun parseLegacy(root: JSONObject, warnings: MutableList<String>): List<TimetableBackup> {
        val st = root.optJSONObject("settings")
        var start = AppSettings().semesterStart
        var totalWeeks = 18
        var periods = AppSettings.defaultPeriods()
        if (st != null) {
            val startStr = st.optString("semesterStart")
            try {
                start = LocalDate.parse(startStr, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                warnings.add("学期开始日期无效：$startStr")
            }
            totalWeeks = st.optInt("totalWeeks", 18).coerceIn(1, 60)
            val parsed = parsePeriods(st.optJSONArray("periods"))
            if (parsed.isNotEmpty()) periods = parsed
        } else {
            warnings.add("备份中缺少学期设置，已使用默认值")
        }
        val courses = parseCourses(root.optJSONArray("courses"), warnings)
        val timetable = Timetable(
            id = 0,
            name = "导入的课表",
            semesterStartEpochDay = start.toEpochDay(),
            totalWeeks = totalWeeks,
            periodsCsv = Timetable.serializePeriods(periods),
            periodDurationMinutes = 45
        )
        return listOf(TimetableBackup(timetable, courses))
    }

    private fun parsePeriods(arr: JSONArray?): List<PeriodTime> {
        val result = mutableListOf<PeriodTime>()
        if (arr == null) return result
        for (i in 0 until minOf(arr.length(), 30)) {
            val token = arr.optString(i).trim()
            val parts = token.split("-")
            if (parts.size != 2) continue
            try {
                val s = LocalTime.parse(parts[0].trim(), PeriodTime.TIME_FMT)
                val e = LocalTime.parse(parts[1].trim(), PeriodTime.TIME_FMT)
                result.add(PeriodTime(s, e))
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun bounded(value: String): String = value.take(ImportPolicy.MAX_TEXT_FIELD)
}
