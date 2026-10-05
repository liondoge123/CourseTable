package com.coursetable.app.ui.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.coroutineScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class DialogDismissController internal constructor(
    private val onDismiss: (afterAction: (() -> Unit)?) -> Unit
) {
    fun dismiss(afterAction: (() -> Unit)? = null) {
        onDismiss(afterAction)
    }
}

val LocalDialogDismissController = staticCompositionLocalOf<DialogDismissController?> { null }

class LiquidSheetState internal constructor(val skipPartiallyExpanded: Boolean)

@Composable
fun rememberModalBottomSheetState(skipPartiallyExpanded: Boolean = true) =
    remember { LiquidSheetState(skipPartiallyExpanded) }

@Composable
@Suppress("UNUSED_PARAMETER")
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: LiquidSheetState = rememberModalBottomSheetState(),
    containerColor: Color = Color.Unspecified,
    shape: Shape = RoundedRectangle(32.dp),
    canDismiss: () -> Boolean = { true },
    content: @Composable () -> Unit
) {
    var drag by remember { mutableFloatStateOf(0f) }
    var dismissRequested by remember { androidx.compose.runtime.mutableStateOf(false) }
    var pendingDismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val visibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val scrimVisibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val currentCanDismiss by androidx.compose.runtime.rememberUpdatedState(canDismiss)
    fun dismissAnimated(afterAction: (() -> Unit)? = null, checkDismiss: Boolean = true) {
        if (checkDismiss && !currentCanDismiss()) { drag = 0f; return }
        if (!dismissRequested) {
            dismissRequested = true
            pendingDismissAction = afterAction
            visibility.targetState = false
            scrimVisibility.targetState = false
        }
    }

    val dismissController = remember {
        DialogDismissController { afterAction ->
            dismissAnimated(afterAction)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (delta < 0 && drag > 0f) {
                    val newDrag = (drag + delta).coerceAtLeast(0f)
                    val consumed = newDrag - drag
                    drag = newDrag
                    Offset(0f, consumed)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (delta > 0 && source == NestedScrollSource.UserInput) {
                    drag = (drag + delta).coerceAtLeast(0f)
                    Offset(0f, delta)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag > 120f) {
                    dismissAnimated()
                } else {
                    drag = 0f
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(visibility.isIdle, visibility.currentState, dismissRequested) {
        if (dismissRequested && visibility.isIdle && !visibility.currentState) {
            val action = pendingDismissAction
            if (action != null) {
                action.invoke()
            } else {
                onDismissRequest()
            }
        }
    }

    GlassOverlayPortal(OverlayDestination.SHEET) {
        val back = rememberPredictiveBack(
            enabled = visibility.currentState || visibility.targetState,
            canCommit = { currentCanDismiss() },
            onBack = { dismissAnimated(checkDismiss = false) }
        )
        CompositionLocalProvider(LocalDialogDismissController provides dismissController) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visibleState = scrimVisibility,
                    modifier = Modifier.predictiveBackScrim(back),
                    enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                    exit = if (back.completed) ExitTransition.None else fadeOut(tween(180, easing = FastOutSlowInEasing))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(libraryOverlayDimColor())
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { dismissAnimated() }
                            )
                    )
                }
                AnimatedVisibility(
                    visibleState = visibility,
                    modifier = Modifier.align(Alignment.BottomCenter).predictiveBackTransform(back, BackPresentation.SHEET),
                    enter = slideInVertically(
                        animationSpec = tween(280),
                        initialOffsetY = { fullHeight -> fullHeight }
                    ) + fadeIn(tween(180)),
                    exit = if (back.completed) ExitTransition.None else slideOutVertically(
                        animationSpec = tween(220),
                        targetOffsetY = { fullHeight -> fullHeight }
                    ) + fadeOut(tween(160))
                ) {
                    OverlayGlassSurface(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .offset { IntOffset(0, drag.coerceAtLeast(0f).roundToInt()) }
                            .nestedScroll(nestedScrollConnection)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = shape,
                        baseColor = containerColor.takeUnless { it == Color.Unspecified },
                        shadowElevation = 24.dp,
                        style = OverlayGlassStyle.SHEET
                    ) {
                        // Keep the sheet frame anchored when the IME opens. Full-height
                        // editors handle keyboard avoidance inside their own fixed bounds.
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { _, amount -> drag = (drag + amount).coerceAtLeast(0f) },
                                            onDragEnd = { if (drag > 120f) dismissAnimated() else drag = 0f }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .width(38.dp)
                                        .height(5.dp)
                                        .background(LiquidTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), CircleShape)
                                )
                            }
                            content()
                        }
                    }
                }
            }
        }
    }
}

