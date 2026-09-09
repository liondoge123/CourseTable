package com.coursetable.app.importer

import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.WeekType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object IcsExporter {

    private val DAY_CODE = mapOf(
        1 to "MO", 2 to "TU", 3 to "WE", 4 to "TH", 5 to "FR", 6 to "SA", 7 to "SU"
    )
    private val DT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun export(settings: AppSettings, courses: List<Course>): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//CourseTable//CN\r\n")
        for (course in courses) {
            appendEvent(sb, settings, course)
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun appendEvent(sb: StringBuilder, settings: AppSettings, course: Course) {
        val dayCode = DAY_CODE[course.dayOfWeek] ?: return
        val firstDate = firstOccurrence(settings, course.startWeek, course.dayOfWeek) ?: return
        val startTime = settings.periods.getOrNull(course.startSection - 1)?.start ?: LocalTime.of(8, 0)
        val endIndex = (course.startSection - 1 + course.duration - 1).coerceIn(0, settings.periods.size - 1)
        val endTime = settings.periods.getOrNull(endIndex)?.end ?: startTime.plusMinutes(45)

        sb.append("BEGIN:VEVENT\r\n")
        foldLine(sb, "UID", "${course.id}-${firstDate}-${dayCode}@coursetable")
        val dt = firstDate.atTime(startTime)
        foldLine(sb, "DTSTART;TZID=Asia/Shanghai", dt.format(DT_FMT))
        foldLine(sb, "DTEND;TZID=Asia/Shanghai", firstDate.atTime(endTime).format(DT_FMT))
        foldLine(sb, "SUMMARY", course.name.replace(Regex("[\r\n]"), " "))
        if (course.location.isNotBlank()) foldLine(sb, "LOCATION", course.location)
        if (course.teacher.isNotBlank()) foldLine(sb, "DESCRIPTION", course.teacher)

        val interval = if (course.weekType == WeekType.ALL.code) 1 else 2
        val occurrences = occurrencesInRange(course.startWeek, course.endWeek, course.weekType)
        if (occurrences > 1) {
            foldLine(sb, "RRULE", "FREQ=WEEKLY;INTERVAL=$interval;BYDAY=$dayCode;COUNT=$occurrences")
        }
        sb.append("END:VEVENT\r\n")
    }

    private fun firstOccurrence(settings: AppSettings, startWeek: Int, dayOfWeek: Int): LocalDate? {
        val startDay = settings.semesterStart.dayOfWeek.value
        var date = settings.semesterStart.plusDays(((startWeek - 1) * 7).toLong())
        var offset = dayOfWeek - startDay
        if (offset < 0) offset += 7
        date = date.plusDays(offset.toLong())
        return date
    }

    private fun occurrencesInRange(startWeek: Int, endWeek: Int, weekType: Int): Int {
        var count = 0
        for (w in startWeek..endWeek) {
            val match = when (weekType) {
                WeekType.ODD.code -> w % 2 == 1
                WeekType.EVEN.code -> w % 2 == 0
                else -> true
            }
            if (match) count++
        }
        return count
    }

    private fun foldLine(sb: StringBuilder, name: String, value: String) {
        val line = "$name:$value"
        if (line.length <= 75) {
            sb.append(line).append("\r\n")
            return
        }
        sb.append(line.substring(0, 75)).append("\r\n")
        var rest = line.substring(75)
        while (rest.length > 74) {
            sb.append(" ").append(rest.substring(0, 74)).append("\r\n")
            rest = rest.substring(74)
        }
        if (rest.isNotEmpty()) {
            sb.append(" ").append(rest).append("\r\n")
        }
    }
}
