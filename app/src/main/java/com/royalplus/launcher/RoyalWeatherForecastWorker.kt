package com.royalplus.launcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Refreshes the complete rolling seven-day forecast once per day when a network
 * is available. The repository only replaces the saved forecast after a valid
 * seven-day response, so a failed/short response cannot erase offline data.
 */
internal class RoyalWeatherForecastWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {
    override fun doWork(): Result =
        if (RoyalWeatherRepository.refreshStoredWeather(applicationContext)) {
            Result.success()
        } else {
            Result.retry()
        }
}

internal object RoyalWeatherForecastScheduler {
    private const val UNIQUE_WORK_NAME = "royalplus-weekly-weather-refresh"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RoyalWeatherForecastWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
