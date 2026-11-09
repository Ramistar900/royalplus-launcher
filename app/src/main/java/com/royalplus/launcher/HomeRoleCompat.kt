package com.royalplus.launcher

import android.annotation.TargetApi
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Isolates Android 10 RoleManager references from the API 26 launcher path.
 */
internal object HomeRoleCompat {
    fun isRoyalPlusDefaultHome(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { isRoyalPlusDefaultHomeApi29(context) }.getOrNull()
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun isRoyalPlusDefaultHomeApi29(context: Context): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    fun openDefaultHomeSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching { openDefaultHomeSettingsApi29(context) }.getOrDefault(false)
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun openDefaultHomeSettingsApi29(context: Context): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return false
        val intent = if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            Intent(Settings.ACTION_HOME_SETTINGS)
        } else {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        context.startActivity(intent)
        return true
    }
}
