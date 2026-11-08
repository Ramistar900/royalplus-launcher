package com.royalplus.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.sin

/**
 * Subtle animated light layer over the photographic fireplace image.
 * The image remains the detailed source of realism; this layer adds moving flame
 * highlights, changing glow, and rising embers without a video decoder.
 */
@Composable
internal fun RoyalFireOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val glowPulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val baseY = height * 0.855f
        val glowCenter = Offset(width * 0.5f, baseY - height * 0.035f)
        val glowRadius = width * 0.285f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x42FF8A20).copy(alpha = glowPulse),
                    Color(0x20FF4A08).copy(alpha = glowPulse),
                    Color.Transparent
                ),
                center = glowCenter,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = glowCenter
        )

        // Thin, low-opacity flame tongues reinforce the photographed fire rather than
        // replacing it with cartoon shapes.
        for (index in 0 until 13) {
            val phase = (cycle + index * 0.137f) % 1f
            val wave = sin(phase * 2f * PI.toFloat() + index * 1.37f)
            val pulse = 0.70f + 0.30f * wave
            val centerX = width * (0.235f + index * 0.044f)
            val flameHeight = height * (0.235f + (index % 5) * 0.038f) * pulse
            val flameWidth = width * (0.024f + (index % 3) * 0.0045f)
            val sway = wave * flameWidth * 0.58f
            val tipX = centerX + sway
            val tipY = baseY - flameHeight

            val outer = Path().apply {
                moveTo(centerX - flameWidth, baseY)
                cubicTo(
                    centerX - flameWidth * 1.18f, baseY - flameHeight * 0.28f,
                    tipX - flameWidth * 0.38f, tipY + flameHeight * 0.22f,
                    tipX, tipY
                )
                cubicTo(
                    tipX + flameWidth * 0.36f, tipY + flameHeight * 0.20f,
                    centerX + flameWidth * 1.18f, baseY - flameHeight * 0.28f,
                    centerX + flameWidth, baseY
                )
                close()
            }
            drawPath(
                path = outer,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xCCFFF0B0),
                        Color(0xBFFF9B2E),
                        Color(0x88FF5A12),
                        Color.Transparent
                    ),
                    startY = tipY,
                    endY = baseY
                )
            )

            val coreWidth = flameWidth * 0.34f
            val coreHeight = flameHeight * 0.57f
            val core = Path().apply {
                moveTo(centerX - coreWidth, baseY)
                cubicTo(
                    centerX - coreWidth * 0.9f, baseY - coreHeight * 0.28f,
                    tipX - coreWidth * 0.25f, baseY - coreHeight * 0.78f,
                    tipX, baseY - coreHeight
                )
                cubicTo(
                    tipX + coreWidth * 0.25f, baseY - coreHeight * 0.78f,
                    centerX + coreWidth * 0.9f, baseY - coreHeight * 0.28f,
                    centerX + coreWidth, baseY
                )
                close()
            }
            drawPath(
                path = core,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xDDFFF8D6),
                        Color(0xAAFFE28A),
                        Color(0x66FFC04B),
                        Color.Transparent
                    ),
                    startY = baseY - coreHeight,
                    endY = baseY
                )
            )
        }

        // Small glowing embers drift upward at different speeds and fade naturally.
        for (index in 0 until 19) {
            val speed = 0.46f + (index % 5) * 0.13f
            val progress = (cycle * speed + index * 0.173f) % 1f
            val xBase = 0.34f + (index % 9) * 0.04f
            val drift = sin((progress * 5.5f + index * 1.9f).toDouble()).toFloat()
            val x = width * xBase + drift * width * 0.009f
            val y = baseY - progress * height * (0.30f + (index % 4) * 0.035f)
            val fade = (1f - progress).coerceIn(0f, 1f)
            val radius = (1.2f + (index % 3) * 0.65f) * fade
            if (radius > 0.35f) {
                drawCircle(
                    color = if (index % 3 == 0) Color(0xFFFFE1A0) else Color(0xFFFF9B36),
                    radius = radius,
                    center = Offset(x, y),
                    alpha = fade * 0.72f
                )
            }
        }
    }
}
