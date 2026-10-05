package com.coursetable.app.data

import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodTimeSchemeTest {
    @Test fun defaultAlwaysExistsFirstAndUsesExistingTableTimes() {
        val fallback = PeriodTimeScheme.defaultFor(7, summer.periods, 45)
        assertEquals(listOf(fallback), PeriodTimeScheme.withDefault(emptyList(), fallback))
        assertTrue(fallback.isDefault)
        assertTrue(PeriodTimeScheme.isDefaultId(fallback.id))
        assertEquals("默认方案", fallback.name)
        val stored = fallback.copy(periods = winter.periods, durationMinutes = 50)
        assertEquals(listOf(stored, summer), PeriodTimeScheme.withDefault(listOf(summer, stored), fallback))
        assertEquals(listOf(stored), PeriodTimeScheme.decode(PeriodTimeScheme.encode(listOf(stored))))
        assertFalse(summer.isDefault)
        assertFalse(PeriodTimeScheme.defaultFor(8).id == fallback.id)
    }
    private val summer = PeriodTimeScheme(
        "summer", "夏季作息",
        listOf(
            PeriodTime(LocalTime.of(8, 0), LocalTime.of(8, 45)),
            PeriodTime(LocalTime.of(9, 0), LocalTime.of(9, 50))
        ),
        45
    )
    private val winter = summer.copy(
        id = "winter", name = "冬季作息",
        periods = PeriodUtils.build(listOf(LocalTime.of(8, 30), LocalTime.of(9, 30)), 50),
        durationMinutes = 50
    )

    @Test
    fun roundTripPreservesMultipleSchemesAndIndividualEndTimes() {
        val schemes = listOf(summer, winter)
        assertEquals(schemes, PeriodTimeScheme.decode(PeriodTimeScheme.encode(schemes)))
    }

    @Test
    fun matchingRequiresBothFullPeriodTimesAndDuration() {
        assertTrue(summer.matches(summer.periods, 45))
        assertFalse(summer.matches(winter.periods, 45))
        assertFalse(summer.matches(summer.periods, 50))
        assertFalse(summer.matches(summer.periods.take(1), 45))
        assertFalse(summer.matches(summer.periods.map { it.copy(end = it.end.plusMinutes(1)) }, 45))
    }

    @Test
    fun missingOrMalformedStorageDoesNotCrash() {
        listOf(null, "", "not json", "{}", "[null]").forEach {
            assertTrue(PeriodTimeScheme.decode(it).isEmpty())
        }
    }

    @Test
    fun malformedSchemeDoesNotDiscardOtherSavedSchemes() {
        val array = JSONArray(PeriodTimeScheme.encode(listOf(summer)))
        array.put(JSONObject().put("id", "broken"))
        array.put(JSONArray(PeriodTimeScheme.encode(listOf(winter))).getJSONObject(0))
        assertEquals(listOf(summer, winter), PeriodTimeScheme.decode(array.toString()))
    }

    @Test
    fun emptyOverlappingOrMidnightWrappingSchemesAreRejected() {
        val invalid = listOf(
            summer.copy(periods = emptyList()),
            summer.copy(periods = PeriodUtils.build(listOf(LocalTime.of(8, 0), LocalTime.of(8, 10)), 45)),
            summer.copy(periods = PeriodUtils.build(listOf(LocalTime.of(23, 45)), 45)),
            summer.copy(durationMinutes = 0),
            summer.copy(name = " ")
        )
        assertTrue(PeriodTimeScheme.decode(PeriodTimeScheme.encode(invalid)).isEmpty())
    }
}
