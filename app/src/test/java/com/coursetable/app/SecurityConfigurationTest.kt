package com.coursetable.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigurationTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $path from ${File(".").absolutePath}")
    }

    @Test
    fun manifestUsesScopedNetworkBackupAndAlarmPolicies() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertFalse(manifest.contains("android:scheme=\"file\""))
        assertFalse(manifest.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))

        val network = source("src/main/res/xml/network_security_config.xml")
        assertTrue(network.contains("<base-config cleartextTrafficPermitted=\"false\""))
        assertTrue(network.contains(">jwxt.ujs.edu.cn</domain>"))

        val backup = source("src/main/res/xml/data_extraction_rules.xml")
        assertTrue(backup.contains("<exclude domain=\"database\" path=\".\""))
        assertTrue(backup.contains("<exclude domain=\"sharedpref\" path=\".\""))
    }

    @Test
    fun remindersCheckCapabilityAndAlwaysHaveFallback() {
        val scheduler = source("src/main/java/com/coursetable/app/reminder/ReminderScheduler.kt")
        assertTrue(scheduler.contains("canScheduleExactAlarms()"))
        assertTrue(scheduler.contains("setExactAndAllowWhileIdle"))
        assertTrue(scheduler.contains("setAndAllowWhileIdle"))
        assertTrue(scheduler.contains("setWindow"))
        assertFalse(scheduler.contains("setAlarmClock"))
    }

    @Test
    fun educationWebViewDisablesDangerousAccess() {
        val screen = source("src/main/java/com/coursetable/app/ui/EduImportScreen.kt")
        assertTrue(screen.contains("settings.allowFileAccess = false"))
        assertTrue(screen.contains("settings.allowContentAccess = false"))
        assertTrue(screen.contains("WebSettings.MIXED_CONTENT_NEVER_ALLOW"))
        assertTrue(screen.contains("host in school.allowedHosts"))
        assertTrue(screen.contains("removeAllCookies"))
    }
}
