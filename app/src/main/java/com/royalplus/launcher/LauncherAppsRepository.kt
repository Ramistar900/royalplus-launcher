package com.royalplus.launcher

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
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val appPackageName = info.activityInfo.packageName
                if (appPackageName == ownPackageName) return@mapNotNull null

                val launchIntent = packageManager.getLaunchIntentForPackage(appPackageName)
                    ?: return@mapNotNull null

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
