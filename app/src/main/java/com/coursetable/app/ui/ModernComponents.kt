package com.coursetable.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.liquid.ListItemSurface
import com.coursetable.app.ui.liquid.ListItemDefaults
import com.coursetable.app.ui.liquid.ListItemMaterial
import com.coursetable.app.ui.theme.LiquidTheme

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
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

/** Parent pages own the content inset, so headings and their cards share one axis. */
@Composable
internal fun PageSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        style = LiquidTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = LiquidTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun SectionFrame(
    modifier: Modifier = Modifier,
    containerAlpha: Float = 1f,
    content: @Composable () -> Unit
) {
    ListItemSurface(
        modifier = modifier.fillMaxWidth(),
        shape = ListItemDefaults.GroupShape,
        opacity = containerAlpha
    ) {
        content()
    }
}

/** Shared container for fields and pinned source content in the course editor. */
@Composable
internal fun FormSectionCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ListItemSurface(
        modifier = modifier.fillMaxWidth(),
        material = ListItemMaterial.Inset
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            content = content
        )
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
        thickness = 0.5.dp,
        color = LiquidTheme.colorScheme.outlineVariant
    )
}
