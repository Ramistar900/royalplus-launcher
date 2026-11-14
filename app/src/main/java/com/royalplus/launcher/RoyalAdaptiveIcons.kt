package com.royalplus.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royal.adaptive.LocalRoyalAdaptiveProfile

/**
 * Shared icon-glyph sizing for every launcher section.
 *
 * [availableSize] can be supplied when an icon sits inside a fixed visual slot;
 * tap targets and surrounding layout dimensions are intentionally left unchanged.
 */
@Composable
internal fun royalAdaptiveIconSize(
    base: Dp,
    minSize: Dp = 0.dp,
    maxSize: Dp = base * 1.5f,
    availableSize: Dp? = null
): Dp {
    val adaptive = LocalRoyalAdaptiveProfile.current.iconSize(base)
    val cap = minOf(maxSize, availableSize ?: maxSize).coerceAtLeast(minSize)
    return adaptive.coerceAtLeast(minSize).coerceAtMost(cap)
}
