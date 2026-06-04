package com.example.weather

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.LauncherPrefs

class WeatherSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        android.util.Log.d("WeatherSyncWorker", "Starting background weather sync...")
        val prefs = LauncherPrefs(applicationContext)
        if (!prefs.isWeatherOnlineAllowed) {
            android.util.Log.d("WeatherSyncWorker", "Online weather sync is disabled by user. Skipping background refresh.")
            // Cancel further tasks to be safe
            WeatherSyncScheduler.cancelWeatherSync(applicationContext)
            return Result.success()
        }
        val weatherRepo = WeatherRepository.getInstance(applicationContext)
        
        return try {
            val city = prefs.customCity.ifEmpty { "北京" }
            val lat = if (prefs.customLat != 999f && prefs.customLng != 999f) prefs.customLat.toDouble() else null
            val lng = if (prefs.customLat != 999f && prefs.customLng != 999f) prefs.customLng.toDouble() else null
            
            // forceRefresh = true to ensure we hit network on background scheduled updates
            val result = weatherRepo.fetchWeatherForCityOnline(
                city = city,
                passLat = lat,
                passLng = lng,
                forceRefresh = true
            )
            
            android.util.Log.d("WeatherSyncWorker", "Weather sync done: ${result.city} -> ${result.weatherText} (${result.weatherTemp})")
            
            // Send update broadcast
            val intent = Intent("com.example.LAUNCHER_WEATHER_UPDATE")
            applicationContext.sendBroadcast(intent)
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WeatherSyncWorker", "Weather sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
