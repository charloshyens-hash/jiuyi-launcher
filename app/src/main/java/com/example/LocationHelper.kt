package com.example

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun getCurrentLocationAndFill(context: Context, viewModel: LauncherViewModel) {
    try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        var loc: Location? = null
        try {
            if (hasNetwork) loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc == null && hasGps) loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: SecurityException) {}

        if (loc != null) {
            resolveLocationAndFill(context, viewModel, loc)
        } else {
            // Attempt a fresh single update since last known is null (common after turning GPS on)
            try {
                val provider = if (hasNetwork) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    locationManager.getCurrentLocation(
                        provider,
                        null,
                        context.mainExecutor
                    ) { location ->
                        if (location != null) {
                            resolveLocationAndFill(context, viewModel, location)
                        } else {
                            Toast.makeText(context, "未能获取当前位置，请在上方输入查找", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.requestSingleUpdate(provider, object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            resolveLocationAndFill(context, viewModel, location)
                        }
                        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
                        override fun onProviderEnabled(p0: String) {}
                        override fun onProviderDisabled(p0: String) {}
                    }, Looper.getMainLooper())
                }
                Toast.makeText(context, "正在在线检索精准定位...", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(context, "未获得定位授权或服务禁用", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "无法获取最后位置且在线请求失败，请尝试在上方输入查询", Toast.LENGTH_LONG).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "定位功能异常: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun resolveLocationAndFill(context: Context, viewModel: LauncherViewModel, loc: Location) {
    val latitude = loc.latitude
    val longitude = loc.longitude
    viewModel.viewModelScope.launch(Dispatchers.IO) {
        try {
            val reverseUrl = "https://geocoding-api.open-meteo.com/v1/reverse?latitude=$latitude&longitude=$longitude&language=zh"
            val url = java.net.URL(reverseUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = org.json.JSONObject(resp)
                if (jsonObj.has("results")) {
                    val resultsArray = jsonObj.getJSONArray("results")
                    if (resultsArray.length() > 0) {
                        val firstResult = resultsArray.getJSONObject(0)
                        val name = firstResult.optString("name")
                        val country = firstResult.optString("country")
                        val admin = firstResult.optString("admin1")
                        
                        val displayCityName = if (country.isNotEmpty() && country != "中国") {
                            "$name, $country"
                        } else if (admin.isNotEmpty() && admin != name) {
                            "$name ($admin)"
                        } else {
                            name
                        }
                        
                        withContext(Dispatchers.Main) {
                            viewModel.selectCityAndSimulateWeather(
                                city = displayCityName,
                                lat = latitude,
                                lng = longitude,
                                country = country,
                                admin = admin
                            )
                            viewModel.showCitySelectorDialog = false
                        }
                        return@launch
                    }
                }
            }
            // If API didn't return search results cleanly, fall back with lat/lng label
            withContext(Dispatchers.Main) {
                viewModel.selectCityAndSimulateWeather(
                    city = "当前位置",
                    lat = latitude,
                    lng = longitude,
                    country = "",
                    admin = ""
                )
                viewModel.showCitySelectorDialog = false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                viewModel.selectCityAndSimulateWeather(
                    city = "当前位置", 
                    lat = latitude, 
                    lng = longitude,
                    country = "",
                    admin = ""
                )
                viewModel.showCitySelectorDialog = false
                Toast.makeText(context, "网络解析失败，已采用本地GPS定位", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
