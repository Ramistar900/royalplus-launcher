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
 * Checks saved-city weather every 30 minutes when connected. The repository's
 * three-hour freshness window and the shared server cache prevent unnecessary
 * provider requests; MET Norway is contacted only when a refresh is due and
 * the Open-Meteo primary source cannot provide a valid forecast.
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
    private const val LEGACY_UNIQUE_WORK_NAME = "royalplus-weekly-weather-refresh"
    private const val UNIQUE_WORK_NAME = "royalplus-weather-refresh-30m"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        // Remove the former 24-hour schedule so only the new 30-minute cache check remains.
        workManager.cancelUniqueWork(LEGACY_UNIQUE_WORK_NAME)
        val request = PeriodicWorkRequestBuilder<RoyalWeatherForecastWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
