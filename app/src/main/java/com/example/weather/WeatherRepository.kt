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

    // ─── 日志写入文件（手机文件管理器可查看）────────────────────────────────
    private fun writeLog(msg: String) {
        try {
            val file = java.io.File(context.getExternalFilesDir(null), "weather_debug.txt")
            val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            file.appendText("[$time] $msg\n")
        } catch (e: Exception) { /* 忽略写入失败 */ }
    }

    fun clearLog() {
        try {
            val file = java.io.File(context.getExternalFilesDir(null), "weather_debug.txt")
            if (file.exists()) file.delete()
        } catch (e: Exception) { }
    }

    // ─── 天气码翻译 ──────────────────────────────────────────────────────────
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

    // ─── 天气主入口 ──────────────────────────────────────────────────────────
    suspend fun fetchWeatherForCityOnline(
        city: String? = null,
        passLat: Double? = null,
        passLng: Double? = null,
        forceRefresh: Boolean = false
    ): WeatherFetchResult = withContext(Dispatchers.IO) {
        val currentCountry = prefs.customCountry
        val currentAdmin = prefs.customAdmin

        if (!forceRefresh) {
            val diff = System.currentTimeMillis() - prefs.lastWeatherUpdateTime
            if (diff in 0 until 10 * 60 * 1000 && prefs.customWeather.isNotEmpty() && prefs.customTemp.isNotEmpty()) {
                val dispCity = city ?: prefs.customCity.ifEmpty { "北京" }
                writeLog("天气：缓存有效，直接返回 city=$dispCity weather=${prefs.customWeather} temp=${prefs.customTemp}")
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

        if (lat == null || lng == null) {
            if (prefs.customLat != 999f && prefs.customLng != 999f) {
                lat = prefs.customLat.toDouble()
                lng = prefs.customLng.toDouble()
            }
        }

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
        writeLog("天气：开始请求，city=$finalCity lat=$lat lng=$lng")

        // 二级：QWeather Worker
        val qWeatherResult = fetchFromQWeather(finalCity, lat, lng, currentCountry, currentAdmin)
        if (qWeatherResult != null) {
            return@withContext qWeatherResult
        }

        // 三级：OpenMeteo 天气兜底
        return@withContext fetchFromOpenMeteo(finalCity, lat, lng, currentCountry, currentAdmin)
    }

    // ─── 二级：QWeather Worker 查天气 ────────────────────────────────────────
    private suspend fun fetchFromQWeather(
        city: String, lat: Double, lng: Double, country: String, admin: String
    ): WeatherFetchResult? {
        writeLog("天气二级：请求 QWeather Worker，city=$city")
        return try {
            val workerUrl = "https://steep-sea-0183.charloshyens-d19.workers.dev/?city=${URLEncoder.encode(city, "UTF-8")}"
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            val code = conn.responseCode
            writeLog("天气二级：HTTP 状态码=$code")

            if (code == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                val success = jsonObj.optBoolean("success", false)
                writeLog("天气二级：success=$success 原始返回=${resp.take(200)}")

                if (success) {
                    val weather = jsonObj.getJSONObject("weather")
                    val temp = weather.optString("temp")
                    val text = weather.optString("text")
                    val formattedTemp = "$temp°C"

                    prefs.customWeather = text
                    prefs.customTemp = formattedTemp
                    prefs.lastWeatherUpdateTime = System.currentTimeMillis()

                    writeLog("天气二级：成功 city=${jsonObj.optString("city", city)} text=$text temp=$formattedTemp")
                    WeatherFetchResult(
                        city = jsonObj.optString("city", city),
                        weatherText = text,
                        weatherTemp = formattedTemp,
                        lat = lat,
                        lng = lng,
                        country = jsonObj.optString("country", country),
                        admin = jsonObj.optString("province", admin),
                        isUpdated = true
                    )
                } else {
                    writeLog("天气二级：success=false，进入三级兜底")
                    null
                }
            } else {
                writeLog("天气二级：HTTP 非200，进入三级兜底")
                null
            }
        } catch (e: Exception) {
            writeLog("天气二级：异常 ${e.message}，进入三级兜底")
            android.util.Log.e("WeatherRepository", "Worker fetch failed: ${e.message}")
            null
        }
    }

    // ─── 三级：OpenMeteo 查天气 ──────────────────────────────────────────────
    private suspend fun fetchFromOpenMeteo(
        city: String, lat: Double, lng: Double, country: String, admin: String
    ): WeatherFetchResult {
        writeLog("天气三级：请求 OpenMeteo 天气，lat=$lat lng=$lng")
        try {
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code"
            val conn = URL(weatherUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val code = conn.responseCode
            writeLog("天气三级：HTTP 状态码=$code")

            if (code == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.has("current")) {
                    val currentObj = jsonObj.getJSONObject("current")
                    val temp = currentObj.optDouble("temperature_2m")
                    val wCode = currentObj.optInt("weather_code")
                    val translatedCond = translateWeatherCode(wCode)
                    val formattedTemp = "${Math.round(temp)}°C"

                    prefs.customWeather = translatedCond
                    prefs.customTemp = formattedTemp
                    prefs.lastWeatherUpdateTime = System.currentTimeMillis()

                    writeLog("天气三级：成功 city=$city text=$translatedCond temp=$formattedTemp")
                    return WeatherFetchResult(
                        city = city,
                        weatherText = translatedCond,
                        weatherTemp = formattedTemp,
                        lat = lat, lng = lng,
                        country = country, admin = admin,
                        isUpdated = true
                    )
                }
            }
        } catch (e: Exception) {
            writeLog("天气三级：异常 ${e.message}")
            android.util.Log.e("WeatherRepository", "Open-Meteo forecast failed: ${e.message}")
        }

        // 最终兜底：返回缓存或默认值
        val text = prefs.customWeather.ifEmpty { "多云" }
        val temp = prefs.customTemp.ifEmpty { "18°C" }
        writeLog("天气三级：全部失败，返回缓存 text=$text temp=$temp")
        return WeatherFetchResult(
            city = city, weatherText = text, weatherTemp = temp,
            lat = lat, lng = lng, country = country, admin = admin,
            isUpdated = false
        )
    }

    // ─── 一级：city-search-worker 搜城市 ────────────────────────────────────
    private suspend fun searchFromCityWorker(query: String): List<CityItem> {
        writeLog("城市一级：请求 city-search-worker，query=$query")
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val workerUrl = "https://city-search-worker.charloshyens-d19.workers.dev/search?q=$encoded"
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            val code = conn.responseCode
            writeLog("城市一级：HTTP 状态码=$code")

            if (code == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                writeLog("城市一级：原始返回=${resp.take(300)}")
                val arr = org.json.JSONArray(resp)
                val list = mutableListOf<CityItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name", "")
                    val country = obj.optString("country", "")
                    val admin = obj.optString("admin", "")
                    val pop = obj.optLong("population", 0L)
                    if (name.isNotEmpty()) {
                        list.add(CityItem(
                            name = name, city = name, pinyin = "", initials = "",
                            lat = null, lng = null, population = pop,
                            country = country, admin = admin
                        ))
                    }
                }
                writeLog("城市一级：解析出 ${list.size} 条结果")
                list
            } else {
                writeLog("城市一级：HTTP 非200，进入兜底")
                emptyList()
            }
        } catch (e: Exception) {
            writeLog("城市一级：异常 ${e.message}，进入兜底")
            android.util.Log.e("WeatherRepository", "city-search-worker failed: ${e.message}")
            emptyList()
        }
    }

    // ─── 城市搜索主入口 ──────────────────────────────────────────────────────
    suspend fun searchCityGeo(query: String): List<CityItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 一级：city-search-worker
        val workerResults = searchFromCityWorker(trimmed)
        if (workerResults.isNotEmpty()) {
            writeLog("城市搜索：一级命中，返回 ${workerResults.size} 条")
            return@withContext workerResults
        }
        writeLog("城市搜索：一级无结果，进入 OpenMeteo 兜底")

        // 兜底：OpenMeteo Geocoding
        val list = mutableListOf<CityItem>()
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(trimmed, "UTF-8")}&count=20&language=zh"
            writeLog("城市兜底：请求 OpenMeteo Geocoding，url=$url")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val code = conn.responseCode
            writeLog("城市兜底：HTTP 状态码=$code")

            if (code == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.has("results")) {
                    val resultsArray = jsonObj.getJSONArray("results")
                    for (k in 0 until resultsArray.length()) {
                        val rObj = resultsArray.getJSONObject(k)
                        val cityName = rObj.optString("name")
                        val rCountry = rObj.optString("country")
                        val adminState = rObj.optString("admin1")
                        val latitude = if (rObj.has("latitude")) rObj.optDouble("latitude") else null
                        val longitude = if (rObj.has("longitude")) rObj.optDouble("longitude") else null
                        val pop = if (rObj.has("population")) rObj.optLong("population") else 0L
                        val formattedName = if (rCountry.isNotEmpty() && rCountry != "中国") "$cityName, $rCountry"
                            else if (adminState.isNotEmpty() && adminState != cityName) "$cityName ($adminState)"
                            else cityName
                        list.add(CityItem(
                            name = formattedName, city = cityName, pinyin = "", initials = "",
                            lat = latitude, lng = longitude, population = pop,
                            country = rCountry, admin = adminState
                        ))
                    }
                    writeLog("城市兜底：OpenMeteo 返回 ${list.size} 条结果")
                } else {
                    writeLog("城市兜底：OpenMeteo 无 results 字段，返回空")
                }
            }
        } catch (e: Exception) {
            writeLog("城市兜底：OpenMeteo 异常 ${e.message}")
            android.util.Log.e("WeatherRepository", "Open-Meteo Geo 三级兜底失败: ${e.message}")
        }

        val directItem = CityItem(
            name = trimmed, city = trimmed, pinyin = "", initials = "",
            lat = null, lng = null, population = -1L, country = "", admin = ""
        )
        val result = listOf(directItem) + list.distinctBy { it.name }.sortedByDescending { it.population ?: 0L }
        writeLog("城市搜索：最终返回 ${result.size} 条（含直接输入项）")
        result
    }

    // ─── resolveCityDetails ──────────────────────────────────────────────────
    suspend fun resolveCityDetails(query: String): CityItem? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null

        writeLog("resolveCityDetails：query=$trimmed")
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
                        val first = arr.getJSONObject(0)
                        val name = first.optString("name")
                        val latitude = first.optDouble("latitude")
                        val longitude = first.optDouble("longitude")
                        val rCountry = first.optString("country")
                        val rAdmin = first.optString("admin1")
                        val finalCity = if (rCountry.isNotEmpty() && rCountry != "中国") "$name, $rCountry"
                            else if (rAdmin.isNotEmpty() && rAdmin != name) "$name ($rAdmin)"
                            else name
                        writeLog("resolveCityDetails：成功 name=$finalCity lat=$latitude lng=$longitude")
                        return@withContext CityItem(
                            name = finalCity, city = name, pinyin = "", initials = "",
                            lat = latitude, lng = longitude,
                            population = first.optLong("population", 0L),
                            country = rCountry, admin = rAdmin
                        )
                    }
                }
            }
        } catch (e: Exception) {
            writeLog("resolveCityDetails：失败 ${e.message}")
            android.util.Log.e("WeatherRepository", "resolveCityDetails failed for $query: ${e.message}")
        }
        null
    }

    // ─── reverseGeocode ──────────────────────────────────────────────────────
    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        writeLog("reverseGeocode：lat=$lat lon=$lon")
        try {
            val urlString = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=zh"
            val conn = URL(urlString).openConnection() as HttpURLConnection
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
                        writeLog("reverseGeocode：OSM 成功，city=$cleanCity")
                        return@withContext cleanCity
                    }
                }
            }
        } catch (e: Exception) {
            writeLog("reverseGeocode：OSM 失败 ${e.message}，尝试系统 Geocoder")
            android.util.Log.e("WeatherRepository", "OSM Nominatim reverseGeocode failed: ${e.message}")
        }

        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.CHINESE)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val rawCity = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: ""
                val cleanCity = rawCity.replace("市", "").replace("区", "").replace("县", "")
                if (cleanCity.isNotEmpty()) {
                    writeLog("reverseGeocode：系统 Geocoder 成功，city=$cleanCity")
                    return@withContext cleanCity
                }
            }
        } catch (e: Exception) {
            writeLog("reverseGeocode：系统 Geocoder 也失败 ${e.message}")
            android.util.Log.e("WeatherRepository", "Failed system reverseGeocode: ${e.message}")
        }
        writeLog("reverseGeocode：全部失败，返回 null")
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