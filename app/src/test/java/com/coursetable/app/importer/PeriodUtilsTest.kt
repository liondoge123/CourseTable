package com.coursetable.app.importer

import com.coursetable.app.data.PeriodUtils
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PeriodUtilsTest {

    @Test
    fun buildsPeriodsFromStartAndDuration() {
        val periods = PeriodUtils.build(
            listOf(LocalTime.of(8, 0), LocalTime.of(8, 55)),
            durationMinutes = 45
        )
        assertEquals(2, periods.size)
        assertEquals(LocalTime.of(8, 45), periods[0].end)
        assertEquals(LocalTime.of(9, 40), periods[1].end)
    }

    @Test
    fun validateAcceptsIncreasingStarts() {
        assertNull(
            PeriodUtils.validate(
                listOf(LocalTime.of(8, 0), LocalTime.of(8, 55)),
                durationMinutes = 45
            )
        )
    }

    @Test
    fun validateRejectsOverlap() {
        assertNotNull(
            PeriodUtils.validate(
                listOf(LocalTime.of(8, 0), LocalTime.of(8, 30)),
                durationMinutes = 45
            )
        )
    }

    @Test
    fun calculateCourseProgressCalculatesCorrectRatio() {
        val periods = PeriodUtils.build(
            listOf(LocalTime.of(8, 0), LocalTime.of(8, 55)),
            durationMinutes = 45
        ) // Section 1: 08:00 - 08:45, Section 2: 08:55 - 09:40, total 100 mins

        // Before start
        assertNull(PeriodUtils.calculateCourseProgress(1, 2, periods, LocalTime.of(7, 59)))

        // At exact start
        val atStart = PeriodUtils.calculateCourseProgress(1, 2, periods, LocalTime.of(8, 0))
        assertNotNull(atStart)
        assertEquals(0.0f, atStart!!, 0.001f)

        // Midway (e.g. 50 mins into 100 min total)
        val halfway = PeriodUtils.calculateCourseProgress(1, 2, periods, LocalTime.of(8, 50))
        assertNotNull(halfway)
        assertEquals(0.5f, halfway!!, 0.001f)

        // At end or after
        assertNull(PeriodUtils.calculateCourseProgress(1, 2, periods, LocalTime.of(9, 40)))
        assertNull(PeriodUtils.calculateCourseProgress(1, 2, periods, LocalTime.of(10, 0)))
    }
}
