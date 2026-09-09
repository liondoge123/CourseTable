package com.coursetable.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.liquid.HorizontalDivider
import com.coursetable.app.ui.liquid.Surface
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.LiquidTheme

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = LiquidTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = LiquidTheme.typography.bodySmall,
                    color = LiquidTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        actions()
    }
}

@Composable
fun SectionFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LiquidTheme.shapes.medium,
        color = LiquidTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LiquidTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        content()
    }
}

@Composable
fun SheetHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = LiquidTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = LiquidTheme.typography.bodySmall,
                color = LiquidTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = LiquidTheme.colorScheme.outlineVariant
    )
}
