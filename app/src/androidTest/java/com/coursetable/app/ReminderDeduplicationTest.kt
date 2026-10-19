package com.coursetable.app

import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.reminder.ReminderScheduler
import org.junit.Assert.*
import org.junit.Test

class ReminderDeduplicationTest {
    @Test fun sameEventIsClaimedOnlyOnceAndSeparateSessionsRemainIndependent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "regression-${java.util.UUID.randomUUID()}"
        assertTrue(ReminderScheduler.claimFired(context, "$key@1", "去重测试"))
        assertFalse(ReminderScheduler.claimFired(context, "$key@1", "去重测试"))
        assertTrue(ReminderScheduler.claimFired(context, "$key@2", "去重测试"))
        assertFalse(ReminderScheduler.claimFired(context, "$key@2", "去重测试"))
    }
}
