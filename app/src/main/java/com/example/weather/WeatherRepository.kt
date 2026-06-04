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
            val workerUrl = "https://steep-sea-0183.charloshyens-d19.workers.dev/?city=${URLEncoder.encode(city, "UTF-8")}"
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

    private var cachedLocalCities: List<CityItem>? = null

    private val CITIES_JSON = """
        [
          {
            "city": "北京",
            "pinyin": "beijing",
            "initials": "bj",
            "lat": 39.9042,
            "lng": 116.4074
          },
          {
            "city": "上海",
            "pinyin": "shanghai",
            "initials": "sh",
            "lat": 31.2304,
            "lng": 121.4737
          },
          {
            "city": "广州",
            "pinyin": "guangzhou",
            "initials": "gz",
            "lat": 23.1291,
            "lng": 113.2644
          },
          {
            "city": "深圳",
            "pinyin": "shenzhen",
            "initials": "sz",
            "lat": 22.5431,
            "lng": 114.0579
          },
          {
            "city": "杭州",
            "pinyin": "hangzhou",
            "initials": "hz",
            "lat": 30.2741,
            "lng": 120.1551
          },
          {
            "city": "成都",
            "pinyin": "chengdu",
            "initials": "cd",
            "lat": 30.5728,
            "lng": 104.0668
          },
          {
            "city": "武汉",
            "pinyin": "wuhan",
            "initials": "wh",
            "lat": 30.5928,
            "lng": 114.3055
          },
          {
            "city": "南京",
            "pinyin": "nanjing",
            "initials": "nj",
            "lat": 32.0584,
            "lng": 118.7965
          },
          {
            "city": "重庆",
            "pinyin": "chongqing",
            "initials": "cq",
            "lat": 29.5630,
            "lng": 106.5516
          },
          {
            "city": "苏州",
            "pinyin": "suzhou",
            "initials": "sz",
            "lat": 31.2990,
            "lng": 120.6186
          },
          {
            "city": "西安",
            "pinyin": "xian",
            "initials": "xa",
            "lat": 34.3416,
            "lng": 108.9398
          },
          {
            "city": "天津",
            "pinyin": "tianjin",
            "initials": "tj",
            "lat": 39.3434,
            "lng": 117.3616
          },
          {
            "city": "郑州",
            "pinyin": "zhengzhou",
            "initials": "zz",
            "lat": 34.7466,
            "lng": 113.6253
          },
          {
            "city": "哈尔滨",
            "pinyin": "haerbin",
            "initials": "heb",
            "lat": 45.8038,
            "lng": 126.5350
          },
          {
            "city": "长春",
            "pinyin": "changchun",
            "initials": "cc",
            "lat": 43.8171,
            "lng": 125.3235
          },
          {
            "city": "沈阳",
            "pinyin": "shenyang",
            "initials": "sy",
            "lat": 41.8057,
            "lng": 123.4315
          },
          {
            "city": "石家庄",
            "pinyin": "shijiazhuang",
            "initials": "sjz",
            "lat": 38.0423,
            "lng": 114.5149
          },
          {
            "city": "太原",
            "pinyin": "taiyuan",
            "initials": "ty",
            "lat": 37.8732,
            "lng": 112.5621
          },
          {
            "city": "济南",
            "pinyin": "jinan",
            "initials": "jn",
            "lat": 36.6512,
            "lng": 116.9949
          },
          {
            "city": "青岛",
            "pinyin": "qingdao",
            "initials": "qd",
            "lat": 36.0671,
            "lng": 120.3826
          },
          {
            "city": "合肥",
            "pinyin": "hefei",
            "initials": "hf",
            "lat": 31.8206,
            "lng": 117.2272
          },
          {
            "city": "福州",
            "pinyin": "fuzhou",
            "initials": "fz",
            "lat": 26.0745,
            "lng": 119.2965
          },
          {
            "city": "厦门",
            "pinyin": "xiamen",
            "initials": "xm",
            "lat": 24.4798,
            "lng": 118.0894
          },
          {
            "city": "南昌",
            "pinyin": "nanchang",
            "initials": "nc",
            "lat": 28.6820,
            "lng": 115.8579
          },
          {
            "city": "长沙",
            "pinyin": "changsha",
            "initials": "cs",
            "lat": 28.2282,
            "lng": 112.9388
          },
          {
            "city": "南宁",
            "pinyin": "nanning",
            "initials": "nn",
            "lat": 22.8170,
            "lng": 108.3665
          },
          {
            "city": "海口",
            "pinyin": "haikou",
            "initials": "hk",
            "lat": 20.0174,
            "lng": 110.3492
          },
          {
            "city": "昆明",
            "pinyin": "kunming",
            "initials": "km",
            "lat": 25.0406,
            "lng": 102.7122
          },
          {
            "city": "贵阳",
            "pinyin": "guiyang",
            "initials": "gy",
            "lat": 26.6470,
            "lng": 106.6302
          },
          {
            "city": "兰州",
            "pinyin": "lanzhou",
            "initials": "lz",
            "lat": 36.0611,
            "lng": 103.8343
          },
          {
            "city": "西宁",
            "pinyin": "xining",
            "initials": "xn",
            "lat": 36.6171,
            "lng": 101.7782
          },
          {
            "city": "银川",
            "pinyin": "yinchuan",
            "initials": "yc",
            "lat": 38.4872,
            "lng": 106.2309
          },
          {
            "city": "呼和浩特",
            "pinyin": "huhehaote",
            "initials": "hhht",
            "lat": 40.8415,
            "lng": 111.7511
          },
          {
            "city": "拉萨",
            "pinyin": "lasa",
            "initials": "ls",
            "lat": 29.6524,
            "lng": 91.1172
          },
          {
            "city": "乌鲁木齐",
            "pinyin": "wulumuqi",
            "initials": "wlmq",
            "lat": 43.8256,
            "lng": 87.6168
          },
          {
            "city": "宁波",
            "pinyin": "ningbo",
            "initials": "nb",
            "lat": 29.8683,
            "lng": 121.5440
          },
          {
            "city": "温州",
            "pinyin": "wenzhou",
            "initials": "wz",
            "lat": 27.9943,
            "lng": 120.6994
          },
          {
            "city": "绍兴",
            "pinyin": "shaoxing",
            "initials": "sx",
            "lat": 30.0024,
            "lng": 120.5796
          },
          {
            "city": "金华",
            "pinyin": "jinhua",
            "initials": "jh",
            "lat": 29.0781,
            "lng": 119.6475
          },
          {
            "city": "泉州",
            "pinyin": "quanzhou",
            "initials": "qz",
            "lat": 24.8741,
            "lng": 118.6757
          },
          {
            "city": "珠海",
            "pinyin": "zhuhai",
            "initials": "zh",
            "lat": 22.2707,
            "lng": 113.5767
          },
          {
            "city": "东莞",
            "pinyin": "dongguan",
            "initials": "dg",
            "lat": 23.0205,
            "lng": 113.7518
          },
          {
            "city": "佛山",
            "pinyin": "foshan",
            "initials": "fs",
            "lat": 23.0215,
            "lng": 113.1214
          },
          {
            "city": "无锡",
            "pinyin": "wuxi",
            "initials": "wx",
            "lat": 31.4912,
            "lng": 120.3119
          },
          {
            "city": "常州",
            "pinyin": "changzhou",
            "initials": "cz",
            "lat": 31.7833,
            "lng": 119.9667
          },
          {
            "city": "大理",
            "pinyin": "dali",
            "initials": "dl",
            "lat": 25.6065,
            "lng": 100.2676
          },
          {
            "city": "丽江",
            "pinyin": "lijiang",
            "initials": "lj",
            "lat": 26.8550,
            "lng": 100.2244
          },
          {
            "city": "三亚",
            "pinyin": "sanya",
            "initials": "sy",
            "lat": 18.2528,
            "lng": 109.5119
          },
          {
            "city": "桂林",
            "pinyin": "guilin",
            "initials": "gl",
            "lat": 25.2736,
            "lng": 110.2901
          }
        ]
    """.trimIndent()

    private fun loadLocalCities(): List<CityItem> {
        val cached = cachedLocalCities
        if (cached != null) return cached
        
        return try {
            val jsonArray = org.json.JSONArray(CITIES_JSON)
            val list = mutableListOf<CityItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val city = obj.optString("city", "")
                val pinyin = obj.optString("pinyin", "")
                val initials = obj.optString("initials", "")
                val lat = obj.optDouble("lat", 0.0)
                val lng = obj.optDouble("lng", 0.0)
                list.add(
                    CityItem(
                        name = city,
                        city = city,
                        pinyin = pinyin,
                        initials = initials,
                        lat = lat,
                        lng = lng,
                        population = 10000000L - i,
                        country = "中国",
                        admin = city
                    )
                )
            }
            cachedLocalCities = list
            list
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "Failed to parse local cities JSON", e)
            emptyList()
        }
    }

    private fun searchLocalCities(query: String): List<CityItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()
        val allLocal = loadLocalCities()
        
        return allLocal.filter { item ->
            item.city.lowercase().contains(trimmed) ||
            item.pinyin.lowercase().contains(trimmed) ||
            item.initials.lowercase().contains(trimmed)
        }.sortedWith(compareBy<CityItem> { item ->
            val cityLower = item.city.lowercase()
            val pinyinLower = item.pinyin.lowercase()
            val initialsLower = item.initials.lowercase()
            when {
                cityLower == trimmed || pinyinLower == trimmed || initialsLower == trimmed -> 0
                cityLower.startsWith(trimmed) || pinyinLower.startsWith(trimmed) || initialsLower.startsWith(trimmed) -> 1
                else -> 2
            }
        })
    }

    suspend fun searchCityGeo(query: String): List<CityItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        
        // 1. Search locally first
        val localResults = searchLocalCities(trimmed)
        if (localResults.isNotEmpty()) {
            return@withContext localResults
        }

        // 2. Fallback to OpenMeteo geocoding search
        val list = mutableListOf<CityItem>()
        try {
            val openMeteoGeoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(trimmed, "UTF-8")}&count=20&language=zh"
            val url = URL(openMeteoGeoUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
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
                        
                        val formattedName = if (rCountry.isNotEmpty() && rCountry != "中国") {
                            "$cityName, $rCountry"
                        } else if (adminState.isNotEmpty() && adminState != cityName) {
                            "$cityName ($adminState)"
                        } else {
                            cityName
                        }
                        list.add(
                            CityItem(
                                name = formattedName,
                                city = cityName,
                                pinyin = "",
                                initials = "",
                                lat = latitude,
                                lng = longitude,
                                population = pop,
                                country = rCountry,
                                admin = adminState
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "Open-Meteo Geo query failed: ${e.message}")
        }
        list.distinctBy { it.name }.sortedByDescending { it.population ?: 0L }
    }

    suspend fun resolveCityDetails(query: String): CityItem? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null

        // 1. Resolve locally first
        val localResults = searchLocalCities(trimmed)
        if (localResults.isNotEmpty()) {
            return@withContext localResults.first()
        }

        // 2. Fallback to OpenMeteo
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
                        
                        val finalCity = if (rCountry.isNotEmpty() && rCountry != "中国") {
                            "$name, $rCountry"
                        } else if (rAdmin.isNotEmpty() && rAdmin != name) {
                            "$name ($rAdmin)"
                        } else {
                            name
                        }
                        
                        return@withContext CityItem(
                            name = finalCity,
                            city = name,
                            pinyin = "",
                            initials = "",
                            lat = latitude,
                            lng = longitude,
                            population = first.optLong("population", 0L),
                            country = rCountry,
                            admin = rAdmin
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherRepository", "resolveCityDetails failed for $query: ${e.message}")
        }
        null
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        // Try Online OSM Nominatim first to avoid Android Geocoder's "Service not available" failures in virtual environments
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
