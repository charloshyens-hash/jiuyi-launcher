package com.example.weather

import android.content.Context
import com.example.LauncherPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

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

    // ─── 语言工具函数 ─────────────────────────────────────────────────────────

    /**
     * 获取系统语言标签，精确区分简繁体和香港。
     * 返回值直接用于 city-search-worker 的 lang 参数。
     *   zh-HK / zh-MO → "zh-HK"
     *   zh-TW / zh-Hant → "zh-TW"
     *   zh-* (其余简体) → "zh-CN"
     *   其他 → 主语言码，如 "en"、"ja"、"ko"
     */
    private fun getSystemLangTag(): String {
        val tag = Locale.getDefault().toLanguageTag().lowercase()
        return when {
            tag.startsWith("zh-hk") || tag.startsWith("zh-mo") -> "zh-HK"
            tag.startsWith("zh-tw") || tag.startsWith("zh-hant") -> "zh-TW"
            tag.startsWith("zh") -> "zh-CN"
            else -> Locale.getDefault().language
        }
    }

    /**
     * OpenMeteo / Nominatim 只接受不带地区的语言码（zh、en、ja 等）。
     */
    private fun getOpenMeteoLang(): String {
        val tag = getSystemLangTag().lowercase()
        return if (tag.startsWith("zh")) "zh" else tag.split("-")[0]
    }

    // ─── 天气代码翻译（保持原样）─────────────────────────────────────────────

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

    // ─── 天气获取（保持原样）─────────────────────────────────────────────────

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

        val qWeatherResult = fetchFromQWeather(
            city = finalCity, lat = lat, lng = lng,
            country = currentCountry, admin = currentAdmin
        )
        if (qWeatherResult != null) return@withContext qWeatherResult

        return@withContext fetchFromOpenMeteo(
            city = finalCity, lat = lat, lng = lng,
            country = currentCountry, admin = currentAdmin
        )
    }

    private suspend fun fetchFromQWeather(
        city: String, lat: Double, lng: Double,
        country: String, admin: String
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
                        weatherText = text, weatherTemp = formattedTemp,
                        lat = lat, lng = lng,
                        country = jsonObj.optString("country", country),
                        admin = jsonObj.optString("province", admin),
                        isUpdated = true
                    )
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(context, "WeatherRepository", "[E] Worker fetch failed: ${e.message}")
        }
        return null
    }

    private suspend fun fetchFromOpenMeteo(
        city: String, lat: Double, lng: Double,
        country: String, admin: String
    ): WeatherFetchResult {
        try {
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code"
            val conn = URL(weatherUrl).openConnection() as HttpURLConnection
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
                        city = city, weatherText = translatedCond, weatherTemp = formattedTemp,
                        lat = lat, lng = lng, country = country, admin = admin, isUpdated = true
                    )
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(context, "WeatherRepository", "[E] Open-Meteo forecast failed: ${e.message}")
        }
        val text = prefs.customWeather.ifEmpty { "多云" }
        val temp = prefs.customTemp.ifEmpty { "18°C" }
        return WeatherFetchResult(
            city = city, weatherText = text, weatherTemp = temp,
            lat = lat, lng = lng, country = country, admin = admin, isUpdated = false
        )
    }

    // ─── 城市搜索：一级 city-search-worker（带系统语言 fallback 链）──────────
    //
    // Worker 端已实现完整 fallback 链：
    //   zh-CN  → zh-Hans → zh-CN → zh → en
    //   zh-TW  → zh-TW   → zh-Hant → zh → en
    //   zh-HK  → zh-HK   → zh-Hant → zh → en
    //   en-*   → en → zh
    //   ja/ko/其他 → 目标语言 → en
    // 每个城市在 Worker 内已去重，只返回 fallback 链里最优语言的名字。
    private suspend fun searchFromCityWorker(query: String): List<CityItem> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val lang = getSystemLangTag()
            val workerUrl = "https://citysearch.charlosh.qzz.io/search?q=$encoded&lang=$lang"
            DebugLogger.log(context, "WeatherRepository", "城市一级：请求 city-search-worker，query=$query lang=$lang")
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                DebugLogger.log(context, "WeatherRepository", "城市一级：HTTP 状态码=200")
                val arr = org.json.JSONArray(resp)
                DebugLogger.log(context, "WeatherRepository", "城市一级：解析出 ${arr.length()} 条结果")
                val list = mutableListOf<CityItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name    = obj.optString("name", "")
                    val country = obj.optString("country", "")
                    val admin   = obj.optString("admin", "")
                    val pop     = obj.optLong("population", 0L)
                    val lat     = if (obj.has("lat")) obj.optDouble("lat") else null
                    val lng     = if (obj.has("lng")) obj.optDouble("lng") else null
                    if (name.isNotEmpty()) {
                        list.add(CityItem(
                            name = name, city = name, pinyin = "", initials = "",
                            lat = lat, lng = lng, population = pop,
                            country = country, admin = admin
                        ))
                    }
                }
                list
            } else {
                DebugLogger.log(context, "WeatherRepository", "[W] 城市一级：HTTP 状态码=${conn.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            DebugLogger.log(context, "WeatherRepository", "[E] 城市一级：异常 ${e.message}，进入兜底")
            emptyList()
        }
    }

    // ─── 城市搜索：二级兜底 —— 用用户输入直接查 QWeather，能查到才显示 ────────
    private suspend fun searchFromQWeatherDirect(query: String): CityItem? {
        return try {
            val workerUrl = "https://qweather.charlosh.qzz.io/?city=${URLEncoder.encode(query.trim(), "UTF-8")}"
            DebugLogger.log(context, "WeatherRepository", "城市二级：请求 QWeather Worker，query=$query")
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.optBoolean("success", false)) {
                    // QWeather 查到了，把它作为唯一候选项返回
                    val cityName = jsonObj.optString("city", query)
                    val country  = jsonObj.optString("country", "")
                    val province = jsonObj.optString("province", "")
                    DebugLogger.log(context, "WeatherRepository", "城市二级：QWeather 命中，city=$cityName")
                    CityItem(
                        name       = if (province.isNotEmpty() && province != cityName) "$cityName ($province)" else cityName,
                        city       = cityName,
                        pinyin     = "",
                        initials   = "",
                        lat        = null,
                        lng        = null,
                        population = -1L,
                        country    = country,
                        admin      = province
                    )
                } else {
                    DebugLogger.log(context, "WeatherRepository", "城市二级：QWeather 无结果，进入三级")
                    null
                }
            } else {
                DebugLogger.log(context, "WeatherRepository", "[W] 城市二级：HTTP 状态码=${conn.responseCode}，进入三级")
                null
            }
        } catch (e: Exception) {
            DebugLogger.log(context, "WeatherRepository", "[E] 城市二级：异常 ${e.message}，进入三级")
            null
        }
    }

    // ─── 城市搜索：三级兜底 —— OpenMeteo Geocoding，使用系统语言 ─────────────
    private suspend fun searchFromOpenMeteoGeo(query: String): List<CityItem> {
        val openMeteoLang = getOpenMeteoLang()
        val list = mutableListOf<CityItem>()
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=20&language=$openMeteoLang"
            DebugLogger.log(context, "WeatherRepository", "城市三级：请求 OpenMeteo Geocoding，url=$url")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.has("results")) {
                    val arr = jsonObj.getJSONArray("results")
                    DebugLogger.log(context, "WeatherRepository", "城市三级：OpenMeteo 返回 ${arr.length()} 条结果")
                    for (k in 0 until arr.length()) {
                        val rObj       = arr.getJSONObject(k)
                        val cityName   = rObj.optString("name")
                        val rCountry   = rObj.optString("country")
                        val adminState = rObj.optString("admin1")
                        val latitude   = if (rObj.has("latitude")) rObj.optDouble("latitude") else null
                        val longitude  = if (rObj.has("longitude")) rObj.optDouble("longitude") else null
                        val pop        = if (rObj.has("population")) rObj.optLong("population") else 0L
                        val formattedName = when {
                            rCountry.isNotEmpty() && rCountry != "中国" -> "$cityName, $rCountry"
                            adminState.isNotEmpty() && adminState != cityName -> "$cityName ($adminState)"
                            else -> cityName
                        }
                        list.add(CityItem(
                            name = formattedName, city = cityName,
                            pinyin = "", initials = "",
                            lat = latitude, lng = longitude, population = pop,
                            country = rCountry, admin = adminState
                        ))
                    }
                } else {
                    DebugLogger.log(context, "WeatherRepository", "城市三级：OpenMeteo 无 results 字段，返回空")
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(context, "WeatherRepository", "[E] 城市三级：异常 ${e.message}")
        }
        // 按城市名去重，人口降序
        return list.distinctBy { it.name }.sortedByDescending { it.population ?: 0L }
    }

    // ─── 城市搜索主入口：三级严格互斥 ───────────────────────────────────────
    //
    // 一级：city-search-worker D1 搜索（多语言 fallback，每城市只返回最优名）
    //       → 有结果：直接返回候选列表给用户选择（结束）
    //       → 无结果：进二级
    //
    // 二级：把用户输入传给 QWeather Worker 直接查天气
    //       → QWeather 查到：把该城市作为唯一候选项返回给用户（结束）
    //       → 未查到：进三级
    //
    // 三级：OpenMeteo Geocoding 搜索城市列表
    //       → 有结果：去重后返回候选列表给用户选择（结束）
    //       → 无结果：返回空列表（界面显示"未找到"）
    suspend fun searchCityGeo(query: String): List<CityItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 一级
        val workerResults = searchFromCityWorker(trimmed)
        if (workerResults.isNotEmpty()) {
            DebugLogger.log(context, "WeatherRepository", "城市搜索：一级命中，返回 ${workerResults.size} 条")
            return@withContext workerResults
        }
        DebugLogger.log(context, "WeatherRepository", "[W] 城市搜索：一级无结果，进入二级")

        // 二级
        val qWeatherItem = searchFromQWeatherDirect(trimmed)
        if (qWeatherItem != null) {
            DebugLogger.log(context, "WeatherRepository", "城市搜索：二级命中，返回 1 条")
            return@withContext listOf(qWeatherItem)
        }
        DebugLogger.log(context, "WeatherRepository", "[W] 城市搜索：二级无结果，进入三级")

        // 三级
        val openMeteoResults = searchFromOpenMeteoGeo(trimmed)
        DebugLogger.log(context, "WeatherRepository", "城市搜索：三级返回 ${openMeteoResults.size} 条")
        return@withContext openMeteoResults  // 空列表也直接返回，界面显示"未找到"
    }

    // ─── resolveCityDetails：使用系统语言 ────────────────────────────────────
    suspend fun resolveCityDetails(query: String): CityItem? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null
        val openMeteoLang = getOpenMeteoLang()
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=$openMeteoLang"
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                if (json.has("results")) {
                    val arr = json.getJSONArray("results")
                    if (arr.length() > 0) {
                        val first     = arr.getJSONObject(0)
                        val name      = first.optString("name")
                        val latitude  = first.optDouble("latitude")
                        val longitude = first.optDouble("longitude")
                        val rCountry  = first.optString("country")
                        val rAdmin    = first.optString("admin1")
                        val finalCity = when {
                            rCountry.isNotEmpty() && rCountry != "中国" -> "$name, $rCountry"
                            rAdmin.isNotEmpty() && rAdmin != name -> "$name ($rAdmin)"
                            else -> name
                        }
                        DebugLogger.log(context, "WeatherRepository", "resolveCityDetails：成功 name=$finalCity lat=$latitude lng=$longitude")
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
            DebugLogger.log(context, "WeatherRepository", "[E] resolveCityDetails failed for $query: ${e.message}")
        }
        null
    }

    // ─── 手机定位反查城市名（使用系统语言）────────────────────────────────────
    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val nominatimLang = getOpenMeteoLang()
        try {
            val urlString = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=$nominatimLang"
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
                    // 只在中文下去掉行政单位后缀
                    val cleanCity = if (nominatimLang == "zh") {
                        rawCity.replace("市", "").replace("区", "").replace("县", "")
                    } else rawCity
                    if (cleanCity.isNotEmpty()) return@withContext cleanCity
                }
            }
        } catch (e: Exception) {
            DebugLogger.log(context, "WeatherRepository", "[E] OSM Nominatim reverseGeocode failed: ${e.message}")
        }

        // Fallback to Android Geocoder（使用系统 Locale，不写死中文）
        try {
            val geocoder = android.location.Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val rawCity = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: ""
                val cleanCity = if (nominatimLang == "zh") {
                    rawCity.replace("市", "").replace("区", "").replace("县", "")
                } else rawCity
                if (cleanCity.isNotEmpty()) return@withContext cleanCity
            }
        } catch (e: Exception) {
            DebugLogger.log(context, "WeatherRepository", "[E] Failed system reverseGeocode: ${e.message}")
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
