package com.coursetable.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursetable.app.CourseApp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.WallpaperConfig
import com.coursetable.app.data.WallpaperRepository
import com.coursetable.app.data.WallpaperTextMode
import com.coursetable.app.ui.icons.Icons
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
import kotlin.math.abs
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
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var textAdjustments by rememberSaveable { mutableStateOf(false) }
    var activeSlider by remember { mutableStateOf<String?>(null) }
    val sliderDragging = activeSlider != null
    val image = rememberWallpaperImage(draft, repository)
    val bitmap = image.bitmap
    val axisStyle = rememberWallpaperAxisStyle(draft, bitmap)
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
    CompositionLocalProvider(LocalWallpaperActive provides (bitmap != null),
        LocalWallpaperCardOpacity provides (draft?.cardOpacity ?: 1f),
        LocalTimetableScrollKey provides "wallpaper-preview") {
        LiquidScrollablePage(
            modifier = Modifier.testTag("wallpaper-editor"),
            floatingTopBar = true,
            wallpaperVisible = bitmap != null,
            background = { WallpaperBackground(draft, bitmap, it) },
            topBar = {
                Box(Modifier.testTag("wallpaper-top-bar")) {
                    LiquidPageTopBar("课表壁纸", onBack = { if (!busy) onBack() }) {
                        TextButton(onClick = {
                            scope.launch {
                                busy = true; error = null
                                try { repository.apply(WallpaperConfig.decode(raw)); onBack() }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message ?: "保存失败，请重试" }
                                finally { busy = false }
                            }
                        }, enabled = !busy && (draft == null || bitmap != null),
                            modifier = Modifier.testTag("wallpaper-apply")) { Text("应用") }
                    }
                }
            }
        ) { insets ->
            Box(Modifier.fillMaxSize().testTag("wallpaper-preview")) {
                // Render at the real window size: wallpaper crop, blur and the course grid match
                // the applied result. Editor controls float above this scene without scrolling.
                TimetableGrid(insets.calculateTopPadding(), insets.calculateBottomPadding(),
                    settings.copy(wallpaper = draft), settings.semesterStart, week, visible, true,
                    LocalDate.now().dayOfWeek.value, {}, { _, _ -> }, null, axisStyle = axisStyle)
                val latestBitmap by rememberUpdatedState(bitmap)
                if (bitmap != null && draft != null) Box(Modifier.fillMaxSize()
                    .testTag("wallpaper-position")
                    .semantics { contentDescription = "拖动调整壁纸位置，双指缩放" }
                    .pointerInput(draft.imageId, busy) {
                        if (!busy) detectTransformGestures { centroid, pan, zoom, _ ->
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
                GlassSurface(Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = insets.calculateBottomPadding()).padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth().testTag("wallpaper-toolbar")
                    .graphicsLayer { alpha = if (sliderDragging) 0f else 1f }
                    .pointerInput(Unit) { detectTapGestures {} }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (draft == null) "选择图片，预览课表壁纸" else "拖动调整位置 · 双指缩放",
                            style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
                        error?.let { Text(it, color = LiquidTheme.colorScheme.error) }
                        if (draft != null && !image.loading && bitmap == null) {
                            Text("壁纸不可用，请更换图片", color = LiquidTheme.colorScheme.error)
                        }
                        if (busy) Text("正在处理…", modifier = Modifier.testTag("wallpaper-busy"))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, enabled = !busy, modifier = Modifier.weight(1f).testTag("wallpaper-pick")) {
                                Text(if (draft == null) "选择图片" else "更换图片")
                            }
                            Button(onClick = { adjusting = true }, enabled = !busy && draft != null,
                                modifier = Modifier.weight(1f).testTag("wallpaper-adjust")) { Text("调整") }
                        }
                    }
                }
            }
        }
    }
    if (adjusting && draft != null) {
        ModalBottomSheet(onDismissRequest = { adjusting = false }, canDismiss = { !busy && !sliderDragging },
            preferReadability = true, scrimColor = Color.Transparent, previewingContent = sliderDragging) {
            val dismiss = LocalDialogDismissController.current
            Column(Modifier.fillMaxWidth().testTag("wallpaper-adjustments")
                .semantics(mergeDescendants = true) { isTraversalGroup = true }) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 4.dp)
                    .graphicsLayer { alpha = if (sliderDragging) 0f else 1f },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("调整壁纸", modifier = Modifier.weight(1f),
                        style = LiquidTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { dismiss?.dismiss() ?: run { adjusting = false } },
                        modifier = Modifier.testTag("wallpaper-adjustments-done")) { Text("完成") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .graphicsLayer { alpha = if (sliderDragging) 0f else 1f },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "壁纸效果", true to "文字清晰度").forEach { (isText, label) ->
                        Box(Modifier.weight(1f).heightIn(min = 40.dp)
                            .background(if (textAdjustments == isText) LiquidTheme.colorScheme.primaryContainer else Color.Transparent,
                                RoundedCornerShape(12.dp))
                            .testTag(if (isText) "wallpaper-text-tab" else "wallpaper-image-tab")
                            .selectable(selected = textAdjustments == isText, enabled = !busy, role = Role.Tab,
                                onClick = { textAdjustments = isText }).padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = LiquidTheme.typography.bodyMedium,
                                color = if (textAdjustments == isText) LiquidTheme.colorScheme.onPrimaryContainer else LiquidTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (textAdjustments) {
                    SettingsChoiceItem(
                        id = "wallpaper-text-mode", icon = Icons.Filled.FormatAlignLeft,
                        title = "课表文字", selectedKey = draft.textMode.key,
                        choices = WallpaperTextMode.entries.map { SettingsChoice(it.key, it.label) },
                        onSelect = { key -> if (!busy) edit { it.copy(textMode = WallpaperTextMode.fromKey(key)) } }
                    )
                    Row(Modifier.fillMaxWidth().testTag("wallpaper-text-clarity")
                        .toggleable(value = draft.textClarity, enabled = !busy, role = Role.Switch,
                            onValueChange = { value -> edit { it.copy(textClarity = value) } })
                        .padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("文字清晰增强", style = LiquidTheme.typography.bodyLarge)
                            Text("为日期栏和时间轴补足底色", style = LiquidTheme.typography.bodySmall,
                                color = LiquidTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = draft.textClarity, onCheckedChange = null)
                    }
                    Text("自动模式结合壁纸、遮罩和课表底板选择字色。这里的设置仅影响日期、节次和时间。",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!textAdjustments) {
                        WallpaperSlider("缩放", draft.scale, 1f..3f, busy, "${(draft.scale * 100).roundToInt()}%", activeSlider = activeSlider, onDraggingChange = { activeSlider = if (it) "缩放" else null }) { value -> edit { it.copy(scale = value) } }
                        WallpaperSlider("模糊", draft.blurDp, 0f..24f, busy, "${draft.blurDp.roundToInt()}", activeSlider = activeSlider, onDraggingChange = { activeSlider = if (it) "模糊" else null }) { value -> edit { it.copy(blurDp = value.roundToInt().toFloat()) } }
                        WallpaperSlider("遮罩", draft.mask, 0f..0.8f, busy, "${(draft.mask * 100).roundToInt()}%", activeSlider = activeSlider, onDraggingChange = { activeSlider = if (it) "遮罩" else null }) { value -> edit { it.copy(mask = value) } }
                        WallpaperSlider("课表底板不透明度", draft.gridOpacity, 0f..1f, busy, "${(draft.gridOpacity * 100).roundToInt()}%", activeSlider = activeSlider, onDraggingChange = { activeSlider = if (it) "课表底板不透明度" else null }) { value -> edit { it.copy(gridOpacity = value) } }
                        WallpaperSlider("课程卡片不透明度", draft.cardOpacity, 0f..1f, busy, "${(draft.cardOpacity * 100).roundToInt()}%", activeSlider = activeSlider, onDraggingChange = { activeSlider = if (it) "课程卡片不透明度" else null }) { value -> edit { it.copy(cardOpacity = value) } }
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        .graphicsLayer { alpha = if (sliderDragging) 0f else 1f },
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { edit { WallpaperConfig(it.imageId) } }, enabled = !busy,
                            modifier = Modifier.testTag("wallpaper-reset")) { Text("重置调整") }
                        TextButton(onClick = {
                            dismiss?.dismiss { raw = ""; error = null; adjusting = false }
                                ?: run { raw = ""; error = null; adjusting = false }
                        }, enabled = !busy, modifier = Modifier.testTag("wallpaper-remove")) { Text("移除壁纸") }
                    }
                }
            }
        }
    }
}
/** Foundation-only slider with a 48dp target and accessibility progress actions. */
@Composable
private fun WallpaperSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                            busy: Boolean, display: String, activeSlider: String?, onDraggingChange: (Boolean) -> Unit,
                            onChange: (Float) -> Unit) {
    val colors = LiquidTheme.colorScheme
    val change by rememberUpdatedState(onChange)
    val draggingChange by rememberUpdatedState(onDraggingChange)
    val active = activeSlider == label
    val visible = activeSlider == null || active
    val labelModifier = Modifier
        .background(if (active) colors.surface.copy(alpha = .9f) else Color.Transparent, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 2.dp)
    // Keep every row mounted so the active thumb does not move or lose its gesture.
    Column(Modifier.testTag("wallpaper-adjustment-$label")
        .semantics(mergeDescendants = true) {}
        .graphicsLayer { alpha = if (visible) 1f else 0f }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = labelModifier)
            Text(display, modifier = labelModifier, color = colors.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(48.dp).testTag("wallpaper-slider-$label")
            .semantics(mergeDescendants = true) {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                if (busy) disabled() else setProgress { change(it.coerceIn(range)); true }
            }.pointerInput(range, busy) {
                if (busy) return@pointerInput
                fun seek(x: Float) {
                    change(range.start + (range.endInclusive - range.start) *
                        ((x - 12.dp.toPx()) / max(1f, size.width - 24.dp.toPx())).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    // A touch or short swipe must never seek. Ignore sideways finger wobble
                    // during vertical gestures, and require deliberate horizontal travel.
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size != 1) return@awaitEachGesture
                        val pointer = event.changes.first()
                        if (pointer.id != down.id || pointer.isConsumed || !pointer.pressed) return@awaitEachGesture
                        val travel = pointer.position - down.position
                        if (abs(travel.y) > slop) return@awaitEachGesture
                        if (abs(travel.x) > slop * 2 && abs(travel.x) > abs(travel.y) * 2) {
                            pointer.consume()
                            try {
                                draggingChange(true)
                                seek(pointer.position.x)
                                horizontalDrag(down.id) {
                                    it.consume()
                                    seek(it.position.x)
                                }
                            } finally {
                                draggingChange(false)
                            }
                            return@awaitEachGesture
                        }
                        // The parent can claim a scroll later in this same event dispatch.
                        awaitPointerEvent(PointerEventPass.Final)
                        if (pointer.isConsumed) return@awaitEachGesture
                    }
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
