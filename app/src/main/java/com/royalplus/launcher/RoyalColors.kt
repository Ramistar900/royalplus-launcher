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
        get() = if (isLightTheme) Color(0xFFFFFCF6) else Color(0xFF171B29)
    val Surface: Color
        get() = if (isLightTheme) Color(0xFFECE6D9) else Color(0xFF080A0F)
    val Overlay: Color
        get() = if (isLightTheme) Color(0xD9F3EFE5) else Color(0xF5080A10)
    val Gold: Color
        get() = if (isLightTheme) Color(0xFF8B681F) else Color(0xFFD6B45D)
    val Pearl: Color
        get() = if (isLightTheme) Color(0xFF202936) else Color(0xFFF5F0E4)
    val Muted: Color
        get() = if (isLightTheme) Color(0xFF626D7A) else Color(0xFFB7B9C4)
    val Turquoise: Color
        get() = if (isLightTheme) Color(0xFF167C78) else Color(0xFF62D4C4)
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
