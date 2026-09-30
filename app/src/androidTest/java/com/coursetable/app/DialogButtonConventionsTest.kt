package com.coursetable.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseTableTheme
import com.coursetable.app.ui.theme.ThemeColor
import com.coursetable.app.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DialogButtonConventionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeActionsHaveSeparateEqualSizedHitAreas() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            CourseTableTheme(themeColor = ThemeColor.GREEN, themeMode = ThemeMode.LIGHT) {
                LiquidBackdropHost(Modifier.fillMaxSize()) {
                    AlertDialog(
                        onDismissRequest = {}, title = { Text("识别失败") },
                        confirmButton = { TextButton(onClick = { clicks += "重试" }) { Text("重试") } },
                        additionalActions = listOf(
                            DialogAction("调整范围", { clicks += "调整范围" }),
                            DialogAction("换图", { clicks += "换图" })
                        )
                    )
                }
            }
        }
        val labels = listOf("重试", "调整范围", "换图")
        val bounds = labels.map {
            compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        assertEquals(bounds[0].right - bounds[0].left, bounds[1].right - bounds[1].left)
        assertEquals(bounds[0].bottom - bounds[0].top, bounds[1].bottom - bounds[1].top)
        assertEquals(bounds[0].bottom - bounds[0].top, bounds[2].bottom - bounds[2].top)
        check(bounds[0].bottom < bounds[1].top && bounds[1].bottom < bounds[2].top)
        val pixels = compose.onNodeWithText("重试").captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, 4]
        val expected = ThemeColor.GREEN.lightScheme.primary
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
        labels.forEach { compose.onNodeWithText(it).performClick() }
        compose.runOnIdle { assertEquals(labels, clicks) }
    }
}
