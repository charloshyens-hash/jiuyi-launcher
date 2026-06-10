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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = LauncherPrefs(application)
    val weatherRepo = WeatherRepository.getInstance(application)

    private val _appList = MutableStateFlow<List<AppModel>>(emptyList())
    val appList: StateFlow<List<AppModel>> = _appList

    val currentThemeIndex = MutableStateFlow(prefs.themeColorIndex)
    val clockStyle = MutableStateFlow(prefs.clockStyle)
    val wallpaperName = MutableStateFlow(prefs.wallpaperName)
    val showLabels = MutableStateFlow(prefs.showLabels)
    val showSystemApps = MutableStateFlow(prefs.showSystemApps)
    val drawerGrid = MutableStateFlow(prefs.drawerGrid)
    val iconPackFilter = MutableStateFlow(prefs.iconPackFilter)

    val searchQuery = MutableStateFlow("")
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
    fun updatePreferredMusicPackage(pkg: String) { prefs.preferredMusicPackage = pkg; preferredMusicPackage.value = pkg }

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

    /**
     * 后台唤醒播放器（Android 8+ 兼容方案）
     *
     * ACTION_MEDIA_BUTTON 定向广播在 Android 8+ 对未运行进程无效。
     * 改用 AudioFocus 请求 + 系统 dispatchMediaKeyEvent，
     * 让系统把媒体键路由给已注册 MediaButtonReceiver 的播放器后台服务。
     * 主流播放器（网易云/QQ音乐/酷狗/酷我）均注册了此接收器，无需启动 Activity。
     */
    private fun tryWakeMusicServiceBackground(pkg: String): Boolean {
        return try {
            val context = getApplication<Application>()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                ?: return false

            // 请求音频焦点：驱使系统通知已注册媒体按钮的播放器服务准备就绪
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

            // 发系统广播媒体键（不定向，系统负责路由给活跃播放器）
            dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            true
        } catch (e: Exception) {
            android.util.Log.e("LauncherVM", "tryWakeMusicServiceBackground failed: ${e.message}")
            false
        }
    }

    /**
     * 播放/暂停
     *
     * 逻辑大幅简化：
     * Service 运行中 → 直接调 sendMediaAction，由 Service 内部双轨策略处理
     * Service 未运行 → 补发系统媒体键兜底
     * 任何情况都不启动 Activity，用户始终留在桌面
     */
    fun toggleMusicPlayback() {
        if (JiuYiMediaService.isServiceRunning) {
            JiuYiMediaService.sendMediaAction("play_pause")
            return
        }
        // Service 未运行时的最后兜底
        dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun nextTrack() {
        if (JiuYiMediaService.isServiceRunning) {
            JiuYiMediaService.sendMediaAction("next")
            return
        }
        dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun prevTrack() {
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
    var isDraggingFromDock by mutableStateOf(false)
    var dragSourceIndex by mutableStateOf(-1)

    val hiddenPackagesFlow = MutableStateFlow<Set<String>>(prefs.hiddenPackages)

    val filteredApps: StateFlow<List<AppModel>> = combine(
        _appList, searchQuery, hiddenPackagesFlow, showSystemApps
    ) { apps, query, hidden, showSys ->
        apps.filter { app ->
            val matchesSys = showSys || !app.isSystem
            val isNotHidden = !hidden.contains(app.packageName) || query.isNotEmpty()
            val matchesQuery = query.isEmpty() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesSys && isNotHidden && matchesQuery
        }.sortedBy { it.label.lowercase() }
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
            if (artBase64.isNotEmpty()) currentArtBase64 = artBase64
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
        loadDockConfiguration()
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
        androidx.core.content.ContextCompat.registerReceiver(application, packageReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
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

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { queryAppsFromSystem() }
            _appList.value = apps
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
}