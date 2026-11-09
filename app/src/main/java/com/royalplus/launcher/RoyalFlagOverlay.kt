package com.royalplus.launcher

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.imageResource
import kotlin.math.PI
import kotlin.math.sin

/**
 * Mesh-warped flag image: the left edge stays anchored while soft folds
 * travel across the fabric. Intentionally silent; no audio controller.
 */
@Composable
internal fun RoyalFlagOverlay(modifier: Modifier = Modifier) {
    val flag = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.royal_syria_flag_4k).asAndroidBitmap()
    val transition = rememberInfiniteTransition(label = "syrian_flag_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flag_wave_phase"
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    Canvas(modifier = modifier.fillMaxSize()) {
        val columns = 28
        val rows = 16
        val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
        val widthPx = size.width
        val heightPx = size.height
        for (row in 0..rows) {
            for (column in 0..columns) {
                val xRatio = column.toFloat() / columns
                val yRatio = row.toFloat() / rows
                val x = xRatio * widthPx
                val envelope = xRatio * xRatio
                val primary = sin(xRatio * (2.7f * PI.toFloat()) - phase + yRatio * 0.45f)
                val secondary = sin(xRatio * (5.4f * PI.toFloat()) - phase * 1.35f + yRatio * 0.7f)
                val y = yRatio * heightPx +
                    (primary * heightPx * 0.026f + secondary * heightPx * 0.007f) * envelope
                val index = (row * (columns + 1) + column) * 2
                vertices[index] = x
                vertices[index + 1] = y
            }
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(flag, columns, rows, vertices, 0, null, 0, paint)
        }
    }
}
