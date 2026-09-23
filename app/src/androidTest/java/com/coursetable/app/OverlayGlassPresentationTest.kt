package com.coursetable.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.liquid.AlertDialog
import com.coursetable.app.ui.liquid.Button
import com.coursetable.app.ui.liquid.DatePicker
import com.coursetable.app.ui.liquid.DatePickerDialog
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.ModalBottomSheet
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.liquid.TextButton
import com.coursetable.app.ui.liquid.rememberDatePickerState
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.ui.theme.CourseTableTheme
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

class OverlayGlassPresentationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nestedDialogReplacesSheetSemanticsAndUsesItsOwnGlassLayer() {
        compose.setContent {
            CourseTableTheme {
                var showSheet by remember { mutableStateOf(true) }
                var showDialog by remember { mutableStateOf(false) }
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFF30D158))
                                )
                            )
                    )
                    if (showSheet) {
                        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                            Column(Modifier.padding(24.dp)) {
                                Text("玻璃底部弹窗")
                                Button(onClick = { showDialog = true }) { Text("打开嵌套对话框") }
                            }
                        }
                    }
                    if (showDialog) {
                        AlertDialog(
                            onDismissRequest = { showDialog = false },
                            title = { Text("嵌套玻璃对话框") },
                            confirmButton = {
                                Button(onClick = { showDialog = false }) { Text("完成") }
                            }
                        )
                    }
                }
            }
        }

        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("overlay-glass-sheet").assertExists()
        capture("overlay-sheet.png")
        compose.onNodeWithText("打开嵌套对话框").performClick()
        compose.mainClock.advanceTimeBy(400)

        compose.onNodeWithTag("overlay-glass-dialog").assertExists()
        compose.onNodeWithText("嵌套玻璃对话框").assertExists()
        compose.onNodeWithText("玻璃底部弹窗").assertDoesNotExist()
        capture("overlay-nested-dialog.png")
    }

    @Test
    fun capturesRealEditorAndDatePickerGlass() {
        var showEditor by mutableStateOf(true)
        compose.setContent {
            CourseTableTheme {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF7DD3FC), Color(0xFFFDE68A), Color(0xFFC4B5FD))
                                )
                            )
                    )
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, top = 72.dp)
                    ) {
                        repeat(24) { row ->
                            Text(
                                text = "%02d:%02d  第 %d 节课程".format(
                                    8 + row / 2,
                                    (row % 2) * 45,
                                    row + 1
                                ),
                                color = Color(0xFF24262C).copy(alpha = 0.86f)
                            )
                        }
                    }
                    if (showEditor) {
                        CourseEditorDialog(
                            course = Course(
                                name = "通信软件 Java 开发基础",
                                teacher = "单田华",
                                location = "计算机楼103",
                                dayOfWeek = 4,
                                startSection = 5,
                                duration = 2,
                                startWeek = 1,
                                endWeek = 14
                            ),
                            totalWeeks = 18,
                            periodCount = 12,
                            onDismiss = {},
                            onSave = {}
                        )
                    } else {
                        val dateState = rememberDatePickerState(1788134400000L)
                        DatePickerDialog(
                            onDismissRequest = {},
                            confirmButton = { TextButton(onClick = {}) { Text("确定") } },
                            dismissButton = { TextButton(onClick = {}) { Text("取消") } }
                        ) {
                            DatePicker(dateState)
                        }
                    }
                }
            }
        }

        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("overlay-glass-sheet").assertExists()
        capture("course-editor-glass.png")

        compose.runOnIdle { showEditor = false }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("overlay-glass-dialog").assertExists()
        capture("date-picker-glass.png")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "overlay-validation").apply { mkdirs() }
        val bitmap = compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
