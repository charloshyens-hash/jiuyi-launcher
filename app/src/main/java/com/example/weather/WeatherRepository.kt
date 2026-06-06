package com.example.weather

import android.content.Context
import com.example.LauncherPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherFetchResult(
    val city: String,
    val weatherText: String,
    val weatherTemp: String,
    val lat: Double,
    val lng: Double,
    val country: String,
    val admin: String,
    val isUpdated: Boolean
)

class WeatherRepository(
    private val context: Context,
    private val prefs: LauncherPrefs
) {

    fun translateWeatherCode(code: Int): String {
        return when (code) {
            0 -> "晴"
            1, 2 -> "多云"
            3 -> "阴"
            45, 48 -> "雾"
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> "小雨"
            56, 57, 66, 67 -> "雨夹雪"
            71, 73, 75, 77, 85, 86 -> "雪"
            95, 96, 99 -> "雷阵雨"
            else -> "多云"
        }
    }

    suspend fun fetchWeatherForCityOnline(
        city: String? = null,
        passLat: Double? = null,
        passLng: Double? = null,
        forceRefresh: Boolean = false
    ): WeatherFetchResult = withContext(Dispatchers.IO) {
        val currentCountry = prefs.customCountry
        val currentAdmin = prefs.customAdmin

        if (!forceRefresh) {
            val currentTime = System.currentTimeMillis()
            val lastUpdate = prefs.lastWeatherUpdateTime
            val diff = currentTime - lastUpdate
            if (diff in 0 until 10 * 60 * 1000 && prefs.customWeather.isNotEmpty() && prefs.customTemp.isNotEmpty()) {
                val dispCity = city ?: prefs.customCity.ifEmpty { "北京" }
                return@withContext WeatherFetchResult(
                    city = dispCity,
                    weatherText = prefs.customWeather,
                    weatherTemp = prefs.customTemp,
                    lat = prefs.customLat.toDouble(),
                    lng = prefs.customLng.toDouble(),
                    country = currentCountry,
                    admin = currentAdmin,
                    isUpdated = false
                )
            }
        }

        var lat: Double? = passLat
        var lng: Double? = passLng

        // If not passed, check if we have saved coordinates in LauncherPrefs
        if (lat == null || lng == null) {
            if (prefs.customLat != 999f && prefs.customLng != 999f) {
                lat = prefs.customLat.toDouble()
                lng = prefs.customLng.toDouble()
            }
        }

        // If we still don't have coordinates, default to Beijing (39.9042, 116.4074)
        if (lat == null || lng == null) {
            lat = 39.9042
            lng = 116.4074
            prefs.customLat = lat.toFloat()
            prefs.customLng = lng.toFloat()
            if (prefs.customCity.isEmpty() || prefs.customCity == "点击设置城市") {
                prefs.customCity = "北京"
                prefs.customCountry = "中国"
                prefs.customAdmin = "北京"
            }
        }

        val finalCity = city ?: prefs.customCity.ifEmpty { "北京" }

        val qWeatherResult = fetchFromQWeather(
            city = finalCity,
            lat = lat,
            lng = lng,
            country = currentCountry,
            admin = currentAdmin
        )

        if (qWeatherResult != null) {
            return@withContext qWeatherResult
        }

        return@withContext fetchFromOpenMeteo(
            city = finalCity,
            lat = lat,
            lng = lng,
            country = currentCountry,
            admin = currentAdmin
        )
    }

    private suspend fun fetchFromQWeather(
        city: String,
        lat: Double,
        lng: Double,
        country: String,
        admin: String
    ): WeatherFetchResult? {
        try {
            val workerUrl = "https://qweather.charlosh.qzz.io/?city=${URLEncoder.encode(city, "UTF-8")}"
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.optBoolean("success", false)) {
                    val weather = jsonObj.getJSONObject("weather")
                    val temp = weather.optString("temp")
                    val text = weather.optString("text")

                    val formattedTemp = "$temp°C"

                    prefs.customWeather = text
                    prefs.customTemp = formattedTemp
                    prefs.lastWeatherUpdateTime = System.currentTimeMillis()

                    return WeatherFetchResult(
                        city = jsonObj.optString("city", city),
                        weatherText = text,
                        weatherTemp = formattedTemp,
                        lat = lat,
                        lng = lng,
                        country = jsonObj.optString("country", country),
                        admin = jsonObj.optString("province", admin),
                        isUpdated = true
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "Worker fetch failed: ${e.message}")
        }
        return null
    }

    private suspend fun fetchFromOpenMeteo(
        city: String,
        lat: Double,
        lng: Double,
        country: String,
        admin: String
    ): WeatherFetchResult {
        try {
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code"
            val url = URL(weatherUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.has("current")) {
                    val currentObj = jsonObj.getJSONObject("current")
                    val temp = currentObj.optDouble("temperature_2m")
                    val code = currentObj.optInt("weather_code")
                    val translatedCond = translateWeatherCode(code)
                    val formattedTemp = "${Math.round(temp)}°C"

                    prefs.customWeather = translatedCond
                    prefs.customTemp = formattedTemp
                    prefs.lastWeatherUpdateTime = System.currentTimeMillis()

                    return WeatherFetchResult(
                        city = city,
                        weatherText = translatedCond,
                        weatherTemp = formattedTemp,
                        lat = lat,
                        lng = lng,
                        country = country,
                        admin = admin,
                        isUpdated = true
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "Open-Meteo forecast failed: ${e.message}")
        }

        // Fallback
        val text = prefs.customWeather.ifEmpty { "多云" }
        val temp = prefs.customTemp.ifEmpty { "18°C" }
        return WeatherFetchResult(
            city = city,
            weatherText = text,
            weatherTemp = temp,
            lat = lat,
            lng = lng,
            country = country,
            admin = admin,
            isUpdated = false
        )
    }

    // ─── 城市搜索：一级 city-search-worker ───────────────────────────────────
    private suspend fun searchFromCityWorker(query: String): List<CityItem> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            // ⚠️ 换成你 city-search-worker 的实际部署地址
            val workerUrl = "https://citysearch.charlosh.qzz.io/search?q=$encoded"
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(resp)
                val list = mutableListOf<CityItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name    = obj.optString("name", "")
                    val country = obj.optString("country", "")
                    val admin   = obj.optString("admin", "")
                    val pop     = obj.optLong("population", 0L)
                    if (name.isNotEmpty()) {
                        list.add(
                            CityItem(
                                name       = name,
                                city       = name,
                                pinyin     = "",
                                initials   = "",
                                lat        = null,
                                lng        = null,
                                population = pop,
                                country    = country,
                                admin      = admin
                            )
                        )
                    }
                }
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "city-search-worker failed: ${e.message}")
            emptyList()
        }
    }

    // ─── 城市搜索主入口：三级兜底 ────────────────────────────────────────────
    suspend fun searchCityGeo(query: String): List<CityItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 一级：city-search-worker（GeoNames D1，支持拼音/缩写/英文/中文别名）
        val workerResults = searchFromCityWorker(trimmed)
        if (workerResults.isNotEmpty()) {
            return@withContext workerResults
        }
        android.util.Log.w("WeatherRepository", "city-search-worker 无结果，进入兜底")

        // 二级兜底候选：直接使用用户原始输入作为城市名（population = -1 作标记）
        val directItem = CityItem(
            name       = trimmed,
            city       = trimmed,
            pinyin     = "",
            initials   = "",
            lat        = null,
            lng        = null,
            population = -1L,
            country    = "",
            admin      = ""
        )

        // 三级兜底：OpenMeteo Geocoding API
        val list = mutableListOf<CityItem>()
        try {
            val openMeteoGeoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(trimmed, "UTF-8")}&count=20&language=zh"
            val conn = URL(openMeteoGeoUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.has("results")) {
                    val resultsArray = jsonObj.getJSONArray("results")
                    for (k in 0 until resultsArray.length()) {
                        val rObj       = resultsArray.getJSONObject(k)
                        val cityName   = rObj.optString("name")
                        val rCountry   = rObj.optString("country")
                        val adminState = rObj.optString("admin1")
                        val latitude   = if (rObj.has("latitude")) rObj.optDouble("latitude") else null
                        val longitude  = if (rObj.has("longitude")) rObj.optDouble("longitude") else null
                        val pop        = if (rObj.has("population")) rObj.optLong("population") else 0L

                        val formattedName = if (rCountry.isNotEmpty() && rCountry != "中国") {
                            "$cityName, $rCountry"
                        } else if (adminState.isNotEmpty() && adminState != cityName) {
                            "$cityName ($adminState)"
                        } else {
                            cityName
                        }
                        list.add(
                            CityItem(
                                name       = formattedName,
                                city       = cityName,
                                pinyin     = "",
                                initials   = "",
                                lat        = latitude,
                                lng        = longitude,
                                population = pop,
                                country    = rCountry,
                                admin      = adminState
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "Open-Meteo Geo 三级兜底失败: ${e.message}")
        }

        val openMeteoResults = list.distinctBy { it.name }.sortedByDescending { it.population ?: 0L }
        // directItem 始终置顶，让用户可以选择直接使用原始输入
        listOf(directItem) + openMeteoResults
    }

    // ─── resolveCityDetails：直接走 OpenMeteo，本地 JSON 已删除 ──────────────
    suspend fun resolveCityDetails(query: String): CityItem? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=zh"
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                if (json.has("results")) {
                    val arr = json.getJSONArray("results")
                    if (arr.length() > 0) {
                        val first    = arr.getJSONObject(0)
                        val name     = first.optString("name")
                        val latitude = first.optDouble("latitude")
                        val longitude = first.optDouble("longitude")
                        val rCountry = first.optString("country")
                        val rAdmin   = first.optString("admin1")

                        val finalCity = if (rCountry.isNotEmpty() && rCountry != "中国") {
                            "$name, $rCountry"
                        } else if (rAdmin.isNotEmpty() && rAdmin != name) {
                            "$name ($rAdmin)"
                        } else {
                            name
                        }

                        return@withContext CityItem(
                            name       = finalCity,
                            city       = name,
                            pinyin     = "",
                            initials   = "",
                            lat        = latitude,
                            lng        = longitude,
                            population = first.optLong("population", 0L),
                            country    = rCountry,
                            admin      = rAdmin
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "resolveCityDetails failed for $query: ${e.message}")
        }
        null
    }

    // ─── 手机定位反查城市名（经纬度唯一使用入口）────────────────────────────
    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=zh"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "WeatherLauncher/1.0")
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.has("address")) {
                    val addr = jsonObj.getJSONObject("address")
                    val rawCity = addr.optString("city", "")
                        .ifEmpty { addr.optString("town", "") }
                        .ifEmpty { addr.optString("village", "") }
                        .ifEmpty { addr.optString("suburb", "") }
                        .ifEmpty { addr.optString("county", "") }
                        .ifEmpty { addr.optString("state", "") }
                    val cleanCity = rawCity.replace("市", "").replace("区", "").replace("县", "")
                    if (cleanCity.isNotEmpty()) {
                        return@withContext cleanCity
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "OSM Nominatim reverseGeocode failed: ${e.message}")
        }

        // Fallback to Android Geocoder
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.CHINESE)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val rawCity = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: ""
                val cleanCity = rawCity.replace("市", "").replace("区", "").replace("县", "")
                if (cleanCity.isNotEmpty()) {
                    return@withContext cleanCity
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "Failed system reverseGeocode: ${e.message}")
        }
        null
    }

    companion object {
        @Volatile
        private var INSTANCE: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WeatherRepository(
                    context.applicationContext,
                    LauncherPrefs(context.applicationContext)
                ).also { INSTANCE = it }
            }
        }
    }
}