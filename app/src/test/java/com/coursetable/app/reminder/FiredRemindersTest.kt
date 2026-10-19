package com.coursetable.app.reminder

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FiredRemindersTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test fun restoresEventKeysContainingSeparators() {
        val records = setOf("42@20743@2026-10-17", "test@123456@2026-10-17")
        val restored = recentFiredReminders(records, today)
        assertEquals(today, restored["42@20743"])
        assertEquals(today, restored["test@123456"])
        assertEquals(records, restored.map { "${it.key}@${it.value}" }.toSet())
    }

    @Test fun prunesOldFutureAndMalformedRecordsWithoutDroppingRecentEvents() {
        val restored = recentFiredReminders(setOf("a@2026-10-10", "b@2026-10-09", "c@2026-10-18", "broken", "d@invalid", "@2026-10-17"), today)
        assertEquals(mapOf("a" to today.minusDays(7)), restored)
    }
}
