package com.coursetable.app.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekUtilsTest {
    @Test fun nonMondayStartChangesWeekOnMonday() {
        val start = LocalDate.of(2026, 9, 1)
        assertEquals(1, WeekUtils.weekOf(start, LocalDate.of(2026, 8, 31)))
        assertEquals(1, WeekUtils.weekOf(start, LocalDate.of(2026, 9, 6)))
        assertEquals(2, WeekUtils.weekOf(start, LocalDate.of(2026, 9, 7)))
        assertEquals(8, WeekUtils.weekOf(start, LocalDate.of(2026, 10, 19)))
    }

    @Test fun datesBeforeTheFirstWeekAreNotTruncatedIntoWeekOne() {
        val start = LocalDate.of(2026, 9, 1)
        assertEquals(0, WeekUtils.weekOf(start, LocalDate.of(2026, 8, 30)))
        assertEquals(0, WeekUtils.weekOf(start, LocalDate.of(2026, 8, 24)))
        assertEquals(-1, WeekUtils.weekOf(start, LocalDate.of(2026, 8, 23)))
    }

    @Test fun everyHeaderDateMapsBackToItsWeekIncludingYearAndLeapDayBoundaries() {
        for (start in listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2028, 2, 29))) {
            for (week in -2..60) {
                for (day in 1..7) {
                    val date = WeekUtils.dateOfWeek(start, week, day)
                    assertEquals("$start / $date", week, WeekUtils.weekOf(start, date))
                    assertEquals(day, date.dayOfWeek.value)
                }
            }
        }
    }
}
