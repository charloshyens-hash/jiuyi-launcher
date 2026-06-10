package com.example

import android.content.Context
import android.content.SharedPreferences

class LauncherPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jiuyi_launcher_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME_COLOR_INDEX = "theme_color_index"
        private const val KEY_CLOCK_STYLE = "clock_style"
        private const val KEY_WALLPAPER_NAME = "wallpaper_name"
        private const val KEY_SHOW_LABELS = "show_labels"
        private const val KEY_DRAWER_GRID = "drawer_grid"
        private const val KEY_DOCK_PACKAGES = "dock_packages"
        private const val KEY_HIDDEN_PACKAGES = "hidden_packages"
        private const val KEY_ICON_PACK_FILTER = "icon_pack_filter"
        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        private const val KEY_WEATHER_ONLINE_ALLOWED = "weather_online_allowed"
        private const val KEY_CUSTOM_CITY = "custom_city"
        private const val KEY_CUSTOM_WEATHER = "custom_weather"
        private const val KEY_CUSTOM_TEMP = "custom_temp"
        private const val KEY_MUSIC_WIDGET_MODE = "music_widget_mode"
        private const val KEY_PREFERRED_MUSIC_PACKAGE = "preferred_music_package"
    }

    var musicWidgetMode: Int
        get() = prefs.getInt(KEY_MUSIC_WIDGET_MODE, 0) // 0: System Media Controller, 1: Built-in Custom Stream
        set(value) = prefs.edit().putInt(KEY_MUSIC_WIDGET_MODE, value).apply()

    var preferredMusicPackage: String
        get() = prefs.getString(KEY_PREFERRED_MUSIC_PACKAGE, "com.netease.cloudmusic") ?: "com.netease.cloudmusic"
        set(value) = prefs.edit().putString(KEY_PREFERRED_MUSIC_PACKAGE, value).apply()

    var isWeatherOnlineAllowed: Boolean
        get() = prefs.getBoolean(KEY_WEATHER_ONLINE_ALLOWED, true)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER_ONLINE_ALLOWED, value).apply()

    var customCity: String
        get() = prefs.getString(KEY_CUSTOM_CITY, "点击设置城市") ?: "点击设置城市"
        set(value) = prefs.edit().putString(KEY_CUSTOM_CITY, value).apply()

    var customLat: Float
        get() = prefs.getFloat("custom_lat", 999f)
        set(value) = prefs.edit().putFloat("custom_lat", value).apply()

    var customLng: Float
        get() = prefs.getFloat("custom_lng", 999f)
        set(value) = prefs.edit().putFloat("custom_lng", value).apply()

    var customCountry: String
        get() = prefs.getString("custom_country", "") ?: ""
        set(value) = prefs.edit().putString("custom_country", value).apply()

    var customAdmin: String
        get() = prefs.getString("custom_admin", "") ?: ""
        set(value) = prefs.edit().putString("custom_admin", value).apply()

    var customWeather: String
        get() = prefs.getString(KEY_CUSTOM_WEATHER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_WEATHER, value).apply()

    var customTemp: String
        get() = prefs.getString(KEY_CUSTOM_TEMP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_TEMP, value).apply()

    var lastWeatherUpdateTime: Long
        get() = prefs.getLong("last_weather_update_time", 0L)
        set(value) = prefs.edit().putLong("last_weather_update_time", value).apply()

    var recentCitiesCSV: String
        get() = prefs.getString("recent_cities_pipe_v4", "") ?: ""
        set(value) = prefs.edit().putString("recent_cities_pipe_v4", value).apply()

    fun addRecentCity(city: String, query: String = city) {
        val trimmed = city.trim()
        val queryTrimmed = query.trim()
        if (trimmed.isEmpty()) return
        val currentList = getRecentCityObjects().toMutableList()
        currentList.removeAll { it.name.equals(trimmed, ignoreCase = true) }
        currentList.add(0, RecentCity(trimmed, queryTrimmed))
        val finalList = currentList.take(12)
        saveRecentCityObjects(finalList)
    }

    fun removeRecentCity(city: String) {
        val trimmed = city.trim()
        val currentList = getRecentCityObjects().toMutableList()
        currentList.removeAll { it.name.equals(trimmed, ignoreCase = true) }
        saveRecentCityObjects(currentList)
    }

    fun getRecentCityObjects(): List<RecentCity> {
        val str = prefs.getString("recent_cities_with_query_v1", null)
        if (str != null) {
            if (str.isEmpty()) return emptyList()
            return str.split("|").mapNotNull { entry ->
                val parts = entry.split(":::")
                if (parts.size >= 2) {
                    RecentCity(parts[0].trim(), parts[1].trim())
                } else if (parts.isNotEmpty()) {
                    val fallback = parts[0].trim()
                    if (fallback.isNotEmpty()) RecentCity(fallback, fallback) else null
                } else null
            }
        }
        
        // Fallback to older pipe string/csv
        val oldList = getRecentCitiesList()
        if (oldList.isNotEmpty()) {
            val converted = oldList.map { RecentCity(it, it) }
            saveRecentCityObjects(converted)
            return converted
        }
        return emptyList()
    }

    private fun saveRecentCityObjects(list: List<RecentCity>) {
        val serialized = list.joinToString("|") { "${it.name}:::${it.query}" }
        prefs.edit().putString("recent_cities_with_query_v1", serialized).apply()
        recentCitiesCSV = list.map { it.name }.joinToString("|")
    }

    fun getRecentCitiesList(): List<String> {
        val pipeStr = prefs.getString("recent_cities_pipe_v4", null)
        if (pipeStr != null) {
            return pipeStr.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val csv = prefs.getString("recent_cities_csv2", "") ?: ""
        if (csv.isNotEmpty()) {
            val migrated = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            prefs.edit().putString("recent_cities_pipe_v4", migrated.joinToString("|")).apply()
            return migrated
        }
        return emptyList()
    }

    var themeColorIndex: Int
        get() = prefs.getInt(KEY_THEME_COLOR_INDEX, 2) // 2: Geek PurpleBlue, with fallback choices customizable
        set(value) = prefs.edit().putInt(KEY_THEME_COLOR_INDEX, value).apply()

    var clockStyle: String
        get() = prefs.getString(KEY_CLOCK_STYLE, "Retro Flip") ?: "Retro Flip" // "Retro Flip", "Minimalist", "Analog Classic"
        set(value) = prefs.edit().putString(KEY_CLOCK_STYLE, value).apply()

    var wallpaperName: String
        get() = prefs.getString(KEY_WALLPAPER_NAME, "Warm Sunlight") ?: "Warm Sunlight" // "Warm Sunlight", "Cosmic Wave", "Interactive Matrix", "Starfield Warp", "Minimal Slate"
        set(value) = prefs.edit().putString(KEY_WALLPAPER_NAME, value).apply()

    var showLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_LABELS, value).apply()

    var showSystemApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, value).apply()

    var drawerGrid: String
        get() = prefs.getString(KEY_DRAWER_GRID, "4x6") ?: "4x6" // "4x6", "5x5"
        set(value) = prefs.edit().putString(KEY_DRAWER_GRID, value).apply()

    var iconPackFilter: String
        get() = prefs.getString(KEY_ICON_PACK_FILTER, "Sketch Outline") ?: "Sketch Outline" // Default to Hand-drawn Sketch outline
        set(value) = prefs.edit().putString(KEY_ICON_PACK_FILTER, value).apply()

    // Dock consists of a JSON or comma-separated list of package names.
    // Fixed total of 5 items, by default, menu button position is stored contextually.
    // Let's store comma-separated package names. Special keyword "MENU_BUTTON" represents the menu trigger.
    var dockPackagesCommaSeparated: String
        get() = prefs.getString(KEY_DOCK_PACKAGES, "com.android.contacts,com.android.mms,MENU_BUTTON,com.android.browser,com.android.settings") 
            ?: "com.android.contacts,com.android.mms,MENU_BUTTON,com.android.browser,com.android.settings"
        set(value) = prefs.edit().putString(KEY_DOCK_PACKAGES, value).apply()

    var hiddenPackages: Set<String>
        get() {
            val empty = emptySet<String>()
            return prefs.getStringSet(KEY_HIDDEN_PACKAGES, empty) ?: empty
        }
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, value).apply()

    fun toggleHiddenPackage(pkg: String) {
        val current = hiddenPackages.toMutableSet()
        if (current.contains(pkg)) {
            current.remove(pkg)
        } else {
            current.add(pkg)
        }
        hiddenPackages = current
    }

    fun isPackageHidden(pkg: String): Boolean {
        return hiddenPackages.contains(pkg)
    }
}

data class RecentCity(
    val name: String,
    val query: String
)