enum class DialogActionRole { Primary, Secondary, Destructive }

data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val role: DialogActionRole = DialogActionRole.Secondary,
    val enabled: Boolean = true
)

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    onBackRequest: (() -> Unit)? = null,
    actionShape: Shape = Capsule(),
    confirmButtonEmphasized: Boolean = true,
    confirmButtonRole: DialogActionRole = if (confirmButtonEmphasized) DialogActionRole.Primary else DialogActionRole.Secondary,
    dismissButtonRole: DialogActionRole = DialogActionRole.Secondary,
    additionalActions: List<DialogAction> = emptyList(),
    fixedEditorFrame: Boolean = false,
    pageTransitionKey: Any? = null
) {
    val page = DialogPagePresentation(
        pageTransitionKey ?: Unit, title, text, confirmButton, dismissButton,
        actionShape, confirmButtonRole, dismissButtonRole, additionalActions, fixedEditorFrame
    )
    var dismissRequested by remember { mutableStateOf(false) }
    var pendingDismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val visibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val scrimVisibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    fun dismissAnimated(afterAction: (() -> Unit)? = null) {
        if (!dismissRequested) {
            dismissRequested = true
            pendingDismissAction = afterAction
            visibility.targetState = false
            scrimVisibility.targetState = false
        }
    }

    val dismissController = remember {
        DialogDismissController { afterAction ->
            dismissAnimated(afterAction)
        }
    }

    LaunchedEffect(visibility.isIdle, visibility.currentState, dismissRequested) {
        if (dismissRequested && visibility.isIdle && !visibility.currentState) {
            val action = pendingDismissAction
            if (action != null) {
                action.invoke()
            } else {
                onDismissRequest()
            }
        }
    }

    GlassOverlayPortal(OverlayDestination.DIALOG) {
        val back = rememberPredictiveBack(
            enabled = visibility.currentState || visibility.targetState,
            canCommit = { onBackRequest == null },
            onRejected = { onBackRequest?.invoke() },
            onBack = { dismissAnimated() }
        )
        CompositionLocalProvider(LocalDialogDismissController provides dismissController) {
            Box(
                Modifier.fillMaxSize().then(if (fixedEditorFrame || pageTransitionKey != null) Modifier else Modifier.imePadding()),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visibleState = scrimVisibility,
                    modifier = Modifier.predictiveBackScrim(back),
                    enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                    exit = if (back.completed) ExitTransition.None else fadeOut(tween(180, easing = FastOutSlowInEasing))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(libraryOverlayDimColor())
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onBackRequest?.invoke() ?: dismissAnimated() }
                            )
                    )
                }

                AnimatedVisibility(
                    visibleState = visibility,
                    // Editors retain the same frame throughout the IME animation.
                    // Ordinary dialogs still fit above the keyboard and below system bars.
                    modifier = (if (fixedEditorFrame || pageTransitionKey != null) Modifier.systemBarsPadding() else Modifier.safeDrawingPadding())
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .predictiveBackTransform(back, BackPresentation.DIALOG),
                    enter = scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(200, easing = LinearOutSlowInEasing)),
                    exit = if (back.completed) ExitTransition.None else scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(160, easing = FastOutSlowInEasing))
                ) {
                    if (pageTransitionKey != null) {
                        DialogTransitionSurface(page)
                    } else {
                        DialogPageSurface(page)
                    }
                }
            }
        }
    }
}

private data class DialogPagePresentation(
    val key: Any,
    val title: (@Composable () -> Unit)?,
    val text: (@Composable () -> Unit)?,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: (@Composable () -> Unit)?,
    val actionShape: Shape,
    val confirmButtonRole: DialogActionRole,
    val dismissButtonRole: DialogActionRole,
    val additionalActions: List<DialogAction>,
    val fixedEditorFrame: Boolean
)

