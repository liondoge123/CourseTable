package com.coursetable.app.ui.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.shapes.Capsule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal val LocalContentColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LiquidTheme.typography.bodyMedium
) {
    val resolved = if (color != Color.Unspecified) color else LocalContentColor.current.takeIf { it != Color.Unspecified }
        ?: LiquidTheme.colorScheme.onSurface
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            color = resolved,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeight = lineHeight
        ),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout
    )
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current.takeIf { it != Color.Unspecified } ?: LiquidTheme.colorScheme.onSurface
) {
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
    )
}

private fun Modifier.surfaceStyle(shape: Shape, color: Color, border: BorderStroke?, shadowElevation: Dp): Modifier {
    var result = this
    if (shadowElevation > 0.dp) result = result.shadow(shadowElevation, shape, clip = false)
    result = result.clip(shape).background(color)
    if (border != null) result = result.border(border, shape)
    return result
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = LiquidTheme.colorScheme.surface,
    contentColor: Color = LiquidTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(modifier.surfaceStyle(shape, color, border, shadowElevation)) { content() }
    }
}

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = LiquidTheme.colorScheme.surface,
    contentColor: Color = LiquidTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape, color = color, contentColor = contentColor, border = border,
        tonalElevation = tonalElevation, shadowElevation = shadowElevation, content = content
    )
}

private enum class ButtonKind { Filled, Tonal, Outline, Text }

internal val LiquidButtonShape = RoundedCornerShape(13.dp)

internal val LocalLibraryDialogAction = staticCompositionLocalOf { false }
internal val LocalLibraryDialogActionColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
private fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    contentPadding: PaddingValues,
    kind: ButtonKind,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LiquidTheme.colorScheme
    val isLibraryDialogAction = LocalLibraryDialogAction.current && kind == ButtonKind.Text
    val dialogActionColor = LocalLibraryDialogActionColor.current
    val baseBackground = when (kind) {
        ButtonKind.Filled -> colors.primary
        ButtonKind.Tonal -> colors.primaryContainer
        ButtonKind.Outline -> colors.surfaceContainerHigh
        ButtonKind.Text -> Color.Transparent
    }
    val background = when {
        kind == ButtonKind.Text -> Color.Transparent
        enabled -> baseBackground
        else -> baseBackground.copy(alpha = 0.45f)
    }
    val foreground = if (isLibraryDialogAction && dialogActionColor != Color.Unspecified) {
        dialogActionColor
    } else when (kind) {
        ButtonKind.Filled -> colors.onPrimary
        ButtonKind.Tonal -> colors.onPrimaryContainer
        else -> colors.primary
    }.let { if (enabled) it else it.copy(alpha = 0.55f) }
    val shape = if (isLibraryDialogAction) Capsule() else LiquidButtonShape
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Row(
            modifier = modifier
                .then(if (isLibraryDialogAction) Modifier.fillMaxSize() else Modifier)
                .defaultMinSize(minHeight = 48.dp)
                .clip(shape)
                .background(background)
                .then(if (kind == ButtonKind.Outline) Modifier.border(1.dp, colors.outlineVariant, shape) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp), content: @Composable RowScope.() -> Unit) = LiquidButton(onClick, modifier, enabled, contentPadding, ButtonKind.Filled, content)
@Composable fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp), content: @Composable RowScope.() -> Unit) = LiquidButton(onClick, modifier, enabled, contentPadding, ButtonKind.Tonal, content)
@Composable fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp), content: @Composable RowScope.() -> Unit) = LiquidButton(onClick, modifier, enabled, contentPadding, ButtonKind.Outline, content)
@Composable fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp), content: @Composable RowScope.() -> Unit) = LiquidButton(onClick, modifier, enabled, contentPadding, ButtonKind.Text, content)

@Stable data class IconButtonColors(val containerColor: Color = Color.Transparent, val contentColor: Color = Color.Unspecified)
object IconButtonDefaults { @Composable fun iconButtonColors(containerColor: Color = Color.Transparent, contentColor: Color = LiquidTheme.colorScheme.onSurface) = IconButtonColors(containerColor, contentColor) }

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, colors: IconButtonColors = IconButtonDefaults.iconButtonColors(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides colors.contentColor) {
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .background(colors.containerColor)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

@Stable data class FilterChipColors(val container: Color, val selectedContainer: Color, val label: Color, val selectedLabel: Color)
object FilterChipDefaults {
    @Composable fun filterChipColors(
        containerColor: Color = LiquidTheme.colorScheme.surfaceContainerHigh,
        selectedContainerColor: Color = LiquidTheme.colorScheme.primaryContainer,
        labelColor: Color = LiquidTheme.colorScheme.onSurfaceVariant,
        selectedLabelColor: Color = LiquidTheme.colorScheme.primary
    ) = FilterChipColors(containerColor, selectedContainerColor, labelColor, selectedLabelColor)
}

@Composable
fun FilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp), border: BorderStroke? = null, colors: FilterChipColors = FilterChipDefaults.filterChipColors()) {
    Surface(
        onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 48.dp), shape = shape,
        color = if (selected) colors.selectedContainer else colors.container,
        contentColor = if (selected) colors.selectedLabel else colors.label, border = border
    ) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) { label() }
    }
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val colors = LiquidTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val fieldBackground by animateColorAsState(
        targetValue = if (focused) {
            lerp(colors.surfaceContainerHighest, colors.primary, if (colors.isDark) 0.12f else 0.05f)
        } else colors.surfaceContainerHighest,
        label = "text-field-background"
    )
    val fieldBorder by animateColorAsState(
        targetValue = if (focused) colors.primary.copy(alpha = 0.78f) else colors.outlineVariant,
        label = "text-field-border"
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = singleLine,
        textStyle = LiquidTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        visualTransformation = visualTransformation,
        decorationBox = { inner ->
            Column(
                Modifier
                    .clip(shape)
                    .background(fieldBackground)
                    .border(if (focused) 1.5.dp else 1.dp, fieldBorder, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (label != null) CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) { label() }
                Box {
                    if (value.isEmpty() && placeholder != null) CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant.copy(alpha = 0.7f)) { placeholder() }
                    inner()
                }
            }
        }
    )
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 0.5.dp, color: Color = LiquidTheme.colorScheme.outlineVariant) {
    Spacer(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
fun RadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (selected) LiquidTheme.colorScheme.primary else LiquidTheme.colorScheme.onSurfaceVariant, label = "radio")
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onClick != null
            ) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(22.dp).border(1.8.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).background(color, CircleShape))
        }
    }
}

@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    NativeLiquidToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

@Composable
fun CircularProgressIndicator(modifier: Modifier = Modifier, color: Color = LiquidTheme.colorScheme.primary, strokeWidth: Dp = 2.dp) {
    val transition = rememberInfiniteTransition(label = "circular-progress")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "circular-progress-rotation"
    )
    androidx.compose.foundation.Canvas(modifier.defaultMinSize(20.dp, 20.dp)) {
        drawArc(color, -90f + rotation, 270f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
    }
}

fun fullscreenSubpageTransitionSpec(
    durationMs: Int = 320
): AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
    if (targetState) {
        (slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(220)))
        .togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(220), targetAlpha = 0.7f)
        ).apply {
            targetContentZIndex = 1f
        }
    } else {
        (slideInHorizontally(
            initialOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(220), initialAlpha = 0.7f))
        .togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(220))
        ).apply {
            targetContentZIndex = 0f
        }
    }
}

fun <T> fullscreenSubpageTransitionSpec(
    durationMs: Int = 320,
    isSubpage: (T) -> Boolean
): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
    val forward = isSubpage(targetState) && !isSubpage(initialState)
    if (forward) {
        (slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(220)))
        .togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(220), targetAlpha = 0.7f)
        ).apply {
            targetContentZIndex = 1f
        }
    } else {
        (slideInHorizontally(
            initialOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(220), initialAlpha = 0.7f))
        .togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(220))
        ).apply {
            targetContentZIndex = 0f
        }
    }
}

/**
 * 全屏页面容器：自带完整的液态磨砂与渐变背景，
 * 确保页面在进入/退出转场滑动时背景跟随页面同步平移，
 * 彻底防止滑动过程中底层页面穿透透视的问题。
 */
@Composable
fun FullscreenPageContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LiquidAmbientBackground()
        content()
    }
}
