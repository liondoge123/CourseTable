package com.coursetable.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.coursetable.app.importer.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@Composable
internal fun rememberReviewBitmap(file: File, region: ImportRegion? = null): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, file, region) {
        var loaded: Bitmap? = null
        value = null
        try {
            withContext(Dispatchers.IO) {
                val original = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
                loaded = if (region == null) original else {
                    val x = (region.left * original.width).toInt().coerceIn(0, original.width - 1)
                    val y = (region.top * original.height).toInt().coerceIn(0, original.height - 1)
                    Bitmap.createBitmap(original, x, y, ((region.right - region.left) * original.width).toInt().coerceIn(1, original.width - x), ((region.bottom - region.top) * original.height).toInt().coerceIn(1, original.height - y)).also { if (it !== original) original.recycle() }
                }
            }
            value = loaded
            awaitDispose { }
        } finally { loaded?.recycle() }
    }
    return bitmap
}

/** One-finger selection adjustment and two-finger image movement share a single gesture handler. */
@Composable
internal fun ImportImageViewport(bitmap: Bitmap, modifier: Modifier, selection: ImageSelection? = null, onSelection: ((ImageSelection) -> Unit)? = null, enabled: Boolean = true, resetKey: Int = 0) {
    var zoom by remember(bitmap, resetKey) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap, resetKey) { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var factor by remember { mutableFloatStateOf(1f) }
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelection by rememberUpdatedState(onSelection)
    val accent = LiquidTheme.colorScheme.primary
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier.clipToBounds().background(LiquidTheme.colorScheme.surfaceContainerHigh).pointerInput(bitmap, enabled, resetKey) {
        if (!enabled) return@pointerInput
        val hitRadius = 28.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val initial = currentSelection
            var previous = down.position
            var transforming = false
            val left = initial?.let { origin.x + it.left * bitmap.width * factor } ?: 0f
            val right = initial?.let { origin.x + it.right * bitmap.width * factor } ?: 0f
            val top = initial?.let { origin.y + it.top * bitmap.height * factor } ?: 0f
            val bottom = initial?.let { origin.y + it.bottom * bitmap.height * factor } ?: 0f
            val nearX = down.position.x in (left - hitRadius)..(right + hitRadius)
            val nearY = down.position.y in (top - hitRadius)..(bottom + hitRadius)
            val edgeX = if (nearY && abs(down.position.x - left) < hitRadius) -1 else if (nearY && abs(down.position.x - right) < hitRadius) 1 else 0
            val edgeY = if (nearX && abs(down.position.y - top) < hitRadius) -1 else if (nearX && abs(down.position.y - bottom) < hitRadius) 1 else 0
            val moving = initial != null && down.position.x in left..right && down.position.y in top..bottom
            do {
                val event = awaitPointerEvent()
                val active = event.changes.filter { it.pressed }
                if (active.size > 1) {
                    transforming = true
                    val next = (zoom * event.calculateZoom()).coerceIn(1f, 8f)
                    val ratio = next / zoom
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    pan = (pan + center - centroid) * ratio + centroid - center + event.calculatePan()
                    zoom = next
                    previous = active.first().position
                } else if (active.size == 1) {
                    val point = active.first().position
                    val delta = point - previous
                    if (initial != null && !transforming && (moving || edgeX != 0 || edgeY != 0)) {
                        val s = currentSelection ?: initial
                        val dx = delta.x / (factor * bitmap.width)
                        val dy = delta.y / (factor * bitmap.height)
                        val updated = if (edgeX == 0 && edgeY == 0) {
                            val x = dx.coerceIn(-s.left, 1f - s.right)
                            val y = dy.coerceIn(-s.top, 1f - s.bottom)
                            ImageSelection(s.left + x, s.top + y, s.right + x, s.bottom + y)
                        } else ImageSelection(
                            if (edgeX == -1) (s.left + dx).coerceIn(0f, s.right - .02f) else s.left,
                            if (edgeY == -1) (s.top + dy).coerceIn(0f, s.bottom - .02f) else s.top,
                            if (edgeX == 1) (s.right + dx).coerceIn(s.left + .02f, 1f) else s.right,
                            if (edgeY == 1) (s.bottom + dy).coerceIn(s.top + .02f, 1f) else s.bottom
                        )
                        currentOnSelection?.invoke(updated)
                    } else pan += delta
                    previous = point
                }
                val fit = minOf((size.width - 48.dp.toPx()).coerceAtLeast(1f) / bitmap.width, (size.height - 48.dp.toPx()).coerceAtLeast(1f) / bitmap.height)
                val maxX = ((bitmap.width * fit * zoom - size.width) / 2).coerceAtLeast(0f)
                val maxY = ((bitmap.height * fit * zoom - size.height) / 2).coerceAtLeast(0f)
                pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }) {
        factor = minOf((size.width - 48.dp.toPx()).coerceAtLeast(1f) / bitmap.width, (size.height - 48.dp.toPx()).coerceAtLeast(1f) / bitmap.height) * zoom
        origin = Offset((size.width - bitmap.width * factor) / 2, (size.height - bitmap.height * factor) / 2) + pan
        withTransform({ translate(origin.x, origin.y); scale(factor, factor, Offset.Zero) }) { drawImage(image) }
        selection?.let {
            val a = origin + Offset(it.left * bitmap.width * factor, it.top * bitmap.height * factor)
            val b = origin + Offset(it.right * bitmap.width * factor, it.bottom * bitmap.height * factor)
            val l = a.x.coerceIn(0f, size.width); val r = b.x.coerceIn(l, size.width)
            val t = a.y.coerceIn(0f, size.height); val bottom = b.y.coerceIn(t, size.height)
            val shade = Color.Black.copy(alpha = .48f)
            drawRect(shade, Offset.Zero, Size(size.width, t))
            drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
            drawRect(shade, Offset(0f, t), Size(l, bottom - t))
            drawRect(shade, Offset(r, t), Size(size.width - r, bottom - t))
            drawRect(accent, a, Size(b.x - a.x, b.y - a.y), style = Stroke(2.dp.toPx()))
            for (p in listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y), Offset((a.x + b.x) / 2, a.y), Offset((a.x + b.x) / 2, b.y), Offset(a.x, (a.y + b.y) / 2), Offset(b.x, (a.y + b.y) / 2))) {
                drawCircle(Color.White, 6.dp.toPx(), p)
                drawCircle(accent, 6.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

@Composable
fun ImportImageSelection(image: PreparedImportImage, selection: ImageSelection, onSelection: (ImageSelection) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, busy: Boolean, error: String?) {
    var resetKey by remember { mutableIntStateOf(0) }
    val bitmap = rememberReviewBitmap(image.preview)
    ModalBottomSheet(onDismissRequest = onDismiss, canDismiss = { !busy }) {
        val dismissController = LocalDialogDismissController.current
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f).testTag("import-selection-page")) {
                PageHeader("识别范围", "框住完整课表，请保留表头、星期、节次和周次信息") {
                    TextButton(onClick = { dismissController?.dismiss() ?: onDismiss() }, enabled = !busy) { Text("返回") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    if (bitmap != null && !bitmap.isRecycled) ImportImageViewport(bitmap, Modifier.fillMaxSize().testTag("import-selection-image"), selection, onSelection, !busy, resetKey)
                    else CircularProgressIndicator(Modifier.size(24.dp))
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("拖动边角调整范围，拖动框内移动选区；双指缩放和移动图片", style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { onSelection(ImageSelection()); resetKey++ }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("重置") }
                        Button(onClick = onConfirm, enabled = !busy && bitmap != null, modifier = Modifier.weight(2f)) { Text(if (busy) "正在识别…" else "确认并识别") }
                    }
                }
            }
    }
}