@Composable
private fun DialogTransitionSurface(page: DialogPagePresentation) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val editorHeight = maxHeight * 0.855f
        val targetOffset = if (page.fixedEditorFrame) -maxHeight * 0.0225f else 0.dp
        val targetOffsetPx = with(density) { targetOffset.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val cardWidth = minOf(maxWidth, 480.dp)
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val radiusPx = with(density) { 32.dp.toPx() }
        var listHeightPx by remember { mutableIntStateOf(0) }
        var measuredHeightPx by remember { mutableIntStateOf(0) }
        val visualHeight = remember { Animatable(0f) }
        val visualOffset = remember { Animatable(0f) }
        LaunchedEffect(measuredHeightPx, targetOffsetPx) {
            if (measuredHeightPx == 0) return@LaunchedEffect
            if (visualHeight.value == 0f) {
                visualHeight.snapTo(measuredHeightPx.toFloat())
                visualOffset.snapTo(targetOffsetPx)
            } else coroutineScope {
                launch { visualHeight.animateTo(measuredHeightPx.toFloat(), tween(300, easing = FastOutSlowInEasing)) }
                launch { visualOffset.animateTo(targetOffsetPx, tween(300, easing = FastOutSlowInEasing)) }
            }
        }
        // Sample glass in fixed screen coordinates and reveal it with a moving outline.
        // Only placement and clipping animate; neither pixels nor content are stretched.
        val glassShape = remember(viewportWidthPx, viewportHeightPx, cardWidthPx, radiusPx) {
            object : Shape {
                override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
                    val height = visualHeight.value.coerceAtLeast(1f)
                    val left = (viewportWidthPx - cardWidthPx) / 2f
                    val top = (viewportHeightPx - height) / 2f + visualOffset.value
                    return Outline.Rounded(RoundRect(left, top, left + cardWidthPx, top + height, CornerRadius(radiusPx)))
                }
            }
        }
        val contentShape = remember(radiusPx) {
            object : Shape {
                override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline =
                    Outline.Rounded(RoundRect(0f, 0f, size.width, visualHeight.value.coerceAtLeast(1f), CornerRadius(radiusPx)))
            }
        }
        OverlayGlassSurface(
            modifier = Modifier.fillMaxSize().clip(glassShape)
                .border(BorderStroke(0.8.dp, LiquidTheme.colorScheme.glassBorder), glassShape),
            shape = RectangleShape,
            shadowElevation = 0.dp,
            style = OverlayGlassStyle.DIALOG,
            refractionEnabled = false
        ) { }
        AnimatedContent(
            modifier = Modifier.align(Alignment.TopCenter).width(cardWidth)
                .then(when {
                    page.fixedEditorFrame -> Modifier.height(editorHeight)
                    listHeightPx > 0 -> Modifier.height(with(density) { listHeightPx.toDp() })
                    else -> Modifier
                })
                .onSizeChanged { measuredHeightPx = it.height }
                .offset { IntOffset(0, ((viewportHeightPx - visualHeight.value) / 2f + visualOffset.value).roundToInt()) }
                .clip(contentShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            targetState = page,
            contentKey = { it.key },
            transitionSpec = {
                (fadeIn(tween(300, easing = FastOutSlowInEasing))
                    togetherWith fadeOut(tween(300, easing = FastOutSlowInEasing)))
                    .using(null)
            },
            contentAlignment = Alignment.TopCenter,
            label = "DialogPageTransition"
        ) { presentedPage ->
            DialogPageBody(presentedPage, if (presentedPage.fixedEditorFrame) Modifier.fillMaxWidth().height(editorHeight)
                else Modifier.wrapContentHeight(unbounded = true).heightIn(max = maxHeight)
                    .onSizeChanged { listHeightPx = it.height })
        }
    }
}
@Composable
private fun DialogPageSurface(page: DialogPagePresentation) {
    with(page) {
        // Reserve the previous frame height to preserve its top position,
        // while shortening only the visible card at the bottom.
        BoxWithConstraints(
            if (fixedEditorFrame) Modifier.fillMaxHeight(0.9f) else Modifier
        ) {
            OverlayGlassSurface(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .then(if (fixedEditorFrame) Modifier.height(maxHeight * 0.95f) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedRectangle(32.dp),
                shadowElevation = 0.dp,
                style = OverlayGlassStyle.DIALOG,
                // Large editing frames retain frosted glass without the extra
                // full-surface lens pass during keyboard/layout updates.
                refractionEnabled = !fixedEditorFrame
            ) {
                DialogPageBody(page, if (fixedEditorFrame) Modifier.fillMaxSize() else Modifier)
            }
        }
    }
}

@Composable
private fun DialogPageBody(page: DialogPagePresentation, modifier: Modifier = Modifier) {
    with(page) {
        Column(
            // Insets must be relative to this floating frame, not the
            // screen bottom, or its outer margin is counted a second time.
            modifier.then(if (fixedEditorFrame) Modifier.recalculateWindowInsets().imePadding() else Modifier)
        ) {
            if (title != null) {
                CompositionLocalProvider(LocalContentColor provides LiquidTheme.colorScheme.onSurface) {
                    Box(
                        if (fixedEditorFrame) Modifier.padding(start = 12.dp, top = 12.dp, end = 24.dp, bottom = 4.dp)
                        else Modifier.padding(start = 28.dp, top = 24.dp, end = 28.dp, bottom = 12.dp)
                    ) {
                        title()
                    }
                }
            }
            if (text != null) {
                CompositionLocalProvider(LocalContentColor provides LiquidTheme.colorScheme.onSurfaceVariant) {
                    Box(Modifier.weight(1f, fill = fixedEditorFrame)
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp, bottom = if (fixedEditorFrame) 8.dp else 12.dp)) {
                        text()
                    }
                }
            }
            LibraryDialogActions(dismissButton, confirmButton, actionShape, confirmButtonRole, dismissButtonRole, additionalActions,
                topPadding = if (fixedEditorFrame) 8.dp else 12.dp)
        }
    }
}

