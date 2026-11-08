package com.royalplus.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas

internal object LauncherAppsRepository {
    @Suppress("DEPRECATION")
    fun load(
        packageManager: PackageManager,
        ownPackageName: String
    ): List<LauncherApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        // Some TV apps expose only LEANBACK_LAUNCHER, while phone/tablet apps
        // commonly expose LAUNCHER. Manifest queries declare both categories.
        val activities = (
            packageManager.queryIntentActivities(launcherIntent, 0) +
                packageManager.queryIntentActivities(leanbackIntent, 0)
            )
            .distinctBy { info ->
                val activity = info.activityInfo
                "${activity.packageName}/${activity.name}"
            }

        return activities
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val appPackageName = activity.packageName
                if (appPackageName == ownPackageName) return@mapNotNull null

                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(appPackageName, activity.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                LauncherApp(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = appPackageName,
                    launchIntent = launchIntent,
                    icon = loadIconBitmap(info, packageManager)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun loadIconBitmap(
        info: ResolveInfo,
        packageManager: PackageManager
    ): Bitmap? {
        return try {
            val drawable = info.loadIcon(packageManager)
            val size = 96
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
            }
        } catch (_: Exception) {
            null
        }
    }
}
