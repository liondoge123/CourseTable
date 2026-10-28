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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.LiquidTheme
import com.coursetable.app.ui.theme.tabularNumbers
import com.coursetable.app.ui.theme.fromStoredLong

/** One neutral surface instead of stacked translucent cards or a misleading progress bar. */
@Composable
internal fun TimetableConflictCardSurface(
    block: TimetableCourseBlock,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LiquidTheme.colorScheme
    val neutral = lerp(colors.surface.copy(alpha = 1f), colors.onSurfaceVariant, if (colors.isDark) 0.12f else 0.06f)
    val background = if (LocalWallpaperActive.current) neutral.copy(alpha = LocalWallpaperCardOpacity.current.coerceIn(0f, 1f)) else neutral
    val shape = RoundedCornerShape(6.dp)
    Column(modifier.testTag("timetable-conflict-${block.key}")
        .semantics(mergeDescendants = true) {
            contentDescription = "${block.items.size} 门冲突，" + block.items.joinToString("；") {
                "${it.course.name}，${courseOccurrenceTime(it.course)}"
            }
        }.clip(shape).background(background).border(1.dp, colors.outlineVariant, shape)
        .clickable(role = Role.Button, onClick = onClick).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("${block.items.size}门冲突", fontSize = 9.sp, lineHeight = 11.sp,
            color = colors.primary, fontWeight = FontWeight.Bold, maxLines = 1,
            softWrap = false, overflow = TextOverflow.Ellipsis)
        // Give each name the same height, using all available lines rather than a
        // fixed two-line preview. A long first name must not crowd out the next one.
        Column(Modifier.weight(1f).clipToBounds(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            block.items.take(2).forEach { item ->
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()
                    .testTag("timetable-conflict-name-slot-${item.course.id}")) {
                    val lineHeight = with(LocalDensity.current) { 12.sp.toDp() }
                    val availableLines = (maxHeight / lineHeight).toInt().coerceAtLeast(1)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape)
                            .background(Color.fromStoredLong(item.course.color)))
                        Text(item.course.name, modifier = Modifier.weight(1f)
                            .testTag("timetable-conflict-name-${item.course.id}"), fontSize = 11.sp, lineHeight = 12.sp,
                            color = colors.onSurface, fontWeight = FontWeight.Medium,
                            maxLines = availableLines, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Shared timetable axis header. Preview callers omit [date]. */
@Composable
internal fun TimetableDayHeader(
    label: String,
    date: String? = null,
    isToday: Boolean = false,
    modifier: Modifier = Modifier,
    axisStyle: WallpaperAxisStyle? = null
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else if (axisStyle != null) FontWeight.Medium else FontWeight.Normal,
                color = axisStyle?.foreground ?: if (LocalWallpaperActive.current) LiquidTheme.colorScheme.onSurface
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
                        fontWeight = if (isToday) FontWeight.Bold else if (axisStyle != null) FontWeight.Medium else FontWeight.Normal,
                        color = if (isToday && axisStyle != null) wallpaperTextColorOn(LiquidTheme.colorScheme.primary)
                            else if (axisStyle != null) axisStyle.foreground
                            else if (isToday && LocalWallpaperActive.current) LiquidTheme.colorScheme.onPrimary
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
    modifier: Modifier = Modifier,
    axisStyle: WallpaperAxisStyle? = null
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = section.toString(),
                style = LiquidTheme.typography.bodyMedium.tabularNumbers(),
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                color = axisStyle?.foreground ?: if (LocalWallpaperActive.current) LiquidTheme.colorScheme.onSurface else LiquidTheme.colorScheme.primary
            )
            if (startTime != null && endTime != null) {
                val axisColor = axisStyle?.foreground ?: if (LocalWallpaperActive.current) LiquidTheme.colorScheme.onSurface else LiquidTheme.colorScheme.onSurfaceVariant
                val weight = if (axisStyle != null) FontWeight.Medium else FontWeight.Normal
                Text(startTime, fontSize = 10.sp, lineHeight = 11.sp, color = axisColor, fontWeight = weight,
                    style = LiquidTheme.typography.bodyMedium.tabularNumbers())
                Text(endTime, fontSize = 10.sp, lineHeight = 11.sp, color = axisColor, fontWeight = weight,
                    style = LiquidTheme.typography.bodyMedium.tabularNumbers())
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
    showTeacher: Boolean = true,
    showLocation: Boolean = true,
    locationFirst: Boolean = false,
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
    val hasTeacher = showTeacher && teacher.isNotBlank()
    val hasLocation = showLocation && location.isNotBlank()
    val hasMeta = hasTeacher || hasLocation
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
        CourseCardContentLayout(
            modifier = Modifier.padding(start = if (dimmed) 4.5.dp else 6.dp, end = if (dimmed) 4.5.dp else 3.dp, top = 3.dp, bottom = 3.dp),
            alignLeft = alignLeft,
            shortCard = duration == 1,
            locationFirst = locationFirst
        ) {
            Text(
                text = (if (needsCheck) "! " else "") + name,
                modifier = Modifier.layoutId("name"),
                color = LiquidTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = textAlignment,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (hasTeacher) Text(
                teacher, color = teacherColor, fontWeight = metadataWeight,
                modifier = Modifier.layoutId("teacher"),
                fontSize = 10.sp, lineHeight = 11.sp, textAlign = textAlignment,
                maxLines = if (duration == 1) 1 else 2, overflow = TextOverflow.Ellipsis
            )
            if (hasLocation) Text(
                location, color = locationColor, fontWeight = metadataWeight,
                modifier = Modifier.layoutId("location"),
                fontSize = 10.sp, lineHeight = 11.sp, textAlign = textAlignment,
                maxLines = if (duration == 1) 2 else 3, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Allocate height by importance, then place metadata in the user's chosen order. */
@Composable
private fun CourseCardContentLayout(
    modifier: Modifier,
    alignLeft: Boolean,
    shortCard: Boolean,
    locationFirst: Boolean,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val byId = measurables.associateBy { it.layoutId }
        val name = byId.getValue("name")
        val location = byId["location"]
        val teacher = byId["teacher"]
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = 2.dp.roundToPx()
        // Intrinsics use the actual font metrics, including the user's font scale.
        fun androidx.compose.ui.layout.Measurable.minimumHeight(): Int =
            minIntrinsicHeight(maxIntrinsicWidth(Constraints.Infinity).coerceAtLeast(width))
        val nameMin = name.minimumHeight()
        val locationMin = location?.minimumHeight() ?: 0
        val teacherMin = teacher?.minimumHeight() ?: 0
        val metadataCount = listOfNotNull(location, teacher).size
        // Taller cards reserve a line for each field. Short cards spend the
        // available height on the name and location before admitting a teacher.
        val reserveTeacher = !shortCard && teacher != null &&
            height >= nameMin + locationMin + teacherMin + gap * metadataCount
        val teacherReserve = if (reserveTeacher) teacherMin + gap else 0
        val preferredLocationHeight = if (shortCard && location != null) location.minIntrinsicHeight(width) else locationMin
        val locationReserve = when {
            location == null -> 0
            height >= nameMin + preferredLocationHeight + gap + teacherReserve -> preferredLocationHeight + gap
            height >= nameMin + locationMin + gap + teacherReserve -> locationMin + gap
            else -> 0
        }
        fun measure(item: androidx.compose.ui.layout.Measurable, maxHeight: Int) = item.measure(
            Constraints(maxWidth = width, maxHeight = maxHeight.coerceAtLeast(0)))
        val namePlaceable = measure(name, height - locationReserve - teacherReserve)
        var remaining = height - namePlaceable.height
        val locationPlaceable = if (location != null && remaining >= locationMin + gap + teacherReserve) {
            measure(location, remaining - gap - teacherReserve).also { remaining -= it.height + gap }
        } else null
        val teacherPlaceable = if (teacher != null && remaining >= teacherMin + gap) {
            measure(teacher, remaining - gap)
        } else null
        val metadata = if (locationFirst) listOfNotNull(locationPlaceable, teacherPlaceable)
            else listOfNotNull(teacherPlaceable, locationPlaceable)
        val placed = listOf(namePlaceable) + metadata
        val contentHeight = placed.sumOf { it.height } + gap * (placed.size - 1)
        layout(width, constraints.constrainHeight(contentHeight)) {
            var y = 0
            placed.forEach { child ->
                child.placeRelative(if (alignLeft) 0 else (width - child.width) / 2, y)
                y += child.height + gap
            }
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
