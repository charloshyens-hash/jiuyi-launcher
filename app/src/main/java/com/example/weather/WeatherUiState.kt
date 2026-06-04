package com.example.weather

data class WeatherUiState(
    val city: String = "点击设置城市",
    val weather: String = "多云",
    val temperature: String = "18°C",
    val lat: Double? = null,
    val lng: Double? = null,
    val country: String = "",
    val admin: String = "",
    val isLoading: Boolean = false,
    val lastUpdateTime: Long = 0L
)
