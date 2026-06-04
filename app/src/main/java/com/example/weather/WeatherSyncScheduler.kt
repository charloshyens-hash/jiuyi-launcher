package com.example.weather

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WeatherSyncScheduler {
    private const val WEATHER_SYNC_WORK_NAME = "WeatherSyncPeriodic"
    private const val WEATHER_SYNC_ONETIME_NAME = "WeatherSyncImmediate"

    fun scheduleWeatherSync(context: Context) {
        val prefs = com.example.LauncherPrefs(context)
        if (!prefs.isWeatherOnlineAllowed) {
            android.util.Log.d("WeatherSyncScheduler", "Online weather sync is disabled. Canceling any scheduled tasks.")
            cancelWeatherSync(context)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Schedule periodic update every 30 minutes
        val workRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WEATHER_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelWeatherSync(context: Context) {
        android.util.Log.d("WeatherSyncScheduler", "Canceling background weather sync periodic tasks.")
        WorkManager.getInstance(context).cancelUniqueWork(WEATHER_SYNC_WORK_NAME)
    }

    fun startImmediateSync(context: Context) {
        val prefs = com.example.LauncherPrefs(context)
        if (!prefs.isWeatherOnlineAllowed) {
            android.util.Log.d("WeatherSyncScheduler", "Online weather sync is disabled. Skipping immediate sync.")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WEATHER_SYNC_ONETIME_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
