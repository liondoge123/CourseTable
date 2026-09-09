package com.coursetable.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.LiquidTheme

@Composable
fun OptionChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val shape = remember { RoundedCornerShape(10.dp) }
    val colors = LiquidTheme.colorScheme
    val bg = if (selected) colors.primary.copy(alpha = 0.14f) else colors.surface
    val textCol = if (selected) colors.primary else colors.onSurfaceVariant
    val borderCol = if (selected) colors.primary.copy(alpha = 0.45f) else colors.outlineVariant

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, borderCol, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = LiquidTheme.typography.labelMedium,
            color = textCol,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
