package com.coursetable.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.Icon
import com.coursetable.app.ui.liquid.IconButton
import com.coursetable.app.ui.liquid.IconButtonDefaults
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.liquid.TextButton
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme

/**
 * 行内删除操作：
 * - compact = true：严格固定 40dp × 40dp 尺寸，点击后在淡红背景中平滑切换为红色对勾 (✓)，绝不膨胀变形挤压左侧排版；
 * - compact = false：保留底部操作栏等开阔区域的文字按钮形态。
 */
@Composable
fun InlineDeleteAction(
    armed: Boolean,
    onArm: () -> Unit,
    onConfirm: () -> Unit,
    compact: Boolean = false
) {
    if (compact) {
        val backgroundColor by animateColorAsState(
            targetValue = if (armed) {
                MaterialTheme.colorScheme.error.copy(
                    alpha = if (MaterialTheme.colorScheme.isDark) 0.28f else 0.16f
                )
            } else {
                Color.Transparent
            },
            animationSpec = tween(180),
            label = "DeleteActionBg"
        )
        val iconTint by animateColorAsState(
            targetValue = if (armed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            },
            animationSpec = tween(180),
            label = "DeleteActionTint"
        )

        IconButton(
            onClick = if (armed) onConfirm else onArm,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = iconTint
            )
        ) {
            AnimatedContent(
                targetState = armed,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(140))
                },
                label = "DeleteActionIcon"
            ) { isArmed ->
                if (isArmed) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "确认删除",
                        modifier = Modifier.size(18.dp),
                        tint = iconTint
                    )
                } else {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = iconTint
                    )
                }
            }
        }
    } else {
        TextButton(
            onClick = if (armed) onConfirm else onArm,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Icon(
                if (armed) Icons.Filled.Check else Icons.Filled.Delete,
                contentDescription = if (armed) "确认删除" else "删除",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                if (armed) "确认删除" else "删除",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
