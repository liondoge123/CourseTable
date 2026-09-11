package com.coursetable.app.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.createRippleModifierNode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Dp

internal val LightLiquidRippleAlpha = RippleAlpha(
    draggedAlpha = 0.12f,
    focusedAlpha = 0.10f,
    hoveredAlpha = 0.08f,
    pressedAlpha = 0.10f
)

internal val DarkLiquidRippleAlpha = RippleAlpha(
    draggedAlpha = 0.16f,
    focusedAlpha = 0.12f,
    hoveredAlpha = 0.08f,
    pressedAlpha = 0.12f
)

/**
 * Touch feedback for the Liquid design system.
 *
 * A bounded ripple starts at the press position and is clipped by the clickable layout and any
 * parent surface shape. Keeping this in the design system also prevents bare Foundation clickables
 * from falling back to the immediate full-area debug indication.
 */
internal fun liquidRipple(
    color: Color,
    rippleAlpha: RippleAlpha,
    bounded: Boolean = true,
    radius: Dp = Dp.Unspecified
): IndicationNodeFactory = LiquidRippleNodeFactory(
    color = color,
    rippleAlpha = rippleAlpha,
    bounded = bounded,
    radius = radius
)

private data class LiquidRippleNodeFactory(
    private val color: Color,
    private val rippleAlpha: RippleAlpha,
    private val bounded: Boolean,
    private val radius: Dp
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        createRippleModifierNode(
            interactionSource = interactionSource,
            bounded = bounded,
            radius = radius,
            color = { color },
            rippleAlpha = { rippleAlpha }
        )
}
