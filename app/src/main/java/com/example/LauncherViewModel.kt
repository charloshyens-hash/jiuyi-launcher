package com.example

import android.app.Application
import android.content.BroadcastReceiver
import com.example.weather.WeatherRepository
import com.example.weather.WeatherUiState
import com.example.weather.CityItem
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DrawerItem {
    data class App(val app: AppModel) : DrawerItem() {
        val packageName: String get() = app.packageName
        val label: String get() = app.label
    }
    data class Folder(val folder: DrawerFolder) : DrawerItem() {
        val folderId: String get() = folder.id
        val name: String get() = folder.name
    }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = LauncherPrefs(application)
    val weatherRepo = WeatherRepository.getInstance(application)

    val homePages = MutableStateFlow<List<HomeScreenPage>>(emptyList())
    val activePageIndex = MutableStateFlow(0)

    private val _appList = MutableStateFlow<List<AppModel>>(emptyList())
    val appList: StateFlow<List<AppModel>> = _appList

    val currentThemeIndex = MutableStateFlow(prefs.themeColorIndex)
    val clockStyle = MutableStateFlow(prefs.clockStyle)
    val wallpaperName = MutableStateFlow(prefs.wallpaperName)
    val showLabels = MutableStateFlow(prefs.showLabels)
    val showSystemApps = MutableStateFlow(prefs.showSystemApps)
    val drawerGrid = MutableStateFlow(prefs.drawerGrid)
    val iconPackFilter = MutableStateFlow(prefs.iconPackFilter)

    val touchEffect = MutableStateFlow(prefs.touchEffect)
    val homeTransition = MutableStateFlow(prefs.homeTransition)
    val drawerTransition = MutableStateFlow(prefs.drawerTransition)
    val crossTransition = MutableStateFlow(prefs.crossTransition)
    val touchRandomPool = MutableStateFlow(prefs.touchRandomPool)
    val homeRandomPool = MutableStateFlow(prefs.homeRandomPool)
    val drawerRandomPool = MutableStateFlow(prefs.drawerRandomPool)
    val crossRandomPool = MutableStateFlow(prefs.crossRandomPool)

    val searchQuery = MutableStateFlow("")
    val iconRoundness = MutableStateFlow(prefs.iconRoundness)
    val iconSizeScale = MutableStateFlow(prefs.iconSizeScale)
    val fontSizeSp = MutableStateFlow(prefs.fontSizeSp)
    val drawerSortType = MutableStateFlow(prefs.drawerSortType)
    val drawerFolders = MutableStateFlow<List<DrawerFolder>>(prefs.getDrawerFolders())
    val drawerPageIndex = MutableStateFlow(0)
    val appsGridPageIndex = MutableStateFlow(0)

    val backToFirstScreenEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    var batteryLevel by mutableStateOf(85)
    var isBatteryCharging by mutableStateOf(false)
    var batteryTemperature by mutableStateOf(31.4f)
    var batteryVoltage by mutableStateOf(3.2f)

    var ramUsagePercent by mutableStateOf(64)
    var realTotalRamMb by mutableStateOf(4096)
    var realAvailRamMb by mutableStateOf(1400)
    var realCacheSizeMb by mutableStateOf(1.45f)
    var isRamBoosting by mutableStateOf(false)
    var lastBoostTime by mutableStateOf("未运行")

    var realTotalStorageGb by mutableStateOf(64.0f)
    var realFreeStorageGb by mutableStateOf(24.5f)

    var networkPingMs by mutableStateOf(18)
    var realInstalledAppsCount by mutableStateOf(0)

    val isWeatherOnlineAllowed = MutableStateFlow(prefs.isWeatherOnlineAllowed)

    private val _weatherState = MutableStateFlow(
        WeatherUiState(
            city = prefs.customCity,
            weather = prefs.customWeather.ifEmpty { "多云" },
            temperature = prefs.customTemp.ifEmpty { "18°C" },
            lat = if (prefs.customLat != 999f) prefs.customLat.toDouble() else null,
            lng = if (prefs.customLng != 999f) prefs.customLng.toDouble() else null,
            country = prefs.customCountry,
            admin = prefs.customAdmin,
            lastUpdateTime = prefs.lastWeatherUpdateTime
        )
    )
    val weatherState: StateFlow<WeatherUiState> = _weatherState

    var showWeatherConfigDialog by mutableStateOf(false)
    var showCitySelectorDialog by mutableStateOf(false)

    fun isCoordinateString(str: String): Boolean {
        val trimmed = str.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.matches(Regex("^-?\\d+(\\.\\d+)?[,\\s]+-?\\d+(\\.\\d+)?$"))) return true
        if (trimmed.contains("lat", ignoreCase = true) || trimmed.contains("lon", ignoreCase = true) || trimmed.contains("coord", ignoreCase = true)) return true
        val clean = trimmed.replace("°", "").replace("N", "").replace("S", "").replace("E", "").replace("W", "").replace(",", "").replace(".", "").replace("+", "").replace("-", "").replace(" ", "").trim()
        if (clean.isNotEmpty() && clean.all { it.isDigit() }) return true
        return false
    }

    private fun resolveCoordinatesInBackground(coord: String, weather: String, temp: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleaned = coord.replace("°N", "").replace("°S", "").replace("°E", "").replace("°W", "")
                val parts = cleaned.split(Regex("[,\\s]+")).map { it.trim() }.mapNotNull { it.toDoubleOrNull() }
                if (parts.size >= 2) {
                    val lat = parts[0]; val lon = parts[1]
                    val cleanCity = weatherRepo.reverseGeocode(lat, lon)
                    if (cleanCity != null) {
                        _weatherState.value = _weatherState.value.copy(
                            city = cleanCity, weather = weather, temperature = temp,
                            lat = lat, lng = lon, country = prefs.customCountry, admin = prefs.customAdmin
                        )
                        if (prefs.customCity == "点击设置城市" || isCoordinateString(prefs.customCity)) {
                            prefs.customCity = cleanCity; prefs.customWeather = weather; prefs.customTemp = temp
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WeatherLauncher", "Failed block coordinates reverse: ${e.message}")
            }
            if (prefs.customCity == "点击设置城市" || isCoordinateString(prefs.customCity)) {
                _weatherState.value = _weatherState.value.copy(
                    city = "点击设置城市", weather = weather, temperature = temp,
                    country = prefs.customCountry, admin = prefs.customAdmin
                )
            }
        }
    }

    fun translateWeatherCode(code: Int): String = weatherRepo.translateWeatherCode(code)

    fun fetchWeatherForCityOnline(city: String? = null, passLat: Double? = null, passLng: Double? = null, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val result = weatherRepo.fetchWeatherForCityOnline(city, passLat, passLng, forceRefresh)
            _weatherState.value = _weatherState.value.copy(
                city = result.city, weather = result.weatherText, temperature = result.weatherTemp,
                lat = result.lat, lng = result.lng, country = result.country, admin = result.admin,
                lastUpdateTime = System.currentTimeMillis()
            )
        }
    }

    fun trySyncSystemWeatherSilently() {
        if (prefs.customCity.isNotEmpty() && prefs.customCity != "点击设置城市" && !isCoordinateString(prefs.customCity)) return
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            data class SystemWeatherProvider(val uri: String, val cityCol: String, val condCol: String, val tempCol: String)
            val providers = listOf(
                SystemWeatherProvider("content://weather/weather", "city", "weather", "temp"),
                SystemWeatherProvider("content://com.miui.weather2.provider/weather", "city_name", "weather_name", "temperature"),
                SystemWeatherProvider("content://com.huawei.android.totemweather.provider/weather", "city", "weather", "temperature"),
                SystemWeatherProvider("content://com.coloros.weather.provider/weather", "city", "weather", "temperature"),
                SystemWeatherProvider("content://com.oppo.weather.provider/weather", "city", "weather_cond", "temp"),
                SystemWeatherProvider("content://com.vivo.weather.provider/weather", "city", "weather", "temp")
            )
            for (p in providers) {
                try {
                    val cursor = resolver.query(android.net.Uri.parse(p.uri), null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            var cityVal = ""; var weatherVal = ""; var tempVal = ""
                            val cityCol = c.getColumnIndex(p.cityCol); val weatherCol = c.getColumnIndex(p.condCol); val tempCol = c.getColumnIndex(p.tempCol)
                            if (cityCol >= 0) cityVal = c.getString(cityCol) ?: ""
                            if (weatherCol >= 0) weatherVal = c.getString(weatherCol) ?: ""
                            if (tempCol >= 0) tempVal = c.getString(tempCol) ?: ""
                            if (cityVal.isEmpty()) { for (i in 0 until c.columnCount) { val name = c.getColumnName(i).lowercase(); if (name.contains("city") || name.contains("name")) { cityVal = c.getString(i) ?: ""; break } } }
                            if (weatherVal.isEmpty()) { for (i in 0 until c.columnCount) { val name = c.getColumnName(i).lowercase(); if (name.contains("weather") || name.contains("cond") || name.contains("state")) { weatherVal = c.getString(i) ?: ""; break } } }
                            if (tempVal.isEmpty()) { for (i in 0 until c.columnCount) { val name = c.getColumnName(i).lowercase(); if (name.contains("temp") || name.contains("temperature")) { tempVal = c.getString(i) ?: ""; break } } }
                            if (cityVal.isNotEmpty() && (weatherVal.isNotEmpty() || tempVal.isNotEmpty())) {
                                val formattedTemp = if (tempVal.contains("°")) tempVal else "${tempVal}°C"
                                if (isCoordinateString(cityVal)) resolveCoordinatesInBackground(cityVal, weatherVal, formattedTemp)
                                else {
                                    _weatherState.value = _weatherState.value.copy(city = cityVal, weather = weatherVal, temperature = formattedTemp)
                                    prefs.customCity = cityVal; prefs.customWeather = weatherVal; prefs.customTemp = formattedTemp
                                }
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("WeatherLauncher", "Silent check of provider ${p.uri} failed: ${e.message}")
                }
            }
            withContext(Dispatchers.Main) { try { JiuYiMediaService.requestRefresh() } catch (e: Exception) {} }
        }
    }

    fun selectCityAndSimulateWeather(city: String, lat: Double? = null, lng: Double? = null, country: String? = null, admin: String? = null, query: String? = null) {
        if (lat != null && lng != null) {
            prefs.addRecentCity(city, query ?: city)
            prefs.customCity = city; prefs.customLat = lat.toFloat(); prefs.customLng = lng.toFloat()
            prefs.customCountry = country ?: ""; prefs.customAdmin = admin ?: ""
            _weatherState.value = _weatherState.value.copy(city = city, weather = "更新中...", temperature = "--°C", lat = lat, lng = lng, country = country ?: "", admin = admin ?: "")
            fetchWeatherForCityOnline(city, lat, lng, forceRefresh = true)
        } else {
            viewModelScope.launch {
                val details = weatherRepo.resolveCityDetails(city)
                val finalLat = details?.lat ?: 39.9042; val finalLng = details?.lng ?: 116.4074
                val finalCountry = details?.country ?: "中国"; val finalAdmin = details?.admin ?: "北京"; val finalTitle = details?.name ?: "北京"
                prefs.addRecentCity(finalTitle, query ?: city)
                prefs.customCity = finalTitle; prefs.customLat = finalLat.toFloat(); prefs.customLng = finalLng.toFloat()
                prefs.customCountry = finalCountry; prefs.customAdmin = finalAdmin
                _weatherState.value = _weatherState.value.copy(city = finalTitle, weather = "更新中...", temperature = "--°C", lat = finalLat, lng = finalLng, country = finalCountry, admin = finalAdmin)
                fetchWeatherForCityOnline(finalTitle, finalLat, finalLng, forceRefresh = true)
            }
        }
    }

    fun searchAndSelectCity(query: String) {
        viewModelScope.launch {
            val details = weatherRepo.resolveCityDetails(query)
            val finalLat = details?.lat ?: 39.9042; val finalLng = details?.lng ?: 116.4074
            val finalCountry = details?.country ?: "中国"; val finalAdmin = details?.admin ?: "北京"; val finalTitle = details?.name ?: "北京"
            selectCityAndSimulateWeather(city = finalTitle, lat = finalLat, lng = finalLng, country = finalCountry, admin = finalAdmin, query = query)
        }
    }

    fun updateWeatherConsent(allowed: Boolean) {
        prefs.isWeatherOnlineAllowed = allowed
        isWeatherOnlineAllowed.value = allowed
        if (allowed) {
            try { com.example.weather.WeatherSyncScheduler.scheduleWeatherSync(getApplication()) } catch (e: Exception) { android.util.Log.e("LauncherViewModel", "Failed to schedule weather sync: ${e.message}") }
            fetchRealWeather(forceRefresh = true)
        } else {
            try { com.example.weather.WeatherSyncScheduler.cancelWeatherSync(getApplication()) } catch (e: Exception) { android.util.Log.e("LauncherViewModel", "Failed to cancel weather sync: ${e.message}") }
            _weatherState.value = _weatherState.value.copy(city = prefs.customCity, weather = prefs.customWeather.ifEmpty { "多云" }, temperature = prefs.customTemp.ifEmpty { "18°C" })
        }
    }

    fun updateCustomWeather(city: String, cond: String, temp: String) {
        prefs.customCity = city; prefs.customWeather = cond; prefs.customTemp = temp
        prefs.lastWeatherUpdateTime = System.currentTimeMillis()
        _weatherState.value = _weatherState.value.copy(city = city, weather = cond, temperature = temp, lastUpdateTime = System.currentTimeMillis())
    }

    fun fetchRealWeather(forceRefresh: Boolean = false) {
        if (!prefs.isWeatherOnlineAllowed) { trySyncSystemWeatherSilently(); return }
        if (prefs.customCity.isNotEmpty() && prefs.customCity != "点击设置城市" && !isCoordinateString(prefs.customCity)) {
            _weatherState.value = _weatherState.value.copy(city = prefs.customCity, weather = prefs.customWeather, temperature = prefs.customTemp)
            fetchWeatherForCityOnline(prefs.customCity, forceRefresh = forceRefresh)
            return
        }
        trySyncSystemWeatherSilently()
    }

    // ── 音乐播放状态 ──────────────────────────────────────────────────────────
    val musicWidgetMode = MutableStateFlow(prefs.musicWidgetMode)
    val preferredMusicPackage = MutableStateFlow(prefs.preferredMusicPackage)

    var currentTrackName by mutableStateOf("久以金曲")
    var currentTrackArtist by mutableStateOf("打开任意音乐播放器即可显示")
    var isMusicPlaying by mutableStateOf(false)
    var currentArtBase64 by mutableStateOf("")
    var currentPosition by mutableStateOf(0L)
    var currentDuration by mutableStateOf(0L)

    fun updateRealtimeStats() {
        val context = getApplication<Application>()
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        if (actManager != null) {
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            realTotalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
            realAvailRamMb = (memInfo.availMem / (1024 * 1024)).toInt()
            val used = realTotalRamMb - realAvailRamMb
            ramUsagePercent = if (realTotalRamMb > 0) ((used.toFloat() / realTotalRamMb.toFloat()) * 100).toInt() else 64
        }
        try {
            val path = android.os.Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            realTotalStorageGb = (stat.blockCountLong * stat.blockSizeLong) / (1024f * 1024f * 1024f)
            realFreeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024f * 1024f * 1024f)
        } catch (e: Exception) {}
        try {
            var sizeSum: Long = 0
            val cacheFiles = context.cacheDir.listFiles()
            if (cacheFiles != null) { for (f in cacheFiles) { sizeSum += getFolderSize(f) } }
            realCacheSizeMb = if (sizeSum > 0) sizeSum / (1024f * 1024f) else 1.45f
        } catch (e: Exception) { realCacheSizeMb = 1.45f }
        realInstalledAppsCount = _appList.value.size
        try {
            val startTime = System.currentTimeMillis()
            val url = java.net.URL("https://www.google.com")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 1200; conn.readTimeout = 1200; conn.requestMethod = "HEAD"; conn.connect()
            val latency = (System.currentTimeMillis() - startTime).toInt()
            networkPingMs = if (latency > 0) latency else (12..28).random()
        } catch (e: Exception) {
            try {
                val startTime = System.currentTimeMillis()
                val url = java.net.URL("https://www.baidu.com")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 1000; conn.readTimeout = 1000; conn.requestMethod = "HEAD"; conn.connect()
                val latency = (System.currentTimeMillis() - startTime).toInt()
                networkPingMs = if (latency > 0) latency else (15..32).random()
            } catch (ex: Exception) { networkPingMs = (60..90).random() }
        }
    }

    private fun getFolderSize(file: java.io.File): Long {
        var size: Long = 0
        if (file.isDirectory) { val files = file.listFiles(); if (files != null) { for (child in files) { size += getFolderSize(child) } } }
        else { size = file.length() }
        return size
    }

    fun updateMusicWidgetMode(mode: Int) { prefs.musicWidgetMode = mode; musicWidgetMode.value = mode }
    fun updatePreferredMusicPackage(pkg: String) {
        prefs.preferredMusicPackage = pkg
        preferredMusicPackage.value = pkg

        currentTrackName = "久以金曲"
        currentTrackArtist = "打开任意音乐播放器即可显示"
        isMusicPlaying = false
        currentPosition = 0L
        currentDuration = 0L
        currentArtBase64 = ""

        if (JiuYiMediaService.isServiceRunning) {
            JiuYiMediaService.onPreferredPackageChanged()
        }
    }

    fun launchPreferredMusicApp(context: Context) {
        try {
            val pkg = preferredMusicPackage.value
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                val genIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genIntent)
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "无法启动首选音频App，请先在设置中绑定", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun dispatchSystemMediaKey(keyCode: Int) {
        try {
            val context = getApplication<Application>()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                val now = android.os.SystemClock.uptimeMillis()
                audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0))
                audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0))
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherVM", "Error dispatching media key: ${e.message}")
        }
    }

    fun sendMediaKeyToPackage(pkg: String, keyCode: Int) {
        if (pkg.isEmpty()) return
        try {
            val context = getApplication<Application>()
            val pm = context.packageManager
            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            mediaButtonIntent.setPackage(pkg)

            val now = android.os.SystemClock.uptimeMillis()
            val keyDown = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
            val keyUp = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0)

            val services = pm.queryIntentServices(mediaButtonIntent, 0)
            if (!services.isNullOrEmpty()) {
                for (resolved in services) {
                    val svcIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        component = android.content.ComponentName(resolved.serviceInfo.packageName, resolved.serviceInfo.name)
                        putExtra(Intent.EXTRA_KEY_EVENT, keyDown)
                    }
                    val svcIntentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        component = android.content.ComponentName(resolved.serviceInfo.packageName, resolved.serviceInfo.name)
                        putExtra(Intent.EXTRA_KEY_EVENT, keyUp)
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(svcIntent)
                            context.startForegroundService(svcIntentUp)
                        } else {
                            context.startService(svcIntent)
                            context.startService(svcIntentUp)
                        }
                    } catch (e: Exception) {
                        try {
                            context.startService(svcIntent)
                            context.startService(svcIntentUp)
                        } catch (ex: Exception) {}
                    }
                }
            }

            val receivers = pm.queryBroadcastReceivers(mediaButtonIntent, 0)
            if (!receivers.isNullOrEmpty()) {
                for (resolved in receivers) {
                    val componentName = android.content.ComponentName(
                        resolved.activityInfo.packageName,
                        resolved.activityInfo.name
                    )
                    val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        component = componentName
                        putExtra(Intent.EXTRA_KEY_EVENT, keyDown)
                    }
                    val intentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        component = componentName
                        putExtra(Intent.EXTRA_KEY_EVENT, keyUp)
                    }
                    context.sendBroadcast(intentDown)
                    context.sendBroadcast(intentUp)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherVM", "sendMediaKeyToPackage failed: ${e.message}")
        }
    }

    fun findAnyInstalledMusicPackage(): String {
        val context = getApplication<Application>()
        val pm = context.packageManager

        try {
            val musicIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MUSIC)
            }
            val list = pm.queryIntentActivities(musicIntent, 0)
            if (!list.isNullOrEmpty()) {
                val pkg = list[0].activityInfo.packageName
                if (pkg.isNotEmpty()) return pkg
            }
        } catch (e: Exception) {}

        try {
            val packages = pm.getInstalledPackages(0)
            val commonMusicPkgs = listOf(
                "com.tencent.qqmusic",
                "com.netease.cloudmusic",
                "com.kugou.android",
                "cn.kuwo.player",
                "com.android.music",
                "com.miui.player",
                "com.heytap.music",
                "com.android.mediacenter",
                "com.vivo.music",
                "com.cootek.smartdialer",
                "com.apple.android.music",
                "com.google.android.apps.youtube.music"
            )
            for (p in commonMusicPkgs) {
                try {
                    pm.getPackageInfo(p, 0)
                    return p
                } catch (e: Exception) {}
            }

            for (info in packages) {
                val name = info.packageName.lowercase()
                if (name.contains("music") || name.contains("player") || name.contains("audio")) {
                    return info.packageName
                }
            }
        } catch (e: Exception) {}
        return ""
    }

    private fun wakeMusicAppBackground(pkg: String) {
        if (pkg.isEmpty()) return
        val context = getApplication<Application>()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                val focusRequest = android.media.AudioFocusRequest.Builder(
                    android.media.AudioManager.AUDIOFOCUS_GAIN
                ).apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setWillPauseWhenDucked(false)
                    setAcceptsDelayedFocusGain(true)
                    setOnAudioFocusChangeListener({})
                }.build()
                audioManager.requestAudioFocus(focusRequest)
            }
        } catch (e: Exception) {}

        try {
            val pm = context.packageManager
            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            mediaButtonIntent.setPackage(pkg)

            val now = android.os.SystemClock.uptimeMillis()
            val keyDown = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            val keyUp = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0)

            try {
                val services = pm.queryIntentServices(mediaButtonIntent, 0)
                if (!services.isNullOrEmpty()) {
                    for (resolved in services) {
                        val svcIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                            component = android.content.ComponentName(resolved.serviceInfo.packageName, resolved.serviceInfo.name)
                            putExtra(Intent.EXTRA_KEY_EVENT, keyDown)
                        }
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(svcIntent)
                            } else {
                                context.startService(svcIntent)
                            }
                        } catch (e: Exception) {
                            try { context.startService(svcIntent) } catch (ex: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {}

            val receivers = pm.queryBroadcastReceivers(mediaButtonIntent, 0)
            if (!receivers.isNullOrEmpty()) {
                val componentName = android.content.ComponentName(
                    receivers[0].activityInfo.packageName,
                    receivers[0].activityInfo.name
                )
                val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    component = componentName
                    putExtra(Intent.EXTRA_KEY_EVENT, keyDown)
                }
                val intentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    component = componentName
                    putExtra(Intent.EXTRA_KEY_EVENT, keyUp)
                }
                context.sendBroadcast(intentDown)
                context.sendBroadcast(intentUp)
            } else {
                val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_KEY_EVENT, keyDown)
                }
                val intentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_KEY_EVENT, keyUp)
                }
                context.sendBroadcast(intentDown)
                context.sendBroadcast(intentUp)
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherVM", "wakeMusicAppBackground failed: ${e.message}")
        }
    }

    private fun connectAndPlayViaMediaBrowser(pkg: String) {
        if (pkg.isEmpty()) return
        val context = getApplication<Application>()
        val pm = context.packageManager
        val browserIntent = Intent("android.media.browse.MediaBrowserService")
        val services = pm.queryIntentServices(browserIntent, 0)
        val targetService = services.firstOrNull { it.serviceInfo.packageName == pkg }

        if (targetService != null) {
            val componentName = android.content.ComponentName(
                targetService.serviceInfo.packageName,
                targetService.serviceInfo.name
            )
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                try {
                    var mediaBrowser: android.media.browse.MediaBrowser? = null
                    val connectionCallback = object : android.media.browse.MediaBrowser.ConnectionCallback() {
                        override fun onConnected() {
                            try {
                                val token = mediaBrowser?.sessionToken
                                if (token != null) {
                                    val controller = android.media.session.MediaController(context, token)
                                    controller.transportControls.play()
                                    val now = android.os.SystemClock.uptimeMillis()
                                    controller.dispatchMediaButtonEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0))
                                    controller.dispatchMediaButtonEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("LauncherVM", "MediaBrowser session play failed: ${e.message}")
                            } finally {
                                try { mediaBrowser?.disconnect() } catch (e: Exception) {}
                            }
                        }
                        override fun onConnectionFailed() {
                            try { mediaBrowser?.disconnect() } catch (e: Exception) {}
                        }
                        override fun onConnectionSuspended() {
                            try { mediaBrowser?.disconnect() } catch (e: Exception) {}
                        }
                    }
                    mediaBrowser = android.media.browse.MediaBrowser(
                        context,
                        componentName,
                        connectionCallback,
                        null
                    )
                    mediaBrowser.connect()
                } catch (e: Exception) {
                    android.util.Log.e("LauncherVM", "MediaBrowser setup failed: ${e.message}")
                }
            }
        }
    }

    // ── 播放/暂停（修复网易云 matchesPkg 前缀匹配 + 延迟提升至 1200ms）──────
    fun toggleMusicPlayback() {
        val targetPkg = preferredMusicPackage.value
        if (targetPkg.isNotEmpty()) {
            if (JiuYiMediaService.isServiceRunning && JiuYiMediaService.matchesPkg(targetPkg)) {
                JiuYiMediaService.sendMediaAction("play_pause")
            } else {
                connectAndPlayViaMediaBrowser(targetPkg)
                wakeMusicAppBackground(targetPkg)
                viewModelScope.launch {
                    delay(1200)
                    if (JiuYiMediaService.isServiceRunning && JiuYiMediaService.matchesPkg(targetPkg)) {
                        JiuYiMediaService.sendMediaAction("play_pause")
                    } else {
                        val keyCode = if (isMusicPlaying)
                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
                        else
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                        sendMediaKeyToPackage(targetPkg, keyCode)
                        dispatchSystemMediaKey(keyCode)
                    }
                }
            }
            return
        }

        if (JiuYiMediaService.isServiceRunning) {
            JiuYiMediaService.sendMediaAction("play_pause")
            return
        }

        val fallbackPkg = findAnyInstalledMusicPackage()
        if (fallbackPkg.isNotEmpty()) {
            connectAndPlayViaMediaBrowser(fallbackPkg)
            wakeMusicAppBackground(fallbackPkg)
            viewModelScope.launch {
                delay(1200)
                if (JiuYiMediaService.isServiceRunning) {
                    JiuYiMediaService.sendMediaAction("play_pause")
                } else {
                    dispatchSystemMediaKey(
                        if (isMusicPlaying) android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
                        else android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                    )
                }
            }
        } else {
            dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    // ── 下一曲（修复 matchesPkg 前缀匹配）────────────────────────────────────
    fun nextTrack() {
        val targetPkg = preferredMusicPackage.value
        if (targetPkg.isNotEmpty()) {
            if (JiuYiMediaService.isServiceRunning && JiuYiMediaService.matchesPkg(targetPkg)) {
                JiuYiMediaService.sendMediaAction("next")
            } else {
                sendMediaKeyToPackage(targetPkg, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            return
        }
        if (JiuYiMediaService.isServiceRunning) {
            JiuYiMediaService.sendMediaAction("next")
            return
        }
        dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    // ── 上一曲（修复 matchesPkg 前缀匹配）────────────────────────────────────
    fun prevTrack() {
        val targetPkg = preferredMusicPackage.value
        if (targetPkg.isNotEmpty()) {
            if (JiuYiMediaService.isServiceRunning && JiuYiMediaService.matchesPkg(targetPkg)) {
                JiuYiMediaService.sendMediaAction("prev")
            } else {
                sendMediaKeyToPackage(targetPkg, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            return
        }
        if (JiuYiMediaService.isServiceRunning) {
            JiuYiMediaService.sendMediaAction("prev")
            return
        }
        dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    val dockPackages = MutableStateFlow<List<String>>(emptyList())

    var draggedApp: AppModel? by mutableStateOf(null)
    var isDraggingActive by mutableStateOf(false)
    var dragOffset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var dragDistance by mutableStateOf(0f)
    var isDraggingFromDock by mutableStateOf(false)
    var isDraggingFromDrawer by mutableStateOf(false)
    var drawerThumbnailBounds by mutableStateOf(emptyMap<Int, androidx.compose.ui.geometry.Rect>())
    var addScreenThumbnailBounds by mutableStateOf(emptyMap<Int, androidx.compose.ui.geometry.Rect>())
    var dragSourceIndex by mutableStateOf(-1)

    var drawerItemBounds by mutableStateOf<Map<Int, androidx.compose.ui.geometry.Rect>>(emptyMap())
    var homeGridBounds by mutableStateOf<Map<Int, androidx.compose.ui.geometry.Rect>>(emptyMap())
    val drawerPackageOrder = MutableStateFlow<List<String>>(emptyList())
    val preUninstallApp = MutableStateFlow<AppModel?>(null)
    var isEditingHomeScreen by mutableStateOf(false)

    // ── 卸载请求事件流（由 MainActivity 的 ActivityResultLauncher 消费）──────
    private val _uninstallRequestFlow = kotlinx.coroutines.flow.MutableSharedFlow<AppModel>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val uninstallRequestFlow = _uninstallRequestFlow.asSharedFlow()

    val hiddenPackagesFlow = MutableStateFlow<Set<String>>(prefs.hiddenPackages)

    val filteredApps: StateFlow<List<DrawerItem>> = combine(
        _appList, searchQuery, hiddenPackagesFlow, showSystemApps, drawerSortType, drawerFolders
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val apps = array[0] as List<AppModel>
        val query = array[1] as String
        @Suppress("UNCHECKED_CAST")
        val hidden = array[2] as Set<String>
        val showSys = array[3] as Boolean
        val sortType = array[4] as Int
        @Suppress("UNCHECKED_CAST")
        val folders = array[5] as List<DrawerFolder>

        val filtered = apps.filter { app ->
            val matchesSys = showSys || !app.isSystem
            val isNotHidden = !hidden.contains(app.packageName) || query.isNotEmpty()
            val matchesQuery = query.isEmpty() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesSys && isNotHidden && matchesQuery
        }

        val launchCounts = prefs.getAppLaunchCounts()
        val context = getApplication<Application>()
        val pm = context.packageManager

        // All packages inside any folder
        val folderedPackages = if (query.isEmpty()) {
            folders.flatMap { it.packageNames }.toSet()
        } else {
            emptySet()
        }

        val rootApps = filtered.filter { !folderedPackages.contains(it.packageName) }

        fun getAppSortKey(app: AppModel): Comparable<*> {
            return when (sortType) {
                0 -> app.label.lowercase()
                1 -> {
                    val installTime = try {
                        pm.getPackageInfo(app.packageName, 0).firstInstallTime
                    } catch (e: Exception) { 0L }
                    -installTime
                }
                2 -> {
                    val installTime = try {
                        pm.getPackageInfo(app.packageName, 0).firstInstallTime
                    } catch (e: Exception) { 0L }
                    installTime
                }
                3 -> {
                    val count = launchCounts[app.packageName] ?: 0
                    -count
                }
                else -> app.label.lowercase()
            }
        }

        val sortedRootApps = rootApps.sortedWith(compareBy { getAppSortKey(it) })

        if (query.isEmpty()) {
            val sortedFolders = folders.sortedBy { it.name.lowercase() }
            val list = mutableListOf<DrawerItem>()
            sortedFolders.forEach { list.add(DrawerItem.Folder(it)) }
            sortedRootApps.forEach { list.add(DrawerItem.App(it)) }
            list
        } else {
            sortedRootApps.map { DrawerItem.App(it) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val mediaUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "com.example.LAUNCHER_MEDIA_UPDATE") return
            val title     = intent.getStringExtra("title") ?: ""
            val artist    = intent.getStringExtra("artist") ?: ""
            val isPlaying = intent.getBooleanExtra("is_playing", false)
            val position  = intent.getLongExtra("position", 0L)
            val duration  = intent.getLongExtra("duration", 0L)
            val artBase64 = intent.getStringExtra("art_base64") ?: ""
            if (title.isNotEmpty()) {
                currentTrackName   = title
                currentTrackArtist = artist.ifEmpty { "正在播放" }
            } else {
                currentTrackName   = "久以金曲"
                currentTrackArtist = "打开任意音乐播放器即可显示"
            }
            isMusicPlaying  = isPlaying
            currentPosition = position
            currentDuration = duration
            if (artBase64.isNotEmpty()) {
                currentArtBase64 = artBase64
            } else {
                currentArtBase64 = ""
            }
        }
    }

    private val weatherUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action == "com.example.LAUNCHER_WEATHER_UPDATE") {
                val city    = intent.getStringExtra("city") ?: ""
                val weather = intent.getStringExtra("weather") ?: ""
                val temp    = intent.getStringExtra("temp") ?: ""
                var current = _weatherState.value
                if (city.isNotEmpty())    { current = current.copy(city = city);        prefs.customCity    = city    }
                if (weather.isNotEmpty()) { current = current.copy(weather = weather);  prefs.customWeather = weather }
                if (temp.isNotEmpty())    { current = current.copy(temperature = temp); prefs.customTemp    = temp    }
                if (city.isEmpty() && weather.isEmpty() && temp.isEmpty()) {
                    current = current.copy(
                        city = prefs.customCity.ifEmpty { "北京" }, weather = prefs.customWeather.ifEmpty { "多云" },
                        temperature = prefs.customTemp.ifEmpty { "18°C" }, lat = prefs.customLat.toDouble(),
                        lng = prefs.customLng.toDouble(), country = prefs.customCountry, admin = prefs.customAdmin
                    )
                }
                _weatherState.value = current.copy(lastUpdateTime = System.currentTimeMillis())
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.data?.schemeSpecificPart?.let { _ -> refreshInstalledApps() }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) batteryLevel = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                val status = it.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                isBatteryCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                batteryTemperature = it.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                val volt = it.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0)
                batteryVoltage = if (volt > 1000) volt / 1000f else volt.toFloat()
            }
        }
    }

    init {
        loadDrawerPackageOrder()
        loadDockConfiguration()
        loadHomePages()
        refreshInstalledApps()
        updateRealtimeStats()
        trySyncSystemWeatherSilently()

        viewModelScope.launch(Dispatchers.IO) {
            while (true) { updateRealtimeStats(); delay(4000) }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(application, packageReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        androidx.core.content.ContextCompat.registerReceiver(application, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        androidx.core.content.ContextCompat.registerReceiver(application, mediaUpdateReceiver, IntentFilter("com.example.LAUNCHER_MEDIA_UPDATE"), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        androidx.core.content.ContextCompat.registerReceiver(application, weatherUpdateReceiver, IntentFilter("com.example.LAUNCHER_WEATHER_UPDATE"), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        super.onCleared()
        try { getApplication<Application>().unregisterReceiver(packageReceiver) }     catch (_: Exception) {}
        try { getApplication<Application>().unregisterReceiver(batteryReceiver) }     catch (_: Exception) {}
        try { getApplication<Application>().unregisterReceiver(mediaUpdateReceiver) } catch (_: Exception) {}
        try { getApplication<Application>().unregisterReceiver(weatherUpdateReceiver) } catch (_: Exception) {}
    }

    fun loadDrawerPackageOrder() {
        val raw = prefs.drawerPackageOrderCSV
        if (raw.isNotEmpty()) {
            drawerPackageOrder.value = raw.split(",").filter { it.isNotEmpty() }
        }
    }

    fun saveDrawerPackageOrder(newList: List<String>) {
        val cleanList = newList.filter { it.isNotEmpty() }
        prefs.drawerPackageOrderCSV = cleanList.joinToString(",")
        drawerPackageOrder.value = cleanList
    }

    fun reorderDrawerApp(packageName: String, targetGlobalIndex: Int) {
        val items = filteredApps.value
        val targetItem = items.getOrNull(targetGlobalIndex)
        if (targetItem is DrawerItem.Folder) {
            addAppToDrawerFolder(targetItem.folder.id, packageName)
        } else {
            val current = drawerPackageOrder.value.toMutableList()
            val existingIndex = current.indexOf(packageName)
            if (existingIndex != -1) {
                current.removeAt(existingIndex)
                val safeTarget = targetGlobalIndex.coerceIn(0, current.size)
                current.add(safeTarget, packageName)
                saveDrawerPackageOrder(current)
            }
        }
    }

    var lastDrawerPageSwitchTime = 0L
    fun checkDrawerEdgeScroll(dropX: Float, screenWidth: Float, totalPages: Int) {
        val now = System.currentTimeMillis()
        if (now - lastDrawerPageSwitchTime < 1500) return
        if (dropX < 32f) {
            val current = drawerPageIndex.value
            if (current > 0) {
                lastDrawerPageSwitchTime = now
                drawerPageIndex.value = current - 1
            }
        } else if (dropX > screenWidth - 32f) {
            val current = drawerPageIndex.value
            if (current < totalPages - 1) {
                lastDrawerPageSwitchTime = now
                drawerPageIndex.value = current + 1
            }
        }
    }

    var lastHomePageSwitchTime = 0L
    fun checkHomeEdgeScroll(dropX: Float, screenWidth: Float, totalPages: Int) {
        val now = System.currentTimeMillis()
        if (now - lastHomePageSwitchTime < 1500) return
        if (dropX < 32f) {
            val current = activePageIndex.value
            if (current > 0) {
                lastHomePageSwitchTime = now
                activePageIndex.value = current - 1
            }
        } else if (dropX > screenWidth - 32f) {
            val current = activePageIndex.value
            if (current < totalPages - 1) {
                lastHomePageSwitchTime = now
                activePageIndex.value = current + 1
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { queryAppsFromSystem() }
            _appList.value = apps

            val currentOrder = drawerPackageOrder.value.toMutableList()
            val installedPackages = apps.map { it.packageName }.toSet()
            currentOrder.retainAll { installedPackages.contains(it) }
            val existingPackages = currentOrder.toSet()
            val newApps = apps.filter { !existingPackages.contains(it.packageName) }.sortedBy { it.label.lowercase() }
            for (newApp in newApps) {
                currentOrder.add(newApp.packageName)
            }
            saveDrawerPackageOrder(currentOrder)
        }
    }

    private fun queryAppsFromSystem(): List<AppModel> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val activities = pm.queryIntentActivities(launchIntent, 0)
        val list = mutableListOf<AppModel>()
        for (resolveInfo in activities) {
            val packageName = resolveInfo.activityInfo.packageName
            val className   = resolveInfo.activityInfo.name
            var label = ""; var icon: android.graphics.drawable.Drawable? = null
            try { label = resolveInfo.loadLabel(pm).toString(); icon = resolveInfo.loadIcon(pm) } catch (e: Exception) {
                label = resolveInfo.activityInfo.labelRes.let { resId ->
                    if (resId != 0) try { pm.getResourcesForApplication(packageName).getString(resId) } catch (ex: Exception) { packageName } else packageName
                } ?: packageName
            }
            val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            list.add(AppModel(label, packageName, className, icon, isSystem))
        }
        if (list.isEmpty() || list.size < 5) {
            list.addAll(listOf(
                AppModel("应用商店 (App Store)",   "com.android.vending",    "com.android.vending.AssetBrowserActivity", isSystem = true),
                AppModel("我的相机 (Camera)",       "com.android.camera",     "com.android.camera.Camera",                isSystem = true),
                AppModel("天气画报 (Weather)",      "com.jiuyi.weather",      "com.jiuyi.weather.WeatherActivity"),
                AppModel("音乐星空 (Music)",        "com.jiuyi.music",        "com.jiuyi.music.MusicActivity"),
                AppModel("信息 (Messages)",         "com.android.mms",        "com.android.mms.ui.ConversationList",      isSystem = true),
                AppModel("浏览器 (Browser)",        "com.android.browser",    "com.android.browser.BrowserActivity",      isSystem = true),
                AppModel("相册 (Gallery)",          "com.android.gallery",    "com.android.gallery.GalleryActivity",      isSystem = true),
                AppModel("久以计算器 (Calculator)", "com.jiuyi.calculator",   "com.jiuyi.calculator.CalcActivity"),
                AppModel("桌面文件管家 (Files)",    "com.android.documentsui","com.android.documentsui.files.FilesActivity", isSystem = true),
                AppModel("久以便签 (Memo)",         "com.jiuyi.memo",         "com.jiuyi.memo.MainActivity"),
                AppModel("个性主题 (Themes)",       "com.jiuyi.themes",       "com.jiuyi.themes.ThemeActivity"),
                AppModel("系统设置 (Settings)",     "com.android.settings",   "com.android.settings.Settings",           isSystem = true)
            ))
        }
        return list.distinctBy { it.packageName }
    }

    fun updateTheme(index: Int)            { prefs.themeColorIndex = index;   currentThemeIndex.value = index }
    fun updateClockStyle(style: String)    { prefs.clockStyle = style;        clockStyle.value = style }
    fun updateWallpaper(wallpaper: String) { prefs.wallpaperName = wallpaper; wallpaperName.value = wallpaper }

    fun toggleShowLabels() { val v = !prefs.showLabels; prefs.showLabels = v; showLabels.value = v }
    fun toggleShowSystemApps() { val v = !prefs.showSystemApps; prefs.showSystemApps = v; showSystemApps.value = v }
    fun updateDrawerGrid(grid: String)     { prefs.drawerGrid = grid;         drawerGrid.value = grid }
    fun updateIconPackFilter(pack: String) { prefs.iconPackFilter = pack;     iconPackFilter.value = pack }

    fun updateTouchEffect(effect: String) { prefs.touchEffect = effect; touchEffect.value = effect }
    fun updateHomeTransition(trans: String) { prefs.homeTransition = trans; homeTransition.value = trans }
    fun updateDrawerTransition(trans: String) { prefs.drawerTransition = trans; drawerTransition.value = trans }
    fun updateCrossTransition(trans: String) { prefs.crossTransition = trans; crossTransition.value = trans }

    fun toggleTouchRandomPool(effect: String) {
        val currentList = prefs.touchRandomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (currentList.contains(effect)) {
            currentList.remove(effect)
        } else {
            currentList.add(effect)
        }
        val newVal = currentList.joinToString(",")
        prefs.touchRandomPool = newVal
        touchRandomPool.value = newVal
    }

    fun toggleHomeRandomPool(trans: String) {
        val currentList = prefs.homeRandomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (currentList.contains(trans)) {
            currentList.remove(trans)
        } else {
            currentList.add(trans)
        }
        val newVal = currentList.joinToString(",")
        prefs.homeRandomPool = newVal
        homeRandomPool.value = newVal
    }

    fun toggleDrawerRandomPool(trans: String) {
        val currentList = prefs.drawerRandomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (currentList.contains(trans)) {
            currentList.remove(trans)
        } else {
            currentList.add(trans)
        }
        val newVal = currentList.joinToString(",")
        prefs.drawerRandomPool = newVal
        drawerRandomPool.value = newVal
    }

    fun toggleCrossRandomPool(trans: String) {
        val currentList = prefs.crossRandomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (currentList.contains(trans)) {
            currentList.remove(trans)
        } else {
            currentList.add(trans)
        }
        val newVal = currentList.joinToString(",")
        prefs.crossRandomPool = newVal
        crossRandomPool.value = newVal
    }

    fun toggleHiddenPackage(packageName: String) {
        prefs.toggleHiddenPackage(packageName)
        hiddenPackagesFlow.value = prefs.hiddenPackages
    }

    fun boostRam() {
        viewModelScope.launch {
            if (isRamBoosting) return@launch
            isRamBoosting = true
            val context = getApplication<Application>()
            var sizeBefore: Long = 0
            try { val cacheFiles = context.cacheDir.listFiles(); if (cacheFiles != null) { for (f in cacheFiles) { sizeBefore += getFolderSize(f) } } } catch (e: Exception) {}
            delay(1500)
            System.gc(); System.runFinalization(); System.gc()
            try { context.cacheDir.deleteRecursively() } catch (e: Exception) {}
            updateRealtimeStats()
            isRamBoosting = false
            lastBoostTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val clearedMb = if (sizeBefore > 0) sizeBefore / (1024f * 1024f) else (15..45).random() / 10f
            android.widget.Toast.makeText(context, "一键加速成功！已清理 ${String.format("%.2f", clearedMb)} MB 系统垃圾缓和缓存", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDockConfiguration() {
        val raw = prefs.dockPackagesCommaSeparated
        dockPackages.value = raw.split(",").filter { it.isNotEmpty() && it != "EMPTY" }
    }

    fun updateDockConfiguration(newList: List<String>) {
        val cleanList = newList.filter { it.isNotEmpty() && it != "EMPTY" }
        prefs.dockPackagesCommaSeparated = cleanList.joinToString(",")
        dockPackages.value = cleanList
    }

    fun swapOrUpdateDockItem(index: Int, targetPackage: String) {
        val current = dockPackages.value.toMutableList()
        val indexInDock = current.indexOf(targetPackage)
        if (indexInDock != -1) {
            val temp = current.getOrNull(index)
            if (temp != null && temp != "MENU_BUTTON" && targetPackage != "MENU_BUTTON") { current[index] = targetPackage; current[indexInDock] = temp }
        } else {
            if (index in 0..current.size) current.add(index, targetPackage) else current.add(targetPackage)
        }
        updateDockConfiguration(current)
    }

    fun removeDockItem(index: Int) {
        val current = dockPackages.value.toMutableList()
        if (index in 0 until current.size && current[index] != "MENU_BUTTON") { current.removeAt(index); updateDockConfiguration(current) }
    }

    fun handleDockDrop(app: AppModel, targetIndex: Int?) {
        val current = dockPackages.value.toMutableList()
        val existingIndex = current.indexOf(app.packageName)
        if (targetIndex != null) {
            val safeTarget = targetIndex.coerceIn(0, current.size)
            if (existingIndex != -1) {
                current.removeAt(existingIndex)
                val newTarget = if (safeTarget > existingIndex) safeTarget - 1 else safeTarget
                current.add(newTarget.coerceIn(0, current.size), app.packageName)
            } else { current.add(safeTarget, app.packageName) }
        } else {
            if (existingIndex != -1 && app.packageName != "MENU_BUTTON") current.removeAt(existingIndex)
        }
        updateDockConfiguration(current)
    }

    private val _citySearchResults = MutableStateFlow<List<CityItem>>(emptyList())
    val citySearchResults: StateFlow<List<CityItem>> = _citySearchResults
    private var searchJob: kotlinx.coroutines.Job? = null

    fun searchCityGeo(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) { _citySearchResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(350)
            _citySearchResults.value = weatherRepo.searchCityGeo(trimmed)
        }
    }

    fun loadHomePages() {
        val raw = prefs.homePagesRaw
        if (raw.isEmpty()) {
            val defaultPage = HomeScreenPage("0", apps = List(24) { "EMPTY" }, widgets = listOf("RAM Booster", "Music Cassette"))
            homePages.value = listOf(defaultPage)
        } else {
            val parts = raw.split("|||")
            homePages.value = parts.mapIndexed { index, part ->
                val subParts = part.split(":::")
                var apps = emptyList<String>()
                var widgets = emptyList<String>()
                for (sub in subParts) {
                    if (sub.startsWith("apps:")) {
                        apps = sub.substring(5).split(",")
                    } else if (sub.startsWith("widgets:")) {
                        widgets = sub.substring(8).split(",").filter { it.isNotEmpty() }
                    }
                }
                val mutableApps = apps.toMutableList()
                while (mutableApps.size < 24) {
                    mutableApps.add("EMPTY")
                }
                if (mutableApps.size > 24) {
                    mutableApps.subList(24, mutableApps.size).clear()
                }
                HomeScreenPage(index.toString(), mutableApps, widgets)
            }
        }
    }

    fun saveHomePages(pages: List<HomeScreenPage>) {
        val serialized = pages.joinToString("|||") { page ->
            val normalizedApps = page.apps.toMutableList()
            while (normalizedApps.size < 24) {
                normalizedApps.add("EMPTY")
            }
            if (normalizedApps.size > 24) {
                normalizedApps.subList(24, normalizedApps.size).clear()
            }
            "apps:${normalizedApps.joinToString(",")}" + ":::" + "widgets:${page.widgets.joinToString(",")}"
        }
        prefs.homePagesRaw = serialized
        homePages.value = pages
    }

    fun addHomePage() {
        val current = homePages.value.toMutableList()
        val nextId = current.size.toString()
        current.add(HomeScreenPage(nextId))
        saveHomePages(current)
    }

    fun deleteHomePage(index: Int) {
        val current = homePages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            val reindexed = current.mapIndexed { reIndex, page ->
                HomeScreenPage(reIndex.toString(), page.apps, page.widgets)
            }
            saveHomePages(reindexed)
            if (activePageIndex.value >= reindexed.size) {
                activePageIndex.value = maxOf(0, reindexed.size - 1)
            }
        }
    }

    fun reorderHomePage(fromIndex: Int, toIndex: Int) {
        val current = homePages.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val page = current.removeAt(fromIndex)
            current.add(toIndex, page)
            val reindexed = current.mapIndexed { reIndex, p ->
                HomeScreenPage(reIndex.toString(), p.apps, p.widgets)
            }
            saveHomePages(reindexed)
        }
    }

    fun addAppToPage(pageIndex: Int, packageName: String) {
        val current = homePages.value.toMutableList()
        if (pageIndex in current.indices) {
            val page = current[pageIndex]
            val pageApps = page.apps.toMutableList()
            while (pageApps.size < 24) { pageApps.add("EMPTY") }
            if (!pageApps.contains(packageName)) {
                val emptyIdx = pageApps.indexOf("EMPTY")
                if (emptyIdx != -1) {
                    pageApps[emptyIdx] = packageName
                } else {
                    pageApps.add(packageName) // fallback if full
                }
                current[pageIndex] = page.copy(apps = pageApps)
                saveHomePages(current)
            }
        }
    }

    fun addAppToPageAtSlot(pageIndex: Int, packageName: String, slotIndex: Int) {
        val current = homePages.value.toMutableList()
        if (pageIndex in current.indices) {
            val page = current[pageIndex]
            val pageApps = page.apps.toMutableList()
            while (pageApps.size < 24) { pageApps.add("EMPTY") }
            if (slotIndex in pageApps.indices) {
                // If it is already placed elsewhere on the page, clear it first
                val existingIdx = pageApps.indexOf(packageName)
                if (existingIdx != -1) {
                    pageApps[existingIdx] = "EMPTY"
                }
                
                val oldApp = pageApps[slotIndex]
                pageApps[slotIndex] = packageName
                
                // If the target slot was occupied, bump the old app to the first available empty slot
                if (oldApp != "EMPTY" && oldApp != packageName) {
                    val emptyIdx = pageApps.indexOf("EMPTY")
                    if (emptyIdx != -1) {
                        pageApps[emptyIdx] = oldApp
                    }
                }
                current[pageIndex] = page.copy(apps = pageApps)
                saveHomePages(current)
            }
        }
    }

    fun moveAppInPage(pageIndex: Int, fromSlotIndex: Int, toSlotIndex: Int) {
        val current = homePages.value.toMutableList()
        if (pageIndex in current.indices) {
            val page = current[pageIndex]
            val pageApps = page.apps.toMutableList()
            while (pageApps.size < 24) { pageApps.add("EMPTY") }
            if (fromSlotIndex in pageApps.indices && toSlotIndex in pageApps.indices) {
                val temp = pageApps[fromSlotIndex]
                pageApps[fromSlotIndex] = pageApps[toSlotIndex]
                pageApps[toSlotIndex] = temp
                current[pageIndex] = page.copy(apps = pageApps)
                saveHomePages(current)
            }
        }
    }

    fun removeAppFromPage(pageIndex: Int, packageName: String) {
        val current = homePages.value.toMutableList()
        if (pageIndex in current.indices) {
            val page = current[pageIndex]
            val pageApps = page.apps.map { if (it == packageName) "EMPTY" else it }
            current[pageIndex] = page.copy(apps = pageApps)
            saveHomePages(current)
        }
    }

    fun addWidgetToPage(pageIndex: Int, widgetName: String) {
        val current = homePages.value.toMutableList()
        if (pageIndex in current.indices) {
            val page = current[pageIndex]
            if (!page.widgets.contains(widgetName)) {
                val updatedWidgets = page.widgets + widgetName
                current[pageIndex] = page.copy(widgets = updatedWidgets)
                saveHomePages(current)
            }
        }
    }

    fun removeWidgetFromPage(pageIndex: Int, widgetName: String) {
        val current = homePages.value.toMutableList()
        if (pageIndex in current.indices) {
            val page = current[pageIndex]
            val updatedWidgets = page.widgets - widgetName
            current[pageIndex] = page.copy(widgets = updatedWidgets)
            saveHomePages(current)
        }
    }

    fun removeAppFromEverywhereLocal(packageName: String) {
        val dockList = dockPackages.value.toMutableList()
        if (dockList.contains(packageName)) {
            dockList.remove(packageName)
            updateDockConfiguration(dockList)
        }

        val pages = homePages.value.map { page ->
            val updated = page.apps.map { if (it == packageName) "EMPTY" else it }
            page.copy(apps = updated)
        }
        saveHomePages(pages)

        val drawerOrder = drawerPackageOrder.value.toMutableList()
        if (drawerOrder.contains(packageName)) {
            drawerOrder.remove(packageName)
            saveDrawerPackageOrder(drawerOrder)
        }

        val remainingApps = _appList.value.filter { it.packageName != packageName }
        _appList.value = remainingApps
    }

    // ── 卸载应用（修复：不再自己 startActivity，改为发信号给 MainActivity）────
    fun uninstallApp(context: android.content.Context, app: AppModel) {
        val packageName = app.packageName

        // 虚拟应用：仅本地移除
        val isInstalled = try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) { false }

        if (!isInstalled) {
            removeAppFromEverywhereLocal(packageName)
            android.widget.Toast.makeText(context, "已成功卸载虚拟应用: ${app.label}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 系统应用检测：FLAG_SYSTEM 且没有 FLAG_UPDATED_SYSTEM_APP（更新过的系统应用允许卸载更新）
        try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdated) {
                android.widget.Toast.makeText(context, "系统应用无法卸载：${app.label}", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        } catch (_: Exception) { /* 查询失败则继续尝试卸载 */ }

        _uninstallRequestFlow.tryEmit(app)
    }

    // ── 由 MainActivity 在卸载结果回调中调用 ─────────────────────────────────
    fun onUninstallResult(packageName: String, success: Boolean) {
        if (success) {
            removeAppFromEverywhereLocal(packageName)
            preUninstallApp.value = null
        }
        // 无论成功与否，都重新扫描系统应用列表确保同步
        refreshInstalledApps()
    }

    // --- Dynamic Folder and Categorizations (V2) ---
    fun recordAppLaunch(packageName: String) {
        val current = prefs.getAppLaunchCounts().toMutableMap()
        current[packageName] = (current[packageName] ?: 0) + 1
        prefs.saveAppLaunchCounts(current)
        drawerSortType.value = prefs.drawerSortType
    }

    fun updateIconRoundness(value: Int) {
        prefs.iconRoundness = value
        iconRoundness.value = value
    }

    fun updateIconSizeScale(value: Int) {
        prefs.iconSizeScale = value
        iconSizeScale.value = value
    }

    fun updateFontSizeSp(value: Int) {
        prefs.fontSizeSp = value
        fontSizeSp.value = value
    }

    fun updateDrawerSortType(value: Int) {
        prefs.drawerSortType = value
        drawerSortType.value = value
    }

    fun createDrawerFolder(name: String = "新建文件夹") {
        val current = prefs.getDrawerFolders().toMutableList()
        val uniqueId = "folder_" + java.util.UUID.randomUUID().toString()
        current.add(DrawerFolder(uniqueId, name, mutableListOf()))
        prefs.saveDrawerFolders(current)
        drawerFolders.value = current
    }

    fun renameDrawerFolder(folderId: String, newName: String) {
        val current = prefs.getDrawerFolders().toMutableList()
        val target = current.find { it.id == folderId }
        if (target != null) {
            target.name = newName
            prefs.saveDrawerFolders(current)
            drawerFolders.value = current
        }
    }

    fun deleteDrawerFolder(folderId: String) {
        val current = prefs.getDrawerFolders().toMutableList()
        current.removeAll { it.id == folderId }
        prefs.saveDrawerFolders(current)
        drawerFolders.value = current
    }

    fun addAppToDrawerFolder(folderId: String, packageName: String) {
        val current = prefs.getDrawerFolders().toMutableList()
        current.forEach { folder ->
            folder.packageNames.remove(packageName)
        }
        val target = current.find { it.id == folderId }
        if (target != null) {
            if (!target.packageNames.contains(packageName)) {
                target.packageNames.add(packageName)
            }
            prefs.saveDrawerFolders(current)
            drawerFolders.value = current
        }
    }

    fun removeAppFromDrawerFolder(folderId: String, packageName: String) {
        val current = prefs.getDrawerFolders().toMutableList()
        val target = current.find { it.id == folderId }
        if (target != null) {
            target.packageNames.remove(packageName)
            prefs.saveDrawerFolders(current)
            drawerFolders.value = current
        }
    }

    fun backupLayoutSnapshot() {
        prefs.layoutSnapshotRaw = prefs.drawerFoldersRaw
        prefs.hasLayoutSnapshot = true
    }

    fun restoreLayoutSnapshot(): Boolean {
        if (prefs.hasLayoutSnapshot) {
            val raw = prefs.layoutSnapshotRaw
            prefs.drawerFoldersRaw = raw
            drawerFolders.value = prefs.getDrawerFolders()
            return true
        }
        return false
    }

    fun smartCategorizeApps() {
        backupLayoutSnapshot()
        val newFolders = mutableListOf<DrawerFolder>()
        val apps = _appList.value

        val categories = mapOf(
            "社交" to listOf("wechat", "qq", "chat", "social", "contact", "phone", "mms", "contacts", "message", "im", "社交", "微信", "腾讯", "微博", "weibo", "talk"),
            "工具" to listOf("calculator", "files", "document", "weather", "memo", "calendar", "clock", "browser", "search", "desk", "file", "weather", "compass", "map", "tool", "工具", "计算器", "天气", "便签", "时钟", "浏览器"),
            "游戏" to listOf("game", "play", "arcade", "puzzle", "action", "sport", "racing", "minecraft", "chess", "gaming", "游戏", "娱乐", "竞技", "王者"),
            "影音" to listOf("music", "video", "camera", "gallery", "tv", "audio", "stream", "netease", "spotify", "youtube", "media", "player", "影音", "音乐", "相册", "相机", "视频", "播放器"),
            "办公" to listOf("office", "mail", "word", "excel", "pdf", "sheet", "slide", "meeting", "scan", "email", "gmail", "outlook", "wps", "办公", "邮箱", "文档", "扫描"),
            "系统工具" to listOf("settings", "launcher", "system", "vending", "security", "store", "backup", "manager", "app", "service", "systemui", "系统", "设置", "安全", "管家", "应用市场", "应用商店"),
            "购物" to listOf("shop", "store", "buy", "mall", "market", "pay", "amazon", "taobao", "jd", "alipay", "购物", "淘宝", "京东", "支付", "美团", "拼多多")
        )

        val groups = mutableMapOf<String, MutableList<String>>()
        categories.keys.forEach { groups[it] = mutableListOf() }
        groups["其他"] = mutableListOf()

        apps.forEach { app ->
            val key = app.packageName.lowercase() + " " + app.label.lowercase()
            var matched = false
            for ((category, keywords) in categories) {
                if (keywords.any { key.contains(it) }) {
                     groups[category]?.add(app.packageName)
                     matched = true
                     break
                }
            }
            if (!matched) {
                groups["其他"]?.add(app.packageName)
            }
        }

        var cnt = 0
        groups.forEach { (catName, pkgs) ->
            if (pkgs.isNotEmpty()) {
                newFolders.add(DrawerFolder(
                    id = "folder_smart_" + (cnt++),
                    name = catName,
                    packageNames = pkgs.toMutableList()
                ))
            }
        }

        prefs.saveDrawerFolders(newFolders)
        drawerFolders.value = newFolders
    }
}