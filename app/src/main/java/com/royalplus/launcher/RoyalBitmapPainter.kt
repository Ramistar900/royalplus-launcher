package com.royalplus.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext

/**
 * Loads raster drawable resources with a bounded decoded size.
 *
 * Resource painters created with painterResource decode the full source image.
 * Large 4K/8K wallpapers can therefore exceed the bitmap limits of older TV
 * renderers. This decoder inspects dimensions first and downsamples before
 * allocating the pixel buffer.
 */
@Composable
internal fun rememberRoyalBitmapPainter(
    resourceId: Int,
    maxEdgePx: Int = 2048,
    maxPixels: Long = 4_194_304L
): Painter {
    val context = LocalContext.current
    val safeMaxEdge = maxEdgePx.coerceAtLeast(1)
    val safeMaxPixels = maxPixels.coerceAtLeast(1L)

    return remember(context, resourceId, safeMaxEdge, safeMaxPixels) {
        val fallback = ColorPainter(Color(0xFF080A0F))
        val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inScaled = false
            }
            BitmapFactory.decodeResource(context.resources, resourceId, bounds)

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                val sampleSize = royalBitmapSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxEdgePx = safeMaxEdge,
                    maxPixels = safeMaxPixels
                )
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
                BitmapFactory.decodeResource(context.resources, resourceId, options)
            }
        }.getOrNull()

        bitmap?.let { BitmapPainter(it.asImageBitmap()) } ?: fallback
    }
}

/** Returns a power-of-two sample size satisfying both edge and pixel budgets. */
internal fun royalBitmapSampleSize(
    width: Int,
    height: Int,
    maxEdgePx: Int,
    maxPixels: Long
): Int {
    if (width <= 0 || height <= 0) return 1

    val sourceWidth = width.toLong()
    val sourceHeight = height.toLong()
    val edgeLimit = maxEdgePx.coerceAtLeast(1).toLong()
    val pixelLimit = maxPixels.coerceAtLeast(1L)
    var sample = 1

    while (true) {
        val decodedWidth = (sourceWidth + sample - 1L) / sample
        val decodedHeight = (sourceHeight + sample - 1L) / sample
        if (decodedWidth <= edgeLimit &&
            decodedHeight <= edgeLimit &&
            decodedWidth * decodedHeight <= pixelLimit
        ) {
            return sample
        }
        if (sample >= (1 shl 29)) return sample
        sample *= 2
    }
}
