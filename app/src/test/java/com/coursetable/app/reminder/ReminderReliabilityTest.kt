package com.coursetable.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ReminderReliabilityTest {
    @Test fun unknownOldAlarmDoesNotPretendToBeOnTime() {
        assertEquals("旧提醒未记录计划时间", reminderTimingLabel(0L, 50_000L))
    }

    @Test fun reportsActualLateDeliveryEvenWhenItEventuallyArrives() {
        assertEquals("触发延迟 65 秒", reminderTimingLabel(30_000L, 95_999L))
    }

    @Test fun subsecondDeliveryIsDistinctFromAWholeSecondDelay() {
        assertEquals("触发延迟不足 1 秒", reminderTimingLabel(30_000L, 30_999L))
        assertEquals("触发延迟 1 秒", reminderTimingLabel(30_000L, 31_000L))
    }

    @Test fun clockMovingBackwardsIsNotReportedAsZeroDelay() {
        assertEquals("设备时间发生变化，无法判断延迟", reminderTimingLabel(30_000L, 29_000L))
    }

    @Test fun missingModeFromAnOlderPendingAlarmIsNotReportedAsExact() {
        assertEquals("旧提醒未记录调度方式", reminderAlarmModeLabel(null))
        assertEquals("旧提醒未记录调度方式", reminderAlarmModeLabel("unrecognized"))
    }

    @Test fun permittedExactAlarmNeverAlsoRegistersAnInexactAlarm() {
        var exactCalls = 0
        val result = registerReminderAlarm(true, { exactCalls++ }, { error("Unexpected fallback") })
        assertEquals(1, exactCalls)
        assertEquals(ReminderAlarmMode.EXACT, result)
    }

    @Test fun missingPermissionUsesOnlyTheInexactPathAndRecordsWhy() {
        val observed = mutableListOf<ReminderAlarmMode>()
        val result = registerReminderAlarm(false, { error("Must not attempt exact") }, { observed += it })
        assertEquals(listOf(ReminderAlarmMode.INEXACT_PERMISSION_MISSING), observed)
        assertEquals(observed.single(), result)
    }

    @Test fun permissionRevokedDuringRegistrationIsRecordedAsARejection() {
        val observed = mutableListOf<ReminderAlarmMode>()
        val result = registerReminderAlarm(true, { throw SecurityException() }, { observed += it })
        assertEquals(listOf(ReminderAlarmMode.INEXACT_PERMISSION_REJECTED), observed)
        assertEquals(observed.single(), result)
    }

    @Test fun unrelatedExactAlarmFailuresAreNotMisreportedAsMissingPermission() {
        var fallbackCalls = 0
        val failure = IllegalStateException("Registration failed")
        try {
            registerReminderAlarm(true, { throw failure }, { fallbackCalls++ })
            fail("Expected the original failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals(0, fallbackCalls)
    }

    @Test(expected = SecurityException::class)
    fun failedFallbackIsNotReportedAsSuccessfulScheduling() {
        registerReminderAlarm(false, { error("Unexpected exact") }, { throw SecurityException() })
    }

    @Test fun alarmClockRegistrationPreservesItsDistinctDiagnosticMode() {
        var registered = false
        val mode = registerReminderAlarm(true, { registered = true }, { error("Unexpected fallback") },
            exactMode = ReminderAlarmMode.ALARM_CLOCK)
        assertEquals(true, registered)
        assertEquals(ReminderAlarmMode.ALARM_CLOCK, mode)
    }

    @Test fun rejectedAlarmClockFallsBackWithoutClaimingAlarmClockPrecision() {
        val modes = mutableListOf<ReminderAlarmMode>()
        val mode = registerReminderAlarm(true, { throw SecurityException() }, { modes += it },
            exactMode = ReminderAlarmMode.ALARM_CLOCK)
        assertEquals(listOf(ReminderAlarmMode.INEXACT_PERMISSION_REJECTED), modes)
        assertEquals(modes.single(), mode)
    }
}
