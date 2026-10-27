package com.royalplus.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 10, 18)
        window.navigationBarColor = android.graphics.Color.rgb(8, 10, 18)

        setContent {
            MaterialTheme {
                RoyalHomeScreen(
                    apps = remember {
                        LauncherAppsRepository.load(
                            packageManager = packageManager,
                            ownPackageName = packageName
                        )
                    },
                    onLaunchApp = { app ->
                        try {
                            startActivity(app.launchIntent)
                        } catch (_: Exception) {
                            // The app may have been removed after the launcher list was loaded.
                        }
                    }
                )
            }
        }
    }
}
