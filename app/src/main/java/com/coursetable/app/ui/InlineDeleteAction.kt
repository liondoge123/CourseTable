package com.coursetable.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.Icon
import com.coursetable.app.ui.liquid.IconButton
import com.coursetable.app.ui.liquid.IconButtonDefaults
import com.coursetable.app.ui.liquid.LiquidButtonShape
import com.coursetable.app.ui.liquid.Surface
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme

/**
 * 行内删除操作：
 * - compact = true：固定 40dp × 40dp；确认状态使用红底白色垃圾桶，不挤压列表排版；
 * - compact = false：固定 112dp × 48dp，以“垃圾桶 + 文字”呈现完整的删除语义。
 */
@Composable
fun InlineDeleteAction(
    armed: Boolean,
    onArm: () -> Unit,
    onConfirm: () -> Unit,
    compact: Boolean = false
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (armed) MaterialTheme.colorScheme.error else Color.Transparent,
        animationSpec = tween(180),
        label = "DeleteActionBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (armed) Color.White else MaterialTheme.colorScheme.error,
        animationSpec = tween(180),
        label = "DeleteActionContent"
    )

    if (compact) {
        IconButton(
            onClick = if (armed) onConfirm else onArm,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor
            )
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = if (armed) "确认删除" else "删除",
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
        }
    } else {
        Surface(
            onClick = if (armed) onConfirm else onArm,
            modifier = Modifier
                .width(112.dp)
                .height(48.dp),
            shape = LiquidButtonShape,
            color = backgroundColor,
            contentColor = contentColor
        ) {
            Row(
                modifier = Modifier
                    .width(112.dp)
                    .height(48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(6.dp))
                AnimatedContent(
                    targetState = armed,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(140))
                    },
                    label = "DeleteActionLabel"
                ) { isArmed ->
                    Text(
                        if (isArmed) "确认删除" else "删除",
                        color = contentColor
                    )
                }
            }
        }
    }
}
