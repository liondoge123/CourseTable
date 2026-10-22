package com.coursetable.app.ui.liquid

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/** Retain only the trigger's glyphs, never its glass or pressed indication. */
@Composable
internal fun rememberMenuSource(): GraphicsLayer = rememberGraphicsLayer()

internal fun Modifier.captureMenuSource(source: GraphicsLayer, frozen: Boolean): Modifier =
    drawWithContent {
        if (!frozen) source.record { this@drawWithContent.drawContent() }
        drawLayer(source)
    }
