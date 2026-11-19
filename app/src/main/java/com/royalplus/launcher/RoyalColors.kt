package com.royalplus.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal object RoyalColors {
    var isLightTheme by mutableStateOf(false)
        private set

    fun applyLightTheme(enabled: Boolean) {
        isLightTheme = enabled
    }

    val Black: Color
        get() = if (isLightTheme) Color(0xFFF3EFE5) else Color(0xFF080A12)
    val Panel: Color
        get() = if (isLightTheme) Color(0xFFF8F4EA) else Color(0xFF111620)
    val Surface: Color
        get() = if (isLightTheme) Color(0xFFE9E1D3) else Color(0xFF080A0F)
    val Overlay: Color
        get() = if (isLightTheme) Color(0xD9F3EFE5) else Color(0xF5080A10)
    val Gold: Color
        get() = if (isLightTheme) Color(0xFF94701F) else Color(0xFFD6B45D)
    val Pearl: Color
        get() = if (isLightTheme) Color(0xFF26313D) else Color(0xFFF5F0E4)
    val Muted: Color
        get() = if (isLightTheme) Color(0xFF68717C) else Color(0xFFB7B9C4)
    val Turquoise: Color
        get() = if (isLightTheme) Color(0xFF147E78) else Color(0xFF54D3C4)
    val Activity: Color
        get() = Turquoise
    val Green: Color
        get() = if (isLightTheme) Color(0xFF20804B) else Color(0xFF55D28B)
    val Blue: Color
        get() = if (isLightTheme) Color(0xFF276BAE) else Color(0xFF79BFFF)
}

@Composable
internal fun RoyalAppTheme(content: @Composable () -> Unit) {
    val light = RoyalColors.isLightTheme
    val scheme = if (light) {
        lightColorScheme(
            primary = RoyalColors.Gold,
            onPrimary = Color.White,
            secondary = RoyalColors.Turquoise,
            onSecondary = Color.White,
            background = RoyalColors.Black,
            onBackground = RoyalColors.Pearl,
            surface = RoyalColors.Panel,
            onSurface = RoyalColors.Pearl,
            surfaceVariant = RoyalColors.Surface,
            onSurfaceVariant = RoyalColors.Muted
        )
    } else {
        darkColorScheme(
            primary = RoyalColors.Gold,
            onPrimary = Color(0xFF15120A),
            secondary = RoyalColors.Turquoise,
            onSecondary = Color(0xFF06211F),
            background = RoyalColors.Black,
            onBackground = RoyalColors.Pearl,
            surface = RoyalColors.Panel,
            onSurface = RoyalColors.Pearl,
            surfaceVariant = RoyalColors.Surface,
            onSurfaceVariant = RoyalColors.Muted
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
