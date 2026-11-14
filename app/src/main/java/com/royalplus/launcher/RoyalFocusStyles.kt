package com.royalplus.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Adds a visible glass-blue focus surface with a gold frame for remote navigation.
 * Place before clickable() so the observer sees clickable's built-in focus target.
 */
@Composable
internal fun royalGlassFocus(shape: Shape = RoundedCornerShape(12.dp)): Modifier {
    var focused by remember { mutableStateOf(false) }
    return Modifier
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0x7046C8FF),
                                Color(0x4A278FD0),
                                Color(0x22224F79)
                            )
                        ),
                        shape
                    )
                    .border(2.dp, Color(0xFFFFD36A), shape)
                    .border(1.dp, Color(0xFF8FEAFF).copy(alpha = 0.92f), shape)
            } else {
                Modifier
            }
        )
}
