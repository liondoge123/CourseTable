package com.coursetable.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.coursetable.app.CourseApp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.data.WallpaperRepository
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme
import com.coursetable.app.util.WeekUtils
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun WallpaperEditorScreen(settings: AppSettings, courses: List<Course>, onBack: () -> Unit,
    repository: WallpaperRepository = (LocalContext.current.applicationContext as CourseApp).wallpaperRepository) {
    val context = LocalContext.current
    val app = context.applicationContext as CourseApp
    val scope = rememberCoroutineScope()
    var raw by rememberSaveable { mutableStateOf(settings.wallpaper?.encode().orEmpty()) }
    val draft = remember(raw) { WallpaperConfig.decode(raw) }
    var stagedIds by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val image = rememberWallpaperImage(draft, repository)
    val bitmap = image.bitmap
    val viewport = LocalWindowInfo.current.containerSize
    val screenWidthPx = viewport.width.coerceAtLeast(1)
    val screenHeightPx = viewport.height.coerceAtLeast(1)
    val week = WeekUtils.clampWeek(WeekUtils.weekOf(settings.semesterStart, LocalDate.now()), settings.totalWeeks)
    val visible = remember(courses, week, settings.showNonCurrentWeek) {
        TimetableCourseIndex(courses).forWeek(week, settings.showNonCurrentWeek, WeekFilter.ALL)
    }
    DisposableEffect(context) {
        onDispose {
            if (context.activity()?.isChangingConfigurations != true) {
                stagedIds.split(',').filter { it.isNotEmpty() }.forEach { app.discardWallpaperDraft(it, repository) }
            }
        }
    }
    fun update(config: WallpaperConfig) { raw = config.normalized().encode() }
    fun edit(transform: (WallpaperConfig) -> WallpaperConfig) {
        WallpaperConfig.decode(raw)?.let { update(transform(it)) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            var prepared: WallpaperConfig? = null
            var adopted = false
            try {
                // Finish the private-file transaction even if the editor closes, then discard it.
                withContext(NonCancellable) { prepared = repository.prepare(uri) }
                ensureActive()
                val next = checkNotNull(prepared)
                stagedIds.split(',').filter { it.isNotEmpty() }.forEach { app.discardWallpaperDraft(it, repository) }
                stagedIds = next.imageId
                update(next)
                adopted = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "选择图片失败，请重试" }
            finally {
                if (!adopted) prepared?.let { app.discardWallpaperDraft(it.imageId, repository) }
                busy = false
            }
        }
    }
    LiquidScrollablePage(topBar = {
        Box(Modifier.testTag("wallpaper-top-bar")) {
            LiquidPageTopBar("课表壁纸", onBack = { if (!busy) onBack() })
        }
    }) { insets ->
        val direction = LocalLayoutDirection.current
        val padding = insets.pagePadding()
        Column(Modifier.fillMaxSize().testTag("wallpaper-editor")
            // Reserve the fixed header outside the scroll viewport. Content padding
            // alone scrolls away and lets scroll-to actions place controls under it.
            .padding(top = insets.calculateTopPadding())
            .verticalScroll(rememberScrollState()).padding(
                start = padding.calculateStartPadding(direction),
                top = 16.dp,
                end = padding.calculateEndPadding(direction),
                bottom = padding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Lay out the real grid at device size, then scale the entire scene. Crop, typography,
            // card opacity and blur therefore match the full-screen result rather than a mockup.
            Box(Modifier.widthIn(max = 200.dp).fillMaxWidth().align(Alignment.CenterHorizontally).clip(RoundedCornerShape(16.dp))
                .testTag("wallpaper-preview")) {
                Box(Modifier.layout { measurable, constraints ->
                    val factor = constraints.maxWidth.toFloat() / screenWidthPx
                    val placeable = measurable.measure(Constraints.fixed(screenWidthPx, screenHeightPx))
                    layout(constraints.maxWidth, (screenHeightPx * factor).roundToInt()) {
                        placeable.placeWithLayer(0, 0) {
                            scaleX = factor; scaleY = factor; transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                }) {
                    CompositionLocalProvider(LocalWallpaperActive provides (bitmap != null),
                        LocalWallpaperCardOpacity provides (draft?.cardOpacity ?: 1f),
                        LocalTimetableScrollKey provides "wallpaper-preview") {
                        LiquidScrollablePage(wallpaperVisible = bitmap != null, background = { WallpaperBackground(draft, bitmap, it) }) { previewInsets ->
                            TimetableGrid(previewInsets.calculateTopPadding(), previewInsets.calculateBottomPadding(),
                                settings.copy(wallpaper = draft), settings.semesterStart, week, visible, true,
                                LocalDate.now().dayOfWeek.value, {}, { _, _ -> }, null)
                        }
                    }
                    val latestBitmap by rememberUpdatedState(bitmap)
                    if (bitmap != null && draft != null) Box(Modifier.fillMaxSize()
                        .testTag("wallpaper-position")
                        .semantics { contentDescription = "拖动调整壁纸位置，双指缩放" }
                        .pointerInput(draft.imageId, busy) {
                            if (!busy) detectTransformGestures { centroid, pan, zoom, _ ->
                                // Several finger events may arrive before recomposition. Read the
                                // draft state directly so incremental zoom/pan never uses an old frame.
                                val c = WallpaperConfig.decode(raw) ?: return@detectTransformGestures
                                val imageBitmap = latestBitmap ?: return@detectTransformGestures
                                val old = wallpaperPlacement(imageBitmap.width, imageBitmap.height, size.width.toFloat(), size.height.toFloat(), c)
                                val next = c.copy(scale = (c.scale * zoom).coerceIn(1f, 3f))
                                val enlarged = wallpaperPlacement(imageBitmap.width, imageBitmap.height, size.width.toFloat(), size.height.toFloat(), next)
                                val ratio = next.scale / c.scale
                                val origin = centroid - (centroid - old.origin) * ratio + pan
                                update(next.copy(
                                    positionX = if (enlarged.size.width - size.width > .5f) -origin.x / (enlarged.size.width - size.width) else .5f,
                                    positionY = if (enlarged.size.height - size.height > .5f) -origin.y / (enlarged.size.height - size.height) else .5f))
                            }
                        })
                }
            }
            Text(if (draft == null) "未设置壁纸" else "拖动调整位置 · 双指缩放",
                style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = LiquidTheme.colorScheme.error) }
            if (draft != null && !image.loading && bitmap == null) {
                Text("壁纸不可用，请更换图片", color = LiquidTheme.colorScheme.error)
            }
            if (busy) Text("正在处理…", modifier = Modifier.testTag("wallpaper-busy"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy, modifier = Modifier.weight(1f).testTag("wallpaper-pick")) {
                    Text(if (draft == null) "选择图片" else "更换图片")
                }
                TextButton(onClick = { raw = ""; error = null }, enabled = !busy && draft != null,
                    modifier = Modifier.testTag("wallpaper-remove")) { Text("移除") }
            }
            if (draft != null) {
                WallpaperSlider("缩放", draft.scale, 1f..3f, busy, "${(draft.scale * 100).roundToInt()}%") { value -> edit { it.copy(scale = value) } }
                WallpaperSlider("模糊", draft.blurDp, 0f..24f, busy, "${draft.blurDp.roundToInt()}") { value -> edit { it.copy(blurDp = value.roundToInt().toFloat()) } }
                WallpaperSlider("遮罩", draft.mask, 0f..0.8f, busy, "${(draft.mask * 100).roundToInt()}%") { value -> edit { it.copy(mask = value) } }
                WallpaperSlider("课表底板不透明度", draft.gridOpacity, 0f..1f, busy, "${(draft.gridOpacity * 100).roundToInt()}%") { value -> edit { it.copy(gridOpacity = value) } }
                WallpaperSlider("课程卡片不透明度", draft.cardOpacity, 0f..1f, busy, "${(draft.cardOpacity * 100).roundToInt()}%") { value -> edit { it.copy(cardOpacity = value) } }
                TextButton(onClick = { edit { WallpaperConfig(it.imageId) } }, enabled = !busy,
                    modifier = Modifier.testTag("wallpaper-reset")) { Text("重置调整") }
            }
            Button(onClick = {
                scope.launch {
                    busy = true; error = null
                    try { repository.apply(WallpaperConfig.decode(raw)); onBack() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "保存失败，请重试" }
                    finally { busy = false }
                }
            }, enabled = !busy && (draft == null || bitmap != null), modifier = Modifier.fillMaxWidth().testTag("wallpaper-apply")) {
                Text("应用")
            }
        }
    }
}

/** Foundation-only slider with a 48dp target and accessibility progress actions. */
@Composable
private fun WallpaperSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                            busy: Boolean, display: String, onChange: (Float) -> Unit) {
    val colors = LiquidTheme.colorScheme
    val change by rememberUpdatedState(onChange)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label); Text(display, color = colors.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(48.dp).testTag("wallpaper-slider-$label")
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                if (busy) disabled() else setProgress { change(it.coerceIn(range)); true }
            }.pointerInput(range, busy) {
                if (!busy) detectTapGestures { p ->
                    change(range.start + (range.endInclusive - range.start) * ((p.x - 12.dp.toPx()) / max(1f, size.width - 24.dp.toPx())).coerceIn(0f, 1f))
                }
            }.pointerInput(range, busy) {
                if (!busy) detectDragGestures { event, _ ->
                    event.consume()
                    change(range.start + (range.endInclusive - range.start) * ((event.position.x - 12.dp.toPx()) / max(1f, size.width - 24.dp.toPx())).coerceIn(0f, 1f))
                }
            }) {
            val start = Offset(12.dp.toPx(), size.height / 2)
            val end = Offset(size.width - 12.dp.toPx(), start.y)
            val thumb = Offset(start.x + (end.x - start.x) * ((value - range.start) / (range.endInclusive - range.start)), start.y)
            drawLine(colors.outlineVariant, start, end, 4.dp.toPx(), StrokeCap.Round)
            drawLine(colors.primary, start, thumb, 4.dp.toPx(), StrokeCap.Round)
            drawCircle(colors.primary, 10.dp.toPx(), thumb)
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
