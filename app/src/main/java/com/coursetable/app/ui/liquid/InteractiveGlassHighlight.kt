package com.coursetable.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Android-native InteractiveHighlight behavior from AndroidLiquidGlass. */
internal class InteractiveGlassHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader = if (isRuntimeShaderSupported()) {
        RuntimeShader(
            """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}
"""
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressAnimation.value
        if (progress > 0f) {
            if (shader != null) {
                drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
                shader.apply {
                    val highlightPosition = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(alpha = 0.15f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        highlightPosition.x.fastCoerceIn(0f, size.width),
                        highlightPosition.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(alpha = 0.25f * progress), blendMode = BlendMode.Plus)
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            startPosition = down.position
            animationScope.launch {
                launch { pressAnimation.animateTo(1f, pressSpec) }
                launch { positionAnimation.snapTo(startPosition) }
            }

            var pointerDown = true
            while (pointerDown) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    pointerDown = false
                } else {
                    animationScope.launch { positionAnimation.snapTo(change.position) }
                }
            }

            animationScope.launch {
                launch { pressAnimation.animateTo(0f, pressSpec) }
                launch { positionAnimation.animateTo(startPosition, positionSpec) }
            }
        }
    }
}
