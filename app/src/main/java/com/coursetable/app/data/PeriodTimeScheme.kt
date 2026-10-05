package com.coursetable.app.data

import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/** 可跨课表复用的节次时间快照；修改课表不会修改已保存的方案。 */
data class PeriodTimeScheme(
    val id: String,
    val name: String,
    val periods: List<PeriodTime>,
    /** Default for new periods; existing periods may have individual durations. */
    val durationMinutes: Int
) {
    val isDefault: Boolean get() = isDefaultId(id)
    fun matches(periods: List<PeriodTime>, durationMinutes: Int): Boolean =
        this.periods == periods && this.durationMinutes == durationMinutes

    companion object {
        private const val DEFAULT_ID_PREFIX = "builtin-default:"
        const val DEFAULT_NAME = "默认方案"

        fun defaultId(timetableId: Long): String = "$DEFAULT_ID_PREFIX$timetableId"
        fun isDefaultId(id: String): Boolean = id.startsWith(DEFAULT_ID_PREFIX)

        fun defaultFor(timetableId: Long, periods: List<PeriodTime> = AppSettings.defaultPeriods(), durationMinutes: Int = 45) =
            PeriodTimeScheme(defaultId(timetableId), DEFAULT_NAME, periods, durationMinutes)

        /** The protected default is always first, even before stored preferences load. */
        fun withDefault(schemes: List<PeriodTimeScheme>, fallback: PeriodTimeScheme): List<PeriodTimeScheme> =
            listOf(schemes.firstOrNull { it.isDefault } ?: fallback) + schemes.filterNot { it.isDefault }

        fun encode(schemes: List<PeriodTimeScheme>): String = JSONArray().apply {
            schemes.forEach { scheme ->
                put(JSONObject().apply {
                    put("id", scheme.id)
                    put("name", scheme.name)
                    put("durationMinutes", scheme.durationMinutes)
                    put("periods", JSONArray().apply {
                        scheme.periods.forEach { period ->
                            put(JSONObject().apply {
                                put("start", period.start.toString())
                                put("end", period.end.toString())
                            })
                        }
                    })
                })
            }
        }.toString()

        fun decode(raw: String?): List<PeriodTimeScheme> {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val item = array.getJSONObject(index)
                    val times = item.getJSONArray("periods")
                    val periods = (0 until times.length()).map { timeIndex ->
                        val time = times.getJSONObject(timeIndex)
                        PeriodTime(LocalTime.parse(time.getString("start")), LocalTime.parse(time.getString("end")))
                    }
                    PeriodTimeScheme(
                        id = item.getString("id"),
                        name = item.getString("name").trim(),
                        periods = periods,
                        durationMinutes = item.getInt("durationMinutes")
                    ).also {
                        require(it.id.isNotBlank() && it.name.isNotBlank())
                        require(it.durationMinutes > 0 && it.periods.isNotEmpty())
                        require(it.periods.all { period -> period.end > period.start })
                        require(it.periods.zipWithNext().all { (a, b) -> b.start >= a.end })
                    }
                }.getOrNull()
            }.distinctBy { it.id }
        }
    }
}
