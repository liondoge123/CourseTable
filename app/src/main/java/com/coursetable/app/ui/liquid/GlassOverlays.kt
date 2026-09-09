package com.coursetable.app.ui.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.theme.LiquidTheme
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
    containerColor: Color = LiquidTheme.colorScheme.glass,
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
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

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
        BackHandler(
            enabled = visibility.currentState || visibility.targetState,
            onBack = { dismissAnimated() }
        )
        CompositionLocalProvider(LocalDialogDismissController provides dismissController) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visibleState = scrimVisibility,
                    enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (LiquidTheme.colorScheme.isDark) 0.38f else 0.20f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { dismissAnimated() }
                            )
                    )
                }
                AnimatedVisibility(
                    visibleState = visibility,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        animationSpec = tween(280),
                        initialOffsetY = { fullHeight -> fullHeight }
                    ) + fadeIn(tween(180)),
                    exit = slideOutVertically(
                        animationSpec = tween(220),
                        targetOffsetY = { fullHeight -> fullHeight }
                    ) + fadeOut(tween(160))
                ) {
                    OverlayGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .navigationBarsPadding()
                            .imePadding()
                            .offset { IntOffset(0, drag.coerceAtLeast(0f).roundToInt()) }
                            .nestedScroll(nestedScrollConnection)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = sheetShape,
                        baseColor = containerColor,
                        shadowElevation = 24.dp
                    ) {
                        Column(Modifier.fillMaxWidth()) {
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

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
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
        BackHandler(
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
                    enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (LiquidTheme.colorScheme.isDark) 0.38f else 0.20f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { dismissAnimated() }
                            )
                    )
                }

                AnimatedVisibility(
                    visibleState = visibility,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    enter = scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(200, easing = LinearOutSlowInEasing)),
                    exit = scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(160, easing = FastOutSlowInEasing))
                ) {
                    OverlayGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .imePadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 24.dp
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (title != null) {
                                CompositionLocalProvider(LocalContentColor provides LiquidTheme.colorScheme.onSurface) { title() }
                            }
                            if (text != null) {
                                CompositionLocalProvider(LocalContentColor provides LiquidTheme.colorScheme.onSurfaceVariant) { text() }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                dismissButton?.invoke()
                                confirmButton()
                            }
                        }
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
        BackHandler(
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
                    enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (LiquidTheme.colorScheme.isDark) 0.38f else 0.20f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { dismissAnimated() }
                            )
                    )
                }

                AnimatedVisibility(
                    visibleState = visibility,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    enter = scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(tween(200, easing = LinearOutSlowInEasing)),
                    exit = scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(160, easing = FastOutSlowInEasing))
                ) {
                    OverlayGlassSurface(
                        modifier = Modifier
                            .width(280.dp)
                            .imePadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = RoundedCornerShape(22.dp),
                        shadowElevation = 24.dp
                    ) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                style = LiquidTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LiquidTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
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

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { dismissAnimated() }) {
                                    Text("取消", color = LiquidTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(6.dp))
                                TextButton(onClick = {
                                    dismissAnimated {
                                        onConfirm(tempValue)
                                        onDismiss()
                                    }
                                }) {
                                    Text("确定", color = LiquidTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
fun DropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    if (!expanded) return
    GlassOverlayPortal(OverlayDestination.MENU) {
        BackHandler(onBack = onDismissRequest)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            OverlayGlassSurface(
                modifier = Modifier
                    .widthIn(min = 152.dp, max = 220.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) { content() }
            }
        }
    }
}

@Composable
fun DropdownMenuItem(text: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minWidth = 140.dp, minHeight = 42.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) { text() }
}
