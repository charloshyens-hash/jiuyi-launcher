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
     * 返回值直接用于 city-search-worker 和 QWeather Worker 的 lang 参数。
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

    // ─── WMO 天气代码翻译（仅用于 OpenMeteo 兜底路径）────────────────────────
    // QWeather 主路径已通过 lang 参数在服务端完成本地化，此处仅为 OpenMeteo 数字
    // code 提供本地化翻译，覆盖 QWeather 官方支持的全部主要语言。
    fun translateWeatherCode(code: Int): String {
        val lang = getSystemLangTag().lowercase()
        val isZh = lang.startsWith("zh")
        val isJa = lang.startsWith("ja")
        val isKo = lang.startsWith("ko")
        val isDe = lang.startsWith("de")
        val isFr = lang.startsWith("fr")
        val isEs = lang.startsWith("es")
        val isPt = lang.startsWith("pt")
        val isRu = lang.startsWith("ru")
        val isIt = lang.startsWith("it")
        val isNl = lang.startsWith("nl")
        val isAr = lang.startsWith("ar")
        val isTr = lang.startsWith("tr")
        val isId = lang.startsWith("id")
        val isTh = lang.startsWith("th")
        val isVi = lang.startsWith("vi")
        val isPl = lang.startsWith("pl")

        return when (code) {
            0 -> when {
                isZh -> "晴";   isJa -> "晴れ";   isKo -> "맑음"
                isDe -> "Klar"; isFr -> "Clair";  isEs -> "Despejado"
                isPt -> "Limpo"; isRu -> "Ясно";  isIt -> "Sereno"
                isNl -> "Helder"; isAr -> "صافٍ"; isTr -> "Açık"
                isId -> "Cerah"; isTh -> "แจ่มใส"; isVi -> "Quang đãng"
                isPl -> "Bezchmurnie"; else -> "Clear"
            }
            1, 2 -> when {
                isZh -> "多云";   isJa -> "一部曇り";   isKo -> "구름 조금"
                isDe -> "Teilweise bewölkt"; isFr -> "Partiellement nuageux"
                isEs -> "Parcialmente nublado"; isPt -> "Parcialmente nublado"
                isRu -> "Переменная облачность"; isIt -> "Parzialmente nuvoloso"
                isNl -> "Gedeeltelijk bewolkt"; isAr -> "غائم جزئياً"
                isTr -> "Parçalı bulutlu"; isId -> "Berawan sebagian"
                isTh -> "มีเมฆบางส่วน"; isVi -> "Ít mây"
                isPl -> "Częściowe zachmurzenie"; else -> "Partly Cloudy"
            }
            3 -> when {
                isZh -> "阴";   isJa -> "曇り";   isKo -> "흐림"
                isDe -> "Bedeckt"; isFr -> "Couvert"; isEs -> "Nublado"
                isPt -> "Nublado"; isRu -> "Пасмурно"; isIt -> "Coperto"
                isNl -> "Bewolkt"; isAr -> "غائم"; isTr -> "Kapalı"
                isId -> "Mendung"; isTh -> "มีเมฆมาก"; isVi -> "U ám"
                isPl -> "Zachmurzenie"; else -> "Overcast"
            }
            45, 48 -> when {
                isZh -> "雾";  isJa -> "霧";  isKo -> "안개"
                isDe -> "Nebel"; isFr -> "Brouillard"; isEs -> "Niebla"
                isPt -> "Nevoeiro"; isRu -> "Туман"; isIt -> "Nebbia"
                isNl -> "Mist"; isAr -> "ضباب"; isTr -> "Sis"
                isId -> "Kabut"; isTh -> "หมอก"; isVi -> "Sương mù"
                isPl -> "Mgła"; else -> "Fog"
            }
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> when {
                isZh -> "雨";  isJa -> "雨";  isKo -> "비"
                isDe -> "Regen"; isFr -> "Pluie"; isEs -> "Lluvia"
                isPt -> "Chuva"; isRu -> "Дождь"; isIt -> "Pioggia"
                isNl -> "Regen"; isAr -> "مطر"; isTr -> "Yağmur"
                isId -> "Hujan"; isTh -> "ฝน"; isVi -> "Mưa"
                isPl -> "Deszcz"; else -> "Rain"
            }
            56, 57, 66, 67 -> when {
                isZh -> "雨夹雪"; isJa -> "みぞれ"; isKo -> "진눈깨비"
                isDe -> "Schneeregen"; isFr -> "Grésil"; isEs -> "Aguanieve"
                isPt -> "Granizo"; isRu -> "Мокрый снег"; isIt -> "Nevischio"
                isNl -> "Natte sneeuw"; isAr -> "ثلج ممطر"
                isTr -> "Karla karışık yağmur"; isId -> "Hujan salju"
                isTh -> "ลูกเห็บ"; isVi -> "Mưa tuyết"
                isPl -> "Śnieg z deszczem"; else -> "Sleet"
            }
            71, 73, 75, 77, 85, 86 -> when {
                isZh -> "雪";  isJa -> "雪";  isKo -> "눈"
                isDe -> "Schnee"; isFr -> "Neige"; isEs -> "Nieve"
                isPt -> "Neve"; isRu -> "Снег"; isIt -> "Neve"
                isNl -> "Sneeuw"; isAr -> "ثلج"; isTr -> "Kar"
                isId -> "Salju"; isTh -> "หิมะ"; isVi -> "Tuyết"
                isPl -> "Śnieg"; else -> "Snow"
            }
            95, 96, 99 -> when {
                isZh -> "雷阵雨"; isJa -> "雷雨"; isKo -> "뇌우"
                isDe -> "Gewitter"; isFr -> "Orage"; isEs -> "Tormenta"
                isPt -> "Tempestade"; isRu -> "Гроза"; isIt -> "Temporale"
                isNl -> "Onweer"; isAr -> "عاصفة رعدية"
                isTr -> "Gök gürültülü fırtına"; isId -> "Badai petir"
                isTh -> "พายุฝนฟ้าคะนอง"; isVi -> "Giông bão"
                isPl -> "Burza"; else -> "Thunderstorm"
            }
            else -> when {
                isZh -> "多云"; isJa -> "曇り"; isKo -> "흐림"
                isDe -> "Bewölkt"; isFr -> "Nuageux"; isEs -> "Nublado"
                isPt -> "Nublado"; isRu -> "Облачно"; isIt -> "Nuvoloso"
                isNl -> "Bewolkt"; isAr -> "غائم"; isTr -> "Bulutlu"
                isId -> "Berawan"; isTh -> "มีเมฆ"; isVi -> "Nhiều mây"
                isPl -> "Pochmurno"; else -> "Cloudy"
            }
        }
    }

    // ─── 天气获取 ─────────────────────────────────────────────────────────────

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

    /**
     * QWeather Worker 天气查询。
     * 传入系统语言 lang 参数，Worker 在服务端把它转为 QWeather 官方语言码，
     * 直接返回本地化的天气描述（text 字段），客户端无需任何翻译逻辑。
     * city 参数使用传入值（已是系统语言城市名），不覆盖为 QWeather 返回的名称。
     */
    private suspend fun fetchFromQWeather(
        city: String, lat: Double, lng: Double,
        country: String, admin: String
    ): WeatherFetchResult? {
        try {
            val lang = getSystemLangTag()
            val workerUrl = "https://qweather.charlosh.qzz.io/?city=${URLEncoder.encode(city, "UTF-8")}&lang=${URLEncoder.encode(lang, "UTF-8")}"
            android.util.Log.d("WeatherRepository", "QWeather 请求：city=$city lang=$lang")
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.optBoolean("success", false)) {
                    val weather = jsonObj.getJSONObject("weather")
                    val temp = weather.optString("temp")
                    val text = weather.optString("text") // 已由 Worker 本地化
                    val formattedTemp = "$temp°C"
                    prefs.customWeather = text
                    prefs.customTemp = formattedTemp
                    prefs.lastWeatherUpdateTime = System.currentTimeMillis()
                    return WeatherFetchResult(
                        city = city, // 用传入的系统语言城市名，不覆盖
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
            android.util.Log.e("WeatherRepository", "QWeather fetch failed: ${e.message}")
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
                    val translatedCond = translateWeatherCode(code) // 跟随系统语言
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
            android.util.Log.e("WeatherRepository", "Open-Meteo forecast failed: ${e.message}")
        }
        val text = prefs.customWeather.ifEmpty { translateWeatherCode(2) }
        val temp = prefs.customTemp.ifEmpty { "18°C" }
        return WeatherFetchResult(
            city = city, weatherText = text, weatherTemp = temp,
            lat = lat, lng = lng, country = country, admin = admin, isUpdated = false
        )
    }

    // ─── 城市搜索：一级 city-search-worker ───────────────────────────────────
    private suspend fun searchFromCityWorker(query: String): List<CityItem> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val lang = getSystemLangTag()
            val workerUrl = "https://citysearch.charlosh.qzz.io/search?q=$encoded&lang=$lang"
            android.util.Log.d("WeatherRepository", "城市一级：请求 city-search-worker，query=$query lang=$lang")
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(resp)
                android.util.Log.d("WeatherRepository", "城市一级：解析出 ${arr.length()} 条结果")
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
                android.util.Log.w("WeatherRepository", "城市一级：HTTP 状态码=${conn.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "城市一级：异常 ${e.message}，进入兜底")
            emptyList()
        }
    }

    // ─── 城市搜索：二级兜底 —— QWeather Worker 直查（带 lang 参数）──────────
    private suspend fun searchFromQWeatherDirect(query: String): CityItem? {
        return try {
            val lang = getSystemLangTag()
            val workerUrl = "https://qweather.charlosh.qzz.io/?city=${URLEncoder.encode(query.trim(), "UTF-8")}&lang=${URLEncoder.encode(lang, "UTF-8")}"
            android.util.Log.d("WeatherRepository", "城市二级：请求 QWeather Worker，query=$query lang=$lang")
            val conn = URL(workerUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.optBoolean("success", false)) {
                    val cityName = jsonObj.optString("city", query)
                    val country  = jsonObj.optString("country", "")
                    val province = jsonObj.optString("province", "")
                    android.util.Log.d("WeatherRepository", "城市二级：QWeather 命中，city=$cityName")
                    CityItem(
                        name       = cityName,
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
                    android.util.Log.d("WeatherRepository", "城市二级：QWeather 无结果，进入三级")
                    null
                }
            } else {
                android.util.Log.w("WeatherRepository", "城市二级：HTTP 状态码=${conn.responseCode}，进入三级")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "城市二级：异常 ${e.message}，进入三级")
            null
        }
    }

    // ─── 城市搜索：三级兜底 —— OpenMeteo Geocoding ────────────────────────────
    private suspend fun searchFromOpenMeteoGeo(query: String): List<CityItem> {
        val openMeteoLang = getOpenMeteoLang()
        val list = mutableListOf<CityItem>()
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=20&language=$openMeteoLang"
            android.util.Log.d("WeatherRepository", "城市三级：请求 OpenMeteo Geocoding，url=$url")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(resp)
                if (jsonObj.has("results")) {
                    val arr = jsonObj.getJSONArray("results")
                    android.util.Log.d("WeatherRepository", "城市三级：OpenMeteo 返回 ${arr.length()} 条结果")
                    for (k in 0 until arr.length()) {
                        val rObj       = arr.getJSONObject(k)
                        val cityName   = rObj.optString("name")
                        val rCountry   = rObj.optString("country")
                        val adminState = rObj.optString("admin1")
                        val latitude   = if (rObj.has("latitude")) rObj.optDouble("latitude") else null
                        val longitude  = if (rObj.has("longitude")) rObj.optDouble("longitude") else null
                        val pop        = if (rObj.has("population")) rObj.optLong("population") else 0L
                        list.add(CityItem(
                            name = cityName, city = cityName,
                            pinyin = "", initials = "",
                            lat = latitude, lng = longitude, population = pop,
                            country = rCountry, admin = adminState
                        ))
                    }
                } else {
                    android.util.Log.d("WeatherRepository", "城市三级：OpenMeteo 无 results 字段，返回空")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "城市三级：异常 ${e.message}")
        }
        return list.distinctBy { it.name }.sortedByDescending { it.population ?: 0L }
    }

    // ─── 城市搜索主入口 ───────────────────────────────────────────────────────
    suspend fun searchCityGeo(query: String): List<CityItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val workerResults = searchFromCityWorker(trimmed)
        if (workerResults.isNotEmpty()) {
            android.util.Log.d("WeatherRepository", "城市搜索：一级命中，返回 ${workerResults.size} 条")
            return@withContext workerResults
        }
        android.util.Log.w("WeatherRepository", "城市搜索：一级无结果，进入二级")

        val qWeatherItem = searchFromQWeatherDirect(trimmed)
        if (qWeatherItem != null) {
            android.util.Log.d("WeatherRepository", "城市搜索：二级命中，返回 1 条")
            return@withContext listOf(qWeatherItem)
        }
        android.util.Log.w("WeatherRepository", "城市搜索：二级无结果，进入三级")

        val openMeteoResults = searchFromOpenMeteoGeo(trimmed)
        android.util.Log.d("WeatherRepository", "城市搜索：三级返回 ${openMeteoResults.size} 条")
        return@withContext openMeteoResults
    }

    // ─── resolveCityDetails ───────────────────────────────────────────────────
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
                        android.util.Log.d("WeatherRepository", "resolveCityDetails：成功 name=$name lat=$latitude lng=$longitude")
                        return@withContext CityItem(
                            name = name, city = name, pinyin = "", initials = "",
                            lat = latitude, lng = longitude,
                            population = first.optLong("population", 0L),
                            country = rCountry, admin = rAdmin
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "resolveCityDetails failed for $query: ${e.message}")
        }
        null
    }

    // ─── 手机定位反查城市名 ───────────────────────────────────────────────────
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
                    val cleanCity = if (nominatimLang == "zh") {
                        rawCity.replace("市", "").replace("区", "").replace("县", "")
                    } else rawCity
                    if (cleanCity.isNotEmpty()) return@withContext cleanCity
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "OSM Nominatim reverseGeocode failed: ${e.message}")
        }

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