package com.coursetable.app.data

import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/** 可跨课表复用的节次时间快照；修改课表不会修改已保存的方案。 */
data class PeriodTimeScheme(
    val id: String,
    val name: String,
    val periods: List<PeriodTime>,
    val durationMinutes: Int
) {
    fun matches(periods: List<PeriodTime>, durationMinutes: Int): Boolean =
        this.periods == periods && this.durationMinutes == durationMinutes

    companion object {
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
