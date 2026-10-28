package com.royalplus.launcher

import android.content.Intent
import android.graphics.Bitmap

data class LauncherApp(
    val label: String,
    val packageName: String,
    val launchIntent: Intent,
    val icon: Bitmap? = null
)
