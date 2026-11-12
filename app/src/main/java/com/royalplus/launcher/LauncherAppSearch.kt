package com.royalplus.launcher

import java.util.Locale

internal object LauncherAppSearch {
    fun normalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ة', 'ه')
        .replace('ى', 'ي')
}