@Composable
internal fun GlassProgressDialog(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    GlassOverlayPortal(OverlayDestination.DIALOG) {
        BackHandler(enabled = LocalBackLayerActive.current && LocalOverlayBackActive.current && !rememberImeVisible(), onBack = {})
        Box(
            Modifier
                .fillMaxSize()
                .background(libraryOverlayDimColor())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            OverlayGlassSurface(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .then(modifier),
                shape = RoundedRectangle(28.dp),
                shadowElevation = 0.dp,
                style = OverlayGlassStyle.DIALOG
            ) {
                Row(
                    Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = LiquidTheme.typography.titleMedium)
                        Text(
                            message,
                            style = LiquidTheme.typography.bodySmall,
                            color = LiquidTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

class DatePickerState internal constructor(initial: Long?) {
    var selectedDateMillis by mutableLongStateOf(initial ?: System.currentTimeMillis())
}

@Composable
fun rememberDatePickerState(initialSelectedDateMillis: Long? = null) =
    remember { DatePickerState(initialSelectedDateMillis) }

@Composable
fun DatePicker(state: DatePickerState) {
    val date = Instant.ofEpochMilli(state.selectedDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    var displayedMonth by remember { mutableStateOf(YearMonth.from(date)) }
    val colors = LiquidTheme.colorScheme
    val today = LocalDate.now()
    val firstDayOffset = displayedMonth.atDay(1).dayOfWeek.value - 1
    val daysInMonth = displayedMonth.lengthOfMonth()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "已选择  ${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日",
            color = colors.primary,
            style = LiquidTheme.typography.titleMedium
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月", Modifier.size(22.dp))
            }
            Text(
                "${displayedMonth.year} 年 ${displayedMonth.monthValue} 月",
                style = LiquidTheme.typography.titleMedium
            )
            IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月", Modifier.size(22.dp))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                Text(
                    weekday,
                    modifier = Modifier.weight(1f),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = LiquidTheme.typography.labelMedium
                )
            }
        }
        repeat(6) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = row * 7 + column - firstDayOffset + 1
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day in 1..daysInMonth) {
                            val cellDate = displayedMonth.atDay(day)
                            val selected = cellDate == date
                            val isToday = cellDate == today
                            Surface(
                                onClick = {
                                    state.selectedDateMillis = cellDate.atStartOfDay()
                                        .toInstant(ZoneOffset.UTC).toEpochMilli()
                                },
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                color = if (selected) colors.primary else Color.Transparent,
                                contentColor = if (selected) colors.onPrimary else colors.onSurface,
                                border = if (isToday && !selected) BorderStroke(1.dp, colors.primary) else null
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        day.toString(),
                                        fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        style = LiquidTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest,
        title = { Text("选择日期", style = LiquidTheme.typography.titleLarge) },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

class TimePickerState internal constructor(hour: Int, minute: Int) {
    var hour by mutableIntStateOf(hour)
    var minute by mutableIntStateOf(minute)
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun rememberTimePickerState(initialHour: Int, initialMinute: Int, is24Hour: Boolean = true) =
    remember { TimePickerState(initialHour, initialMinute) }

@Composable
fun TimePicker(state: TimePickerState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelColumn(
            values = (0..23).toList(),
            selected = state.hour,
            contentDescription = "小时",
            onSelected = { state.hour = it }
        )
        Text(":", modifier = Modifier.padding(horizontal = 12.dp), style = LiquidTheme.typography.headlineSmall)
        WheelColumn(
            values = (0..59).toList(),
            selected = state.minute,
            contentDescription = "分钟",
            onSelected = { state.minute = it }
        )
    }
}

@Composable
fun WheelColumn(
    values: List<Int>,
    selected: Int,
    contentDescription: String = "",
    width: androidx.compose.ui.unit.Dp = 100.dp,
    formatter: (Int) -> String = { String.format(Locale.ROOT, "%02d", it) },
    onSelected: (Int) -> Unit
) {
    val itemHeight = 44.dp
    val initialIndex = values.indexOf(selected).let { if (it >= 0) it else 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    fun centeredIndex(): Int? {
        val layout = listState.layoutInfo
        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        return layout.visibleItemsInfo.minByOrNull { item ->
            kotlin.math.abs(item.offset + item.size / 2 - viewportCenter)
        }?.index
    }

    LaunchedEffect(listState) {
        snapshotFlow { centeredIndex() }.collectLatest { index ->
            if (index != null && index in values.indices) onSelected(values[index])
        }
    }

    Box(Modifier.width(width).height(itemHeight * 5), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(LiquidTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * 2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(values) { index, value ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        formatter(value),
                        color = if (value == selected) LiquidTheme.colorScheme.primary
                        else LiquidTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        style = if (value == selected) LiquidTheme.typography.headlineSmall
                        else LiquidTheme.typography.bodyLarge
                    )
                }
            }
        }
        if (contentDescription.isNotBlank()) {
            Text(
                contentDescription,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                color = LiquidTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                style = LiquidTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun NumberWheelPickerDialog(
    title: String,
    range: IntRange,
    initialValue: Int,
    formatter: (Int) -> String = { it.toString() },
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tempValue by remember(initialValue) { mutableIntStateOf(initialValue) }
    var dismissRequested by remember { mutableStateOf(false) }
    var pendingDismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val visibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val scrimVisibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    fun dismissAnimated(afterAction: (() -> Unit)? = null) {
        if (!dismissRequested) {
            dismissRequested = true
            pendingDismissAction = afterAction
            visibility.targetState = false
            scrimVisibility.targetState = false
        }
    }

    val dismissController = remember {
        DialogDismissController { afterAction ->
            dismissAnimated(afterAction)
        }
    }

    LaunchedEffect(visibility.isIdle, visibility.currentState, dismissRequested) {
        if (dismissRequested && visibility.isIdle && !visibility.currentState) {
            val action = pendingDismissAction
            if (action != null) {
                action.invoke()
            } else {
                onDismiss()
            }
        }
    }

    GlassOverlayPortal(OverlayDestination.DIALOG) {
        val back = rememberPredictiveBack(
            enabled = visibility.currentState || visibility.targetState,
            onBack = { dismissAnimated() }
        )
        CompositionLocalProvider(LocalDialogDismissController provides dismissController) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visibleState = scrimVisibility,
                    modifier = Modifier.predictiveBackScrim(back),
                    enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                    exit = if (back.completed) ExitTransition.None else fadeOut(tween(180, easing = FastOutSlowInEasing))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(libraryOverlayDimColor())
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { dismissAnimated() }
                            )
                    )
                }

                AnimatedVisibility(
                    visibleState = visibility,
                    modifier = Modifier.padding(horizontal = 24.dp).predictiveBackTransform(back, BackPresentation.DIALOG),
                    enter = scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(200, easing = LinearOutSlowInEasing)),
                    exit = if (back.completed) ExitTransition.None else scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(160, easing = FastOutSlowInEasing))
                ) {
                    OverlayGlassSurface(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .imePadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = RoundedRectangle(32.dp),
                        shadowElevation = 0.dp,
                        style = OverlayGlassStyle.DIALOG
                    ) {
                        Column {
                            Text(
                                text = title,
                                style = LiquidTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LiquidTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(
                                    start = 28.dp,
                                    top = 24.dp,
                                    end = 28.dp,
                                    bottom = 12.dp
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                WheelColumn(
                                    values = range.toList(),
                                    selected = tempValue,
                                    width = 130.dp,
                                    formatter = formatter,
                                    onSelected = { tempValue = it }
                                )
                            }

                            LibraryDialogActions(
                                dismissButton = {
                                    TextButton(onClick = { dismissAnimated() }) {
                                        Text("取消")
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        dismissAnimated {
                                            onConfirm(tempValue)
                                            onDismiss()
                                        }
                                    }) {
                                        Text("确定", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

internal enum class DropdownMenuAlignment { START, END }

internal fun calculateAnchoredMenuPosition(
    rootSize: IntSize,
    menuSize: IntSize,
    anchorBounds: Rect,
    alignment: DropdownMenuAlignment,
    safeMarginPx: Int,
    gapPx: Int
): IntOffset {
    val maxX = (rootSize.width - safeMarginPx - menuSize.width).coerceAtLeast(safeMarginPx)
    val desiredX = when (alignment) {
        DropdownMenuAlignment.START -> anchorBounds.left.roundToInt()
        DropdownMenuAlignment.END -> anchorBounds.right.roundToInt() - menuSize.width
    }
    val x = desiredX.coerceIn(safeMarginPx, maxX)

    val belowY = anchorBounds.bottom.roundToInt() + gapPx
    val aboveY = anchorBounds.top.roundToInt() - gapPx - menuSize.height
    val maxY = (rootSize.height - safeMarginPx - menuSize.height).coerceAtLeast(safeMarginPx)
    val y = when {
        belowY + menuSize.height <= rootSize.height - safeMarginPx -> belowY
        aboveY >= safeMarginPx -> aboveY
        rootSize.height - anchorBounds.bottom >= anchorBounds.top -> belowY.coerceIn(safeMarginPx, maxY)
        else -> aboveY.coerceIn(safeMarginPx, maxY)
    }
    return IntOffset(x, y)
}

@Composable
internal fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect?,
    alignment: DropdownMenuAlignment = DropdownMenuAlignment.START,
    modifier: Modifier = Modifier,
    onClosed: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    var wasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) wasExpanded = true
        visibility.targetState = expanded
    }
    LaunchedEffect(visibility.currentState, visibility.isIdle, expanded) {
        if (wasExpanded && !expanded && visibility.isIdle && !visibility.currentState) {
            wasExpanded = false
            onClosed()
        }
    }
    val mounted = expanded || visibility.currentState || !visibility.isIdle
    val anchor = anchorBounds
    if (!mounted || anchor == null) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val safeMarginPx = with(density) { 12.dp.roundToPx() }
    val gapPx = with(density) { 8.dp.roundToPx() }
    val rootHeightPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val opensDown = anchor.center.y <= rootHeightPx / 2f
    val transformOrigin = TransformOrigin(
        pivotFractionX = if (alignment == DropdownMenuAlignment.START) 0f else 1f,
        pivotFractionY = if (opensDown) 0f else 1f
    )
    GlassOverlayPortal(OverlayDestination.MENU) {
        val back = rememberPredictiveBack(
            enabled = visibility.currentState || visibility.targetState,
            onBack = onDismissRequest
        )
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        ) {
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    AnimatedVisibility(
                        visibleState = visibility,
                        modifier = Modifier.predictiveBackTransform(back, BackPresentation.MENU, transformOrigin),
                        enter = scaleIn(
                            initialScale = 0.94f,
                            transformOrigin = transformOrigin,
                            animationSpec = tween(190, easing = LinearOutSlowInEasing)
                        ) + fadeIn(tween(130, easing = LinearOutSlowInEasing)),
                        exit = if (back.completed) ExitTransition.None else scaleOut(
                            targetScale = 0.96f,
                            transformOrigin = transformOrigin,
                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(120, easing = FastOutSlowInEasing))
                    ) {
                        OverlayGlassSurface(
                            modifier = modifier
                                .widthIn(min = 152.dp, max = 184.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                ),
                            shape = RoundedRectangle(24.dp),
                            shadowElevation = 0.dp,
                            style = OverlayGlassStyle.MENU
                        ) {
                            Column(
                                Modifier
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp)
                            ) { content() }
                        }
                    }
                }
            ) { measurables, constraints ->
                val rootSize = IntSize(constraints.maxWidth, constraints.maxHeight)
                val placeable = measurables.singleOrNull()?.measure(
                    constraints.copy(minWidth = 0, minHeight = 0)
                )
                layout(rootSize.width, rootSize.height) {
                    if (placeable != null) {
                        val position = calculateAnchoredMenuPosition(
                            rootSize = rootSize,
                            menuSize = IntSize(placeable.width, placeable.height),
                            anchorBounds = anchor,
                            alignment = alignment,
                            safeMarginPx = safeMarginPx,
                            gapPx = gapPx
                        )
                        placeable.place(position.x, position.y)
                    }
                }
            }
        }
    }
}

@Composable
internal fun libraryOverlayDimColor(): Color = if (LiquidTheme.colorScheme.isDark) {
    Color(0xFF121212).copy(alpha = 0.28f)
} else {
    Color(0xFF29293A).copy(alpha = 0.10f)
}

@Composable
private fun LibraryDialogActions(
    dismissButton: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    actionShape: Shape = Capsule(),
    confirmButtonRole: DialogActionRole = DialogActionRole.Primary,
    dismissButtonRole: DialogActionRole = DialogActionRole.Secondary,
    additionalActions: List<DialogAction> = emptyList(),
    topPadding: androidx.compose.ui.unit.Dp = 12.dp
) {
    val padding = Modifier.padding(start = 24.dp, top = topPadding, end = 24.dp, bottom = 24.dp).fillMaxWidth()
    if (additionalActions.isNotEmpty()) {
        Column(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryDialogAction(Modifier.fillMaxWidth(), actionShape, confirmButtonRole, confirmButton)
            additionalActions.forEach { action ->
                LibraryDialogAction(Modifier.fillMaxWidth(), actionShape, action.role) {
                    TextButton(onClick = action.onClick, enabled = action.enabled) { Text(action.label) }
                }
            }
            if (dismissButton != null) LibraryDialogAction(Modifier.fillMaxWidth(), actionShape, dismissButtonRole, dismissButton)
        }
    } else {
        Row(padding, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dismissButton != null) LibraryDialogAction(Modifier.weight(1f), actionShape, dismissButtonRole, dismissButton)
            LibraryDialogAction(Modifier.weight(1f), actionShape, confirmButtonRole, confirmButton)
        }
    }
}

@Composable
private fun LibraryDialogAction(
    modifier: Modifier,
    shape: Shape,
    role: DialogActionRole,
    content: @Composable () -> Unit
) {
    val colors = LiquidTheme.colorScheme
    val background = when (role) {
        DialogActionRole.Primary -> colors.primary
        DialogActionRole.Secondary -> colors.onSurface.copy(alpha = if (colors.isDark) 0.10f else 0.06f)
        DialogActionRole.Destructive -> colors.error.copy(alpha = 0.12f)
    }
    val foreground = when (role) {
        DialogActionRole.Primary -> colors.onPrimary
        DialogActionRole.Secondary -> colors.onSurface
        DialogActionRole.Destructive -> colors.error
    }
    Box(
        modifier.clip(shape).background(background)
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalLibraryDialogAction provides true,
            LocalLibraryDialogActionColor provides foreground,
            LocalLibraryDialogActionShape provides shape,
            content = content
        )
    }
}

@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    val colors = LiquidTheme.colorScheme
    val contentColor = if (selected) colors.primary else colors.onSurface
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .clip(RoundedRectangle(20.dp))
                .background(
                    if (selected) colors.primary.copy(alpha = if (colors.isDark) 0.20f else 0.12f)
                    else Color.Transparent
                )
                .semantics { this.selected = selected }
                .defaultMinSize(minWidth = 140.dp, minHeight = 44.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            text()
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "当前课表",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(20.dp),
                    tint = colors.primary
                )
            }
        }
    }
}
