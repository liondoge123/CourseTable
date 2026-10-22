package com.coursetable.app

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeMode
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises cold and repeated dialog entrances with the real render clock. */
class NativeDialogMotionTest {
    @get:Rule val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun dateNumberAndConfirmationDialogsOpenAndDismissInBothThemes() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val showing = mutableStateOf<Int?>(null)
        val dismiss = AtomicReference<(() -> Unit)?>(null)
        val ready = AtomicBoolean()
        activity.scenario.onActivity { screen ->
            screen.enableEdgeToEdge()
            screen.setContent {
                CourseTableTheme(themeMode = mode.value) {
                    LiquidBackdropHost(Modifier.fillMaxSize().drawWithContent { drawContent(); ready.set(true) }) {
                        LiquidAmbientBackground(Modifier.fillMaxSize().glassBackdropSource())
                        when (showing.value) {
                            0 -> {
                                val date = rememberDatePickerState(1788134400000L)
                                DatePickerDialog({ showing.value = null }, confirmButton = {
                                    val controller = LocalDialogDismissController.current
                                    DisposableEffect(controller) {
                                        val action = { controller?.dismiss(); Unit }
                                        dismiss.set(action)
                                        onDispose { dismiss.compareAndSet(action, null) }
                                    }
                                    TextButton({}) { Text("确定") }
                                }, dismissButton = {
                                    val controller = LocalDialogDismissController.current
                                    TextButton({ controller?.dismiss() }) { Text("取消") }
                                }) { DatePicker(date) }
                            }
                            1 -> NumberWheelPickerDialog("学期总周数", 1..60, 18,
                                onConfirm = {}, onDismiss = { showing.value = null })
                            2 -> AlertDialog({ showing.value = null }, title = { Text("确认操作") },
                                text = { Text("检查弹窗打开和关闭时的玻璃边缘。") }, confirmButton = {
                                    val controller = LocalDialogDismissController.current
                                    DisposableEffect(controller) {
                                        val action = { controller?.dismiss(); Unit }
                                        dismiss.set(action)
                                        onDispose { dismiss.compareAndSet(action, null) }
                                    }
                                    TextButton({}) { Text("确定") }
                                }, dismissButton = {
                                    val controller = LocalDialogDismissController.current
                                    TextButton({ controller?.dismiss() }) { Text("取消") }
                                })
                        }
                    }
                }
            }
        }
        val readyDeadline = SystemClock.uptimeMillis() + 8000
        while (!ready.get()) {
            assertTrue("Root scene did not draw", SystemClock.uptimeMillis() < readyDeadline)
            SystemClock.sleep(20)
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val session = InstrumentationRegistry.getArguments().getString("dialogMotionSession")
        val recording = session?.let {
            require(it.matches(Regex("[a-zA-Z0-9-]+")))
            automation.executeShellCommand("screenrecord --time-limit 24 /data/local/tmp/coursetable-testing/$it/dialog-motion.mp4")
        }
        try {
            SystemClock.sleep(700)
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                activity.scenario.onActivity { mode.value = theme }
                SystemClock.sleep(500)
                for (kind in listOf(0, 2, 1, 0)) {
                    activity.scenario.onActivity { showing.value = kind }
                    if (kind != 1) {
                        val deadline = SystemClock.uptimeMillis() + 5000
                        while (dismiss.get() == null) {
                            assertTrue("Dialog did not mount", SystemClock.uptimeMillis() < deadline)
                            SystemClock.sleep(20)
                        }
                    }
                    SystemClock.sleep(900)
                    if (kind == 1) {
                        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    } else {
                        assertTrue("Dialog must provide an animated dismiss action", dismiss.get() != null)
                        activity.scenario.onActivity { dismiss.get()!!.invoke() }
                    }
                    val deadline = SystemClock.uptimeMillis() + 3000
                    while (showing.value != null) {
                        assertTrue("Dialog did not finish dismissing", SystemClock.uptimeMillis() < deadline)
                        SystemClock.sleep(20)
                    }
                    SystemClock.sleep(500)
                }
            }
        } finally {
            recording?.let { ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        }
    }
}
