package com.coursetable.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.LiquidTheme

/** Shared timetable axis header. Preview callers omit [date]. */
@Composable
internal fun TimetableDayHeader(
    label: String,
    date: String? = null,
    isToday: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (LocalWallpaperActive.current) LiquidTheme.colorScheme.onSurface
                    else if (isToday) LiquidTheme.colorScheme.primary else LiquidTheme.colorScheme.onSurfaceVariant
            )
            if (date != null) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .height(26.dp)
                        .widthIn(min = 26.dp)
                        .clip(CircleShape)
                        .background(if (isToday) LiquidTheme.colorScheme.primary.copy(alpha = if (LocalWallpaperActive.current) 1f else 0.12f) else Color.Transparent)
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday && LocalWallpaperActive.current) LiquidTheme.colorScheme.onPrimary
                            else if (LocalWallpaperActive.current) LiquidTheme.colorScheme.onSurface
                            else if (isToday) LiquidTheme.colorScheme.primary else LiquidTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Shared section marker. Preview callers omit the start/end time strings. */
@Composable
internal fun TimetablePeriodLabel(
    section: Int,
    startTime: String? = null,
    endTime: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = section.toString(),
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (LocalWallpaperActive.current) LiquidTheme.colorScheme.onSurface else LiquidTheme.colorScheme.primary
            )
            if (startTime != null && endTime != null) {
                val axisColor = if (LocalWallpaperActive.current) LiquidTheme.colorScheme.onSurface else LiquidTheme.colorScheme.onSurfaceVariant
                Text(startTime, fontSize = 10.sp, lineHeight = 11.sp, color = axisColor)
                Text(endTime, fontSize = 10.sp, lineHeight = 11.sp, color = axisColor)
            }
        }
    }
}

/** Shared opaque color for active-course metadata and its editor preview. */
@Composable
internal fun courseMetadataColor(): Color {
    val colors = LiquidTheme.colorScheme
    return lerp(colors.onSurfaceVariant, colors.onSurface, if (colors.isDark) 0.35f else 0.15f)
}

/** Shared course-card presentation used by the normal timetable and import preview. */
@Composable
internal fun TimetableCourseCardSurface(
    name: String,
    teacher: String,
    location: String,
    accent: Color,
    duration: Int,
    modifier: Modifier = Modifier,
    alignLeft: Boolean = true,
    dimmed: Boolean = false,
    progress: Float? = null,
    needsCheck: Boolean = false,
    onClick: () -> Unit
) {
    val isDark = LiquidTheme.colorScheme.isDark
    val baseSurface = if (LocalWallpaperActive.current) LiquidTheme.colorScheme.surface.copy(alpha = 1f)
        else LiquidTheme.colorScheme.surface
    val opaqueCardColor = if (dimmed) {
        lerp(baseSurface, LiquidTheme.colorScheme.onSurfaceVariant, if (isDark) 0.12f else 0.08f)
    } else {
        lerp(baseSurface, accent, if (isDark) 0.28f else 0.16f)
    }
    val cardColor = if (LocalWallpaperActive.current) opaqueCardColor.copy(
        alpha = LocalWallpaperCardOpacity.current.coerceIn(0f, 1f)) else opaqueCardColor
    val textAlpha = if (dimmed) 0.58f else 1f
    val metadataColor = courseMetadataColor()
    val teacherColor = if (dimmed) LiquidTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f * textAlpha) else metadataColor
    val locationColor = if (dimmed) LiquidTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.84f * textAlpha) else metadataColor
    val metadataWeight = if (dimmed) FontWeight.Normal else FontWeight.Medium
    val hasMeta = teacher.isNotBlank() || location.isNotBlank()
    val nameMaxLines = when {
        duration >= 3 -> 6
        duration == 2 -> 5
        else -> if (hasMeta) 2 else 3
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(cardColor)
            .border(1.dp, if (dimmed) LiquidTheme.colorScheme.outlineVariant else accent.copy(alpha = 0.32f), shape)
            .clickable(onClick = onClick),
        contentAlignment = if (alignLeft) Alignment.TopStart else Alignment.Center
    ) {
        val barWidth = 3.5.dp
        if (!dimmed) {
            if (progress != null) {
                TimetableCourseProgress(progress, accent,
                    Modifier.align(Alignment.CenterStart).fillMaxHeight().width(barWidth))
            } else {
                Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(barWidth).background(accent))
            }
        }
        val textAlignment = if (alignLeft) TextAlign.Start else TextAlign.Center
        Column(
            Modifier.padding(start = if (dimmed) 4.5.dp else 6.dp, end = if (dimmed) 4.5.dp else 3.dp, top = 3.dp, bottom = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = if (alignLeft) Alignment.Start else Alignment.CenterHorizontally
        ) {
            Text(
                text = (if (needsCheck) "! " else "") + name,
                color = LiquidTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = textAlignment,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (teacher.isNotBlank()) Text(
                teacher, color = teacherColor, fontWeight = metadataWeight,
                fontSize = 10.sp, lineHeight = 11.sp, textAlign = textAlignment,
                maxLines = if (duration == 1) 1 else 2, overflow = TextOverflow.Ellipsis
            )
            if (location.isNotBlank()) Text(
                location, color = locationColor, fontWeight = metadataWeight,
                fontSize = 10.sp, lineHeight = 11.sp, textAlign = textAlignment,
                maxLines = if (duration == 1) 1 else 3, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Progress changes invalidate drawing only, leaving card text and layout cached. */
@Composable
internal fun TimetableCourseProgress(progress: Float, accent: Color, modifier: Modifier = Modifier) {
    val animated = animateFloatAsState(progress,
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "course_progress")
    Box(modifier.drawBehind {
        drawRect(accent.copy(alpha = 0.22f))
        drawRect(accent, size = Size(size.width, size.height * animated.value.coerceIn(0f, 1f)))
    })
}
