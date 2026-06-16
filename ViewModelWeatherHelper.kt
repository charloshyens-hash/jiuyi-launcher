package com.example

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════════════════════════════
//  天气相关扩展方法（从 LauncherViewModel 拆分，功能与签名完全不变）
// ═══════════════════════════════════════════════════════════════════════════

fun LauncherViewModel.isCoordinateString(str: String): Boolean {
    val trimmed = str.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.matches(Regex("^-?\\d+(\\.\\d+)?[,\\s]+-?\\d+(\\.\\d+)?$"))) return true
    if (trimmed.contains("lat", ignoreCase = true) || trimmed.contains("lon", ignoreCase = true) || trimmed.contains("coord", ignoreCase = true)) return true
    val clean = trimmed.replace("°", "").replace("N", "").replace("S", "").replace("E", "").replace("W", "").replace(",", "").replace(".", "").replace("+", "").replace("-", "").replace(" ", "").trim()
    if (clean.isNotEmpty() && clean.all { it.isDigit() }) return true
    return false
}

fun LauncherViewModel.resolveCoordinatesInBackground(coord: String, weather: String, temp: String) {
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

fun LauncherViewModel.translateWeatherCode(code: Int): String = weatherRepo.translateWeatherCode(code)

fun LauncherViewModel.fetchWeatherForCityOnline(city: String? = null, passLat: Double? = null, passLng: Double? = null, forceRefresh: Boolean = false) {
    viewModelScope.launch {
        val result = weatherRepo.fetchWeatherForCityOnline(city, passLat, passLng, forceRefresh)
        _weatherState.value = _weatherState.value.copy(
            city = result.city, weather = result.weatherText, temperature = result.weatherTemp,
            lat = result.lat, lng = result.lng, country = result.country, admin = result.admin,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}

fun LauncherViewModel.trySyncSystemWeatherSilently() {
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

fun LauncherViewModel.selectCityAndSimulateWeather(city: String, lat: Double? = null, lng: Double? = null, country: String? = null, admin: String? = null, query: String? = null) {
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

fun LauncherViewModel.searchAndSelectCity(query: String) {
    viewModelScope.launch {
        val details = weatherRepo.resolveCityDetails(query)
        val finalLat = details?.lat ?: 39.9042; val finalLng = details?.lng ?: 116.4074
        val finalCountry = details?.country ?: "中国"; val finalAdmin = details?.admin ?: "北京"; val finalTitle = details?.name ?: "北京"
        selectCityAndSimulateWeather(city = finalTitle, lat = finalLat, lng = finalLng, country = finalCountry, admin = finalAdmin, query = query)
    }
}

fun LauncherViewModel.updateWeatherConsent(allowed: Boolean) {
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

fun LauncherViewModel.updateCustomWeather(city: String, cond: String, temp: String) {
    prefs.customCity = city; prefs.customWeather = cond; prefs.customTemp = temp
    prefs.lastWeatherUpdateTime = System.currentTimeMillis()
    _weatherState.value = _weatherState.value.copy(city = city, weather = cond, temperature = temp, lastUpdateTime = System.currentTimeMillis())
}

fun LauncherViewModel.fetchRealWeather(forceRefresh: Boolean = false) {
    if (!prefs.isWeatherOnlineAllowed) { trySyncSystemWeatherSilently(); return }
    if (prefs.customCity.isNotEmpty() && prefs.customCity != "点击设置城市" && !isCoordinateString(prefs.customCity)) {
        _weatherState.value = _weatherState.value.copy(city = prefs.customCity, weather = prefs.customWeather, temperature = prefs.customTemp)
        fetchWeatherForCityOnline(prefs.customCity, forceRefresh = forceRefresh)
        return
    }
    trySyncSystemWeatherSilently()
}

fun LauncherViewModel.searchCityGeo(query: String) {
    _searchJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.isEmpty()) { _citySearchResults.value = emptyList(); return }
    _searchJob = viewModelScope.launch {
        delay(350)
        _citySearchResults.value = weatherRepo.searchCityGeo(trimmed)
    }
}
