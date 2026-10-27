package com.royalplus.launcher

import android.content.Intent

data class LauncherApp(
    val label: String,
    val packageName: String,
    val launchIntent: Intent
)
