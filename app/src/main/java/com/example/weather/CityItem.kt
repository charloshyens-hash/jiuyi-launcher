package com.example.weather

data class CityItem(
    val name: String, 
    val city: String, 
    val pinyin: String, 
    val initials: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val population: Long? = null,
    val country: String? = null,
    val admin: String? = null
)
