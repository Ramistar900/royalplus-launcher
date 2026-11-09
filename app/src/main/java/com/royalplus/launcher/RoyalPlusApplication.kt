package com.royalplus.launcher

import android.app.Application
import android.content.Context
import java.io.File

/**
 * Saves the last startup stage and the most recent uncaught exception locally.
 * The next launch can display the report without ADB or a computer.
 */
class RoyalPlusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val preferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val lastStage = preferences.getString(KEY_STAGE, "unknown") ?: "unknown"
                val report = buildString {
                    appendLine("ROYAL+ Launcher crash report")
                    appendLine("Android SDK: ${android.os.Build.VERSION.SDK_INT}")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Last startup stage: $lastStage")
                    appendLine()
                    append(error.stackTraceToString())
                }
                File(filesDir, REPORT_FILE).writeText(report)
                preferences.edit().putBoolean(KEY_PENDING, true).commit()
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    companion object {
        private const val PREFS = "royalplus_crash_diagnostics"
        private const val KEY_STAGE = "last_stage"
        private const val KEY_PENDING = "pending"
        private const val REPORT_FILE = "royalplus-last-crash.txt"

        fun markStage(context: Context, stage: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_STAGE, stage).commit()
        }

        fun readPendingReport(context: Context): String? {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!preferences.getBoolean(KEY_PENDING, false)) return null
            return runCatching {
                File(context.filesDir, REPORT_FILE).takeIf { it.exists() }?.readText()
            }.getOrNull() ?: "تم تسجيل انهيار، لكن تعذرت قراءة تفاصيل التقرير."
        }

        fun clearPendingReport(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_PENDING, false).remove(KEY_STAGE).commit()
            runCatching { File(context.filesDir, REPORT_FILE).delete() }
        }
    }
}
