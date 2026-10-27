package com.royalplus.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RoyalBlack = Color(0xFF080A12)
private val RoyalPanel = Color(0xFF171B29)
private val RoyalGold = Color(0xFFD6B45D)
private val RoyalPearl = Color(0xFFF5F0E4)
private val RoyalMuted = Color(0xFFB7B9C4)

data class LauncherApp(
    val label: String,
    val packageName: String,
    val launchIntent: Intent
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 10, 18)
        window.navigationBarColor = android.graphics.Color.rgb(8, 10, 18)

        setContent {
            MaterialTheme {
                RoyalHomeScreen(
                    apps = remember { loadLauncherApps() },
                    onLaunchApp = { app ->
                        try {
                            startActivity(app.launchIntent)
                        } catch (_: Exception) {
                            // An app may be removed after the launcher list is loaded.
                        }
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadLauncherApps(): List<LauncherApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val packageName = info.activityInfo.packageName
                if (packageName == this.packageName) return@mapNotNull null
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@mapNotNull null
                LauncherApp(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    launchIntent = launchIntent
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

@Composable
private fun RoyalHomeScreen(
    apps: List<LauncherApp>,
    onLaunchApp: (LauncherApp) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = RoyalBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RoyalBlack)
                .padding(horizontal = 40.dp, vertical = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ROYAL+",
                        color = RoyalGold,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "SMART TV EXPERIENCE",
                        color = RoyalMuted,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = remember { java.text.SimpleDateFormat("EEE, dd MMM  •  HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) },
                    color = RoyalPearl,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your screen. Your kingdom.",
                        color = RoyalPearl,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose an app to begin",
                        color = RoyalMuted,
                        fontSize = 15.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, RoyalGold.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text("HOME", color = RoyalGold, fontSize = 12.sp, letterSpacing = 2.sp)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No launchable apps found on this device yet.",
                        color = RoyalMuted,
                        fontSize = 18.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        Card(
                            onClick = { onLaunchApp(app) },
                            modifier = Modifier
                                .height(112.dp)
                                .focusable(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = RoyalPanel),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = app.label,
                                    color = RoyalPearl,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "OPEN APP  ›",
                                    color = RoyalGold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
