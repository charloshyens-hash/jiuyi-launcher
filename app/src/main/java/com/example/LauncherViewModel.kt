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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════════════════════════
//  LauncherViewModel — 仅保留：状态字段声明、init、receivers、未拆出的业务逻辑
//  已拆出方法见：
//    ViewModelWeatherHelper.kt  — 天气
//    ViewModelMusicHelper.kt    — 音乐播控
//    ViewModelHomePageHelper.kt — 桌面页 / Dock
//    ViewModelSystemHelper.kt   — 系统状态 / 应用列表
// ══════════════════════════════════════════════════════════════════════════════

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

    // ── 桌面页 ───────────────────────────────────────────────────────────────
    val homePages = MutableStateFlow<List<HomeScreenPage>>(emptyList())
    val activePageIndex = MutableStateFlow(0)

    // ── 应用列表 ─────────────────────────────────────────────────────────────
    internal val _appList = MutableStateFlow<List<AppModel>>(emptyList())
    val appList: StateFlow<List<AppModel>> = _appList

    // ── 主题 / 外观 ──────────────────────────────────────────────────────────
    val currentThemeIndex = MutableStateFlow(prefs.themeColorIndex)
    val clockStyle = MutableStateFlow(prefs.clockStyle)
    val wallpaperName = MutableStateFlow(prefs.wallpaperName)
    val showLabels = MutableStateFlow(prefs.showLabels)
    val showSystemApps = MutableStateFlow(prefs.showSystemApps)
    val drawerGrid = MutableStateFlow(prefs.drawerGrid)
    val iconPackFilter = MutableStateFlow(prefs.iconPackFilter)

    // ── 动画 ─────────────────────────────────────────────────────────────────
    val touchEffect = MutableStateFlow(prefs.touchEffect)
    val homeTransition = MutableStateFlow(prefs.homeTransition)
    val drawerTransition = MutableStateFlow(prefs.drawerTransition)
    val crossTransition = MutableStateFlow(prefs.crossTransition)
    val touchRandomPool = MutableStateFlow(prefs.touchRandomPool)
    val homeRandomPool = MutableStateFlow(prefs.homeRandomPool)
    val drawerRandomPool = MutableStateFlow(prefs.drawerRandomPool)
    val crossRandomPool = MutableStateFlow(prefs.crossRandomPool)

    // ── 抽屉 ─────────────────────────────────────────────────────────────────
    val searchQuery = MutableStateFlow("")
    val iconRoundness = MutableStateFlow(prefs.iconRoundness)
    val iconSizeScale = MutableStateFlow(prefs.iconSizeScale)
    val fontSizeSp = MutableStateFlow(prefs.fontSizeSp)
    val drawerSortType = MutableStateFlow(prefs.drawerSortType)
    val drawerFolders = MutableStateFlow<List<DrawerFolder>>(prefs.getDrawerFolders())
    val drawerPageIndex = MutableStateFlow(0)
    val appsGridPageIndex = MutableStateFlow(0)

    val backToFirstScreenEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // ── 电池 ─────────────────────────────────────────────────────────────────
    var batteryLevel by mutableStateOf(85)
    var isBatteryCharging by mutableStateOf(false)
    var batteryTemperature by mutableStateOf(31.4f)
    var batteryVoltage by mutableStateOf(3.2f)

    // ── 系统状态（字段）───────────────────────────────────────────────────────
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

    // ── 天气（字段）──────────────────────────────────────────────────────────
    val isWeatherOnlineAllowed = MutableStateFlow(prefs.isWeatherOnlineAllowed)

    internal val _weatherState = MutableStateFlow(
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

    // ── 城市搜索（字段）──────────────────────────────────────────────────────
    internal val _citySearchResults = MutableStateFlow<List<CityItem>>(emptyList())
    val citySearchResults: StateFlow<List<CityItem>> = _citySearchResults
    internal var _searchJob: Job? = null

    // ── 音乐（字段）──────────────────────────────────────────────────────────
    val musicWidgetMode = MutableStateFlow(prefs.musicWidgetMode)
    val preferredMusicPackage = MutableStateFlow(prefs.preferredMusicPackage)

    var currentTrackName by mutableStateOf("久以金曲")
    var currentTrackArtist by mutableStateOf("打开任意音乐播放器即可显示")
    var isMusicPlaying by mutableStateOf(false)
    var currentArtBase64 by mutableStateOf("")
    var currentPosition by mutableStateOf(0L)
    var currentDuration by mutableStateOf(0L)

    // ── Dock ─────────────────────────────────────────────────────────────────
    val dockPackages = MutableStateFlow<List<String>>(emptyList())

    // ── 拖拽状态 ─────────────────────────────────────────────────────────────
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

    // ── 卸载事件流 ───────────────────────────────────────────────────────────
    private val _uninstallRequestFlow = kotlinx.coroutines.flow.MutableSharedFlow<AppModel>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val uninstallRequestFlow = _uninstallRequestFlow.asSharedFlow()

    val hiddenPackagesFlow = MutableStateFlow<Set<String>>(prefs.hiddenPackages)

    // ── 抽屉过滤流（combine 逻辑保持不变）────────────────────────────────────
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
                    val installTime = try { pm.getPackageInfo(app.packageName, 0).firstInstallTime } catch (e: Exception) { 0L }
                    -installTime
                }
                2 -> {
                    val installTime = try { pm.getPackageInfo(app.packageName, 0).firstInstallTime } catch (e: Exception) { 0L }
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

    // ── BroadcastReceivers ───────────────────────────────────────────────────
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
            currentArtBase64 = if (artBase64.isNotEmpty()) artBase64 else ""
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
                        city        = prefs.customCity.ifEmpty { "北京" },
                        weather     = prefs.customWeather.ifEmpty { "多云" },
                        temperature = prefs.customTemp.ifEmpty { "18°C" },
                        lat         = prefs.customLat.toDouble(),
                        lng         = prefs.customLng.toDouble(),
                        country     = prefs.customCountry,
                        admin       = prefs.customAdmin
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

    // ── init ─────────────────────────────────────────────────────────────────
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
        try { getApplication<Application>().unregisterReceiver(packageReceiver) }      catch (_: Exception) {}
        try { getApplication<Application>().unregisterReceiver(batteryReceiver) }      catch (_: Exception) {}
        try { getApplication<Application>().unregisterReceiver(mediaUpdateReceiver) }  catch (_: Exception) {}
        try { getApplication<Application>().unregisterReceiver(weatherUpdateReceiver) } catch (_: Exception) {}
    }

    // ── 抽屉排序 ─────────────────────────────────────────────────────────────
    fun loadDrawerPackageOrder() {
        val raw = prefs.drawerPackageOrderCSV
        if (raw.isNotEmpty()) { drawerPackageOrder.value = raw.split(",").filter { it.isNotEmpty() } }
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
                current.add(targetGlobalIndex.coerceIn(0, current.size), packageName)
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
            if (current > 0) { lastDrawerPageSwitchTime = now; drawerPageIndex.value = current - 1 }
        } else if (dropX > screenWidth - 32f) {
            val current = drawerPageIndex.value
            if (current < totalPages - 1) { lastDrawerPageSwitchTime = now; drawerPageIndex.value = current + 1 }
        }
    }

    var lastHomePageSwitchTime = 0L
    fun checkHomeEdgeScroll(dropX: Float, screenWidth: Float, totalPages: Int) {
        val now = System.currentTimeMillis()
        if (now - lastHomePageSwitchTime < 1500) return
        if (dropX < 32f) {
            val current = activePageIndex.value
            if (current > 0) { lastHomePageSwitchTime = now; activePageIndex.value = current - 1 }
        } else if (dropX > screenWidth - 32f) {
            val current = activePageIndex.value
            if (current < totalPages - 1) { lastHomePageSwitchTime = now; activePageIndex.value = current + 1 }
        }
    }

    // ── 应用列表刷新 ─────────────────────────────────────────────────────────
    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { queryAppsFromSystem() }
            _appList.value = apps
            val currentOrder = drawerPackageOrder.value.toMutableList()
            val installedPackages = apps.map { it.packageName }.toSet()
            currentOrder.retainAll { installedPackages.contains(it) }
            val existingPackages = currentOrder.toSet()
            val newApps = apps.filter { !existingPackages.contains(it.packageName) }.sortedBy { it.label.lowercase() }
            for (newApp in newApps) { currentOrder.add(newApp.packageName) }
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

    // ── 外观设置快捷方法 ──────────────────────────────────────────────────────
    fun updateTheme(index: Int)            { prefs.themeColorIndex = index;   currentThemeIndex.value = index }
    fun updateClockStyle(style: String)    { prefs.clockStyle = style;        clockStyle.value = style }
    fun updateWallpaper(wallpaper: String) { prefs.wallpaperName = wallpaper; wallpaperName.value = wallpaper }
    fun toggleShowLabels()                 { val v = !prefs.showLabels;       prefs.showLabels = v;       showLabels.value = v }
    fun toggleShowSystemApps()             { val v = !prefs.showSystemApps;   prefs.showSystemApps = v;   showSystemApps.value = v }
    fun updateDrawerGrid(grid: String)     { prefs.drawerGrid = grid;         drawerGrid.value = grid }
    fun updateIconPackFilter(pack: String) { prefs.iconPackFilter = pack;     iconPackFilter.value = pack }
    fun updateIconRoundness(value: Int)    { prefs.iconRoundness = value;     iconRoundness.value = value }
    fun updateIconSizeScale(value: Int)    { prefs.iconSizeScale = value;     iconSizeScale.value = value }
    fun updateFontSizeSp(value: Int)       { prefs.fontSizeSp = value;        fontSizeSp.value = value }
    fun updateDrawerSortType(value: Int)   { prefs.drawerSortType = value;    drawerSortType.value = value }

    // ── 动画设置 ─────────────────────────────────────────────────────────────
    fun updateTouchEffect(effect: String)    { prefs.touchEffect = effect;      touchEffect.value = effect }
    fun updateHomeTransition(trans: String)  { prefs.homeTransition = trans;    homeTransition.value = trans }
    fun updateDrawerTransition(trans: String){ prefs.drawerTransition = trans;  drawerTransition.value = trans }
    fun updateCrossTransition(trans: String) { prefs.crossTransition = trans;   crossTransition.value = trans }

    fun toggleTouchRandomPool(effect: String) {
        val list = prefs.touchRandomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (list.contains(effect)) list.remove(effect) else list.add(effect)
        val v = list.joinToString(","); prefs.touchRandomPool = v; touchRandomPool.value = v
    }
    fun toggleHomeRandomPool(trans: String) {
        val list = prefs.homeRandomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (list.contains(trans)) list.remove(trans) else list.add(trans)
        val v = list.joinToString(","); prefs.homeRandomPool = v; homeRandomPool.value = v
    }
    fun toggleDrawerRandomPool(trans: String) {
        val list = prefs.drawerRandomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (list.contains(trans)) list.remove(trans) else list.add(trans)
        val v = list.joinToString(","); prefs.drawerRandomPool = v; drawerRandomPool.value = v
    }
    fun toggleCrossRandomPool(trans: String) {
        val list = prefs.crossRandomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (list.contains(trans)) list.remove(trans) else list.add(trans)
        val v = list.joinToString(","); prefs.crossRandomPool = v; crossRandomPool.value = v
    }

    // ── 隐藏应用 ─────────────────────────────────────────────────────────────
    fun toggleHiddenPackage(packageName: String) {
        prefs.toggleHiddenPackage(packageName)
        hiddenPackagesFlow.value = prefs.hiddenPackages
    }

    // ── 卸载 ─────────────────────────────────────────────────────────────────
    fun removeAppFromEverywhereLocal(packageName: String) {
        val dockList = dockPackages.value.toMutableList()
        if (dockList.contains(packageName)) { dockList.remove(packageName); updateDockConfiguration(dockList) }
        val pages = homePages.value.map { page ->
            page.copy(apps = page.apps.map { if (it == packageName) "EMPTY" else it })
        }
        saveHomePages(pages)
        val drawerOrder = drawerPackageOrder.value.toMutableList()
        if (drawerOrder.contains(packageName)) { drawerOrder.remove(packageName); saveDrawerPackageOrder(drawerOrder) }
        _appList.value = _appList.value.filter { it.packageName != packageName }
    }

    fun uninstallApp(context: android.content.Context, app: AppModel) {
        val packageName = app.packageName
        val isInstalled = try { context.packageManager.getPackageInfo(packageName, 0); true } catch (_: Exception) { false }
        if (!isInstalled) {
            removeAppFromEverywhereLocal(packageName)
            android.widget.Toast.makeText(context, "已成功卸载虚拟应用: ${app.label}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val isSystem  = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdated) {
                android.widget.Toast.makeText(context, "系统应用无法卸载：${app.label}", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        } catch (_: Exception) {}
        _uninstallRequestFlow.tryEmit(app)
    }

    fun onUninstallResult(packageName: String, success: Boolean) {
        if (success) { removeAppFromEverywhereLocal(packageName); preUninstallApp.value = null }
        refreshInstalledApps()
    }

    // ── 启动计数 ─────────────────────────────────────────────────────────────
    fun recordAppLaunch(packageName: String) {
        val current = prefs.getAppLaunchCounts().toMutableMap()
        current[packageName] = (current[packageName] ?: 0) + 1
        prefs.saveAppLaunchCounts(current)
        drawerSortType.value = prefs.drawerSortType
    }

    // ── 抽屉文件夹 ───────────────────────────────────────────────────────────
    fun createDrawerFolder(name: String = "新建文件夹") {
        val current = prefs.getDrawerFolders().toMutableList()
        current.add(DrawerFolder("folder_" + java.util.UUID.randomUUID().toString(), name, mutableListOf()))
        prefs.saveDrawerFolders(current); drawerFolders.value = current
    }

    fun renameDrawerFolder(folderId: String, newName: String) {
        val current = prefs.getDrawerFolders().toMutableList()
        current.find { it.id == folderId }?.let { it.name = newName; prefs.saveDrawerFolders(current); drawerFolders.value = current }
    }

    fun deleteDrawerFolder(folderId: String) {
        val current = prefs.getDrawerFolders().toMutableList()
        current.removeAll { it.id == folderId }
        prefs.saveDrawerFolders(current); drawerFolders.value = current
    }

    fun addAppToDrawerFolder(folderId: String, packageName: String) {
        val current = prefs.getDrawerFolders().toMutableList()
        current.forEach { it.packageNames.remove(packageName) }
        current.find { it.id == folderId }?.let {
            if (!it.packageNames.contains(packageName)) it.packageNames.add(packageName)
            prefs.saveDrawerFolders(current); drawerFolders.value = current
        }
    }

    fun removeAppFromDrawerFolder(folderId: String, packageName: String) {
        val current = prefs.getDrawerFolders().toMutableList()
        current.find { it.id == folderId }?.let {
            it.packageNames.remove(packageName); prefs.saveDrawerFolders(current); drawerFolders.value = current
        }
    }

    // ── 布局快照 ─────────────────────────────────────────────────────────────
    fun backupLayoutSnapshot()  { prefs.layoutSnapshotRaw = prefs.drawerFoldersRaw; prefs.hasLayoutSnapshot = true }
    fun restoreLayoutSnapshot(): Boolean {
        if (prefs.hasLayoutSnapshot) { prefs.drawerFoldersRaw = prefs.layoutSnapshotRaw; drawerFolders.value = prefs.getDrawerFolders(); return true }
        return false
    }

    // ── 智能分类 ─────────────────────────────────────────────────────────────
    fun smartCategorizeApps() {
        backupLayoutSnapshot()
        val categories = mapOf(
            "社交"   to listOf("wechat","qq","chat","social","contact","phone","mms","contacts","message","im","社交","微信","腾讯","微博","weibo","talk"),
            "工具"   to listOf("calculator","files","document","weather","memo","calendar","clock","browser","search","desk","file","compass","map","tool","工具","计算器","天气","便签","时钟","浏览器"),
            "游戏"   to listOf("game","play","arcade","puzzle","action","sport","racing","minecraft","chess","gaming","游戏","娱乐","竞技","王者"),
            "影音"   to listOf("music","video","camera","gallery","tv","audio","stream","netease","spotify","youtube","media","player","影音","音乐","相册","相机","视频","播放器"),
            "办公"   to listOf("office","mail","word","excel","pdf","sheet","slide","meeting","scan","email","gmail","outlook","wps","办公","邮箱","文档","扫描"),
            "系统工具" to listOf("settings","launcher","system","vending","security","store","backup","manager","app","service","systemui","系统","设置","安全","管家","应用市场","应用商店"),
            "购物"   to listOf("shop","store","buy","mall","market","pay","amazon","taobao","jd","alipay","购物","淘宝","京东","支付","美团","拼多多")
        )
        val groups = mutableMapOf<String, MutableList<String>>()
        categories.keys.forEach { groups[it] = mutableListOf() }; groups["其他"] = mutableListOf()
        _appList.value.forEach { app ->
            val key = app.packageName.lowercase() + " " + app.label.lowercase()
            var matched = false
            for ((cat, kws) in categories) { if (kws.any { key.contains(it) }) { groups[cat]?.add(app.packageName); matched = true; break } }
            if (!matched) groups["其他"]?.add(app.packageName)
        }
        var cnt = 0
        val newFolders = groups.mapNotNull { (catName, pkgs) ->
            if (pkgs.isNotEmpty()) DrawerFolder("folder_smart_${cnt++}", catName, pkgs.toMutableList()) else null
        }
        prefs.saveDrawerFolders(newFolders); drawerFolders.value = newFolders
    }
}
