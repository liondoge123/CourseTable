package com.coursetable.app

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.reminder.BootReceiver
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRecoveryRegistrationTest {
    @Test fun recoveryEventsCanReachTheManifestReceiverWithoutAnOpenActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (action in BootReceiver.RECOVERY_ACTIONS) {
            val receivers = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName), PackageManager.GET_RESOLVED_FILTER
            )
            assertTrue("Missing recovery receiver for $action", receivers.any {
                it.activityInfo.name == BootReceiver::class.java.name && it.activityInfo.enabled
            })
        }
    }
}
