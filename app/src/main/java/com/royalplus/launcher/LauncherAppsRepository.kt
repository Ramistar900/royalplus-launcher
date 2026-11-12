package com.royalplus.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock

internal object LauncherAppsRepository {
    private const val CACHE_TTL_MILLIS = 10_000L

    @Volatile
    private var cachedApps: List<LauncherApp>? = null
    @Volatile
    private var cachedForPackage: String? = null
    @Volatile
    private var cachedAtElapsedMillis = 0L

    @Suppress("DEPRECATION")
    @Synchronized
    fun load(
        packageManager: PackageManager,
        ownPackageName: String,
        forceRefresh: Boolean = false
    ): List<LauncherApp> {
        val now = SystemClock.elapsedRealtime()
        if (
            !forceRefresh &&
            cachedForPackage == ownPackageName &&
            now - cachedAtElapsedMillis < CACHE_TTL_MILLIS
        ) {
            cachedApps?.let { return it }
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        // Some TV apps expose only LEANBACK_LAUNCHER, while phone/tablet apps
        // commonly expose LAUNCHER. Merge both lists and keep the actual activity.
        val activities = (
            packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL) +
                packageManager.queryIntentActivities(leanbackIntent, PackageManager.MATCH_ALL)
            )
            .distinctBy { info ->
                val activity = info.activityInfo
                "${activity.packageName}/${activity.name}"
            }

        val loadedApps = activities
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
            .toList()

        cachedForPackage = ownPackageName
        cachedAtElapsedMillis = SystemClock.elapsedRealtime()
        cachedApps = loadedApps
        return loadedApps
    }

    @Synchronized
    fun invalidate() {
        cachedApps = null
        cachedForPackage = null
        cachedAtElapsedMillis = 0L
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
