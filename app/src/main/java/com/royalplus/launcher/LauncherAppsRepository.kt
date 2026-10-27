package com.royalplus.launcher

import android.content.Intent
import android.content.pm.PackageManager

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
                    launchIntent = launchIntent
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
