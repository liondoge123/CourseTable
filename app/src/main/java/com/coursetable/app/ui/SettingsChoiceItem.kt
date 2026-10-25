package com.coursetable.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme

internal data class SettingsChoice(val key: String, val label: String)

/** Touch opens at the finger; keyboard/accessibility activation uses the current value. */
@Composable
internal fun SettingsChoiceItem(
    id: String,
    icon: ImageVector,
    title: String,
    selectedKey: String,
    choices: List<SettingsChoice>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var valueBounds by remember { mutableStateOf<Rect?>(null) }
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    var menuAlignment by remember { mutableStateOf(DropdownMenuAlignment.END) }
    val label = choices.firstOrNull { it.key == selectedKey }?.label.orEmpty()
    val colors = LiquidTheme.colorScheme
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp)
                .testTag("settings-choice-$id")
                .semantics { stateDescription = label }
                .onGloballyPositioned { rowCoordinates = it }
                .pointerInput(Unit) {
                    // Observe without consuming: clickable retains its feedback and scroll
                    // cancellation, and the parent can still handle drags normally.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touchPoint = rowCoordinates?.localToRoot(down.position)
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    touchPoint = null
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            touchPoint = null
                        }
                    }
                }
                .clickable(role = Role.Button, onClick = {
                    val point = touchPoint
                    anchor = if (point != null) Rect(point, Size(1f, 1f)) else valueBounds
                    menuAlignment = if (point != null) DropdownMenuAlignment.CENTER else DropdownMenuAlignment.END
                    touchPoint = null
                    expanded = true
                })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListItemIcon(icon, tint = colors.onSurfaceVariant)
            Spacer(Modifier.width(ListItemDefaults.IconGap))
            Text(title, Modifier.weight(1f).padding(end = 12.dp),
                style = LiquidTheme.typography.bodyLarge, fontWeight = FontWeight.Normal)
            Row(
                Modifier.heightIn(min = 32.dp).onGloballyPositioned { valueBounds = it.boundsInRoot() }
                    .testTag("settings-choice-value-$id"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, Modifier.widthIn(max = 108.dp), style = LiquidTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal, color = colors.onSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Canvas(Modifier.size(16.dp, 20.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.22f, size.height * 0.35f)
                        lineTo(size.width * 0.5f, size.height * 0.15f)
                        lineTo(size.width * 0.78f, size.height * 0.35f)
                        moveTo(size.width * 0.22f, size.height * 0.65f)
                        lineTo(size.width * 0.5f, size.height * 0.85f)
                        lineTo(size.width * 0.78f, size.height * 0.65f)
                    }
                    drawPath(path, colors.onSurfaceVariant.copy(alpha = 0.7f),
                        style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchorBounds = anchor,
            alignment = menuAlignment,
            overlapAnchor = false
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    modifier = Modifier.testTag("$id-${choice.key}"),
                    text = { Text(choice.label) },
                    selected = choice.key == selectedKey,
                    onClick = {
                        if (expanded) {
                            expanded = false
                            if (choice.key != selectedKey) onSelect(choice.key)
                        }
                    }
                )
            }
        }
    }
}
