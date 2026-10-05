package com.coursetable.app.data

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class PeriodEditingTest {
    private fun time(start: String, end: String) = PeriodTime(LocalTime.parse(start), LocalTime.parse(end))
    private val periods = listOf(time("08:00", "08:45"), time("08:55", "09:45"), time("14:00", "14:45"))

    @Test fun manualEndChangesOnlyCurrentPeriodAndAllowsCustomDuration() {
        val next = PeriodUtils.replacePeriod(periods, 0, time("08:00", "08:50"))
        assertEquals(periods.drop(1), next.drop(1))
        assertEquals(50, PeriodUtils.durationMinutes(next.first()))
        assertNull(PeriodUtils.validatePeriods(next))
    }

    @Test fun selectedShiftRangePreservesGapsAndIndividualDurations() {
        val next = PeriodUtils.replacePeriod(periods, 0, time("08:00", "08:50"), shiftThrough = 1)
        assertEquals(time("09:00", "09:50"), next[1])
        assertEquals(periods[2], next[2])
        assertNull(PeriodUtils.validatePeriods(next))
        val earlier = PeriodUtils.replacePeriod(periods, 0, time("07:00", "07:45"), shiftThrough = 1)
        assertEquals(time("07:55", "08:45"), earlier[1])
    }

    @Test fun overlapAndOutOfOrderMessagesIdentifyExactPeriods() {
        assertEquals("第 1 节与第 2 节重叠 5 分钟",
            PeriodUtils.validatePeriods(listOf(time("08:00", "09:00"), time("08:55", "09:45"))))
        assertTrue(PeriodUtils.validatePeriods(listOf(time("08:00", "08:45"), time("07:55", "09:00")))!!.contains("第 2 节"))
        assertNotNull(PeriodUtils.validatePeriods(listOf(time("23:45", "00:30"))))
        assertNotNull(PeriodUtils.validatePeriods(listOf(time("08:00", "08:00"))))
        assertNull(PeriodUtils.validatePeriods(listOf(time("08:00", "08:45"), time("08:45", "09:30"))))
    }

    @Test fun uniformDurationKeepsStartsAndRequiresOverlapValidation() {
        val uniform = PeriodUtils.withUniformDuration(periods, 60)
        assertEquals(periods.map { it.start }, uniform.map { it.start })
        assertEquals(LocalTime.of(9, 0), uniform[0].end)
        assertNotNull(PeriodUtils.validatePeriods(uniform))
    }

    @Test(expected = IllegalArgumentException::class)
    fun shiftPastMidnightIsRejectedWithoutChangingOriginal() {
        PeriodUtils.replacePeriod(listOf(time("22:00", "22:45"), time("23:00", "23:50")),
            0, time("22:00", "23:00"), shiftThrough = 1)
    }

    @Test fun addingUsesDefaultWhileKeepingExistingCustomEnds() {
        val next = PeriodUtils.appendPeriod(periods, 40)
        assertEquals(periods, next.dropLast(1))
        assertEquals(time("14:55", "15:35"), next.last())
        assertTrue(runCatching { PeriodUtils.appendPeriod(listOf(time("23:00", "23:40")), 45) }.isFailure)
        assertTrue(runCatching { PeriodUtils.withUniformDuration(listOf(time("23:30", "23:40")), 60) }.isFailure)
    }
}
