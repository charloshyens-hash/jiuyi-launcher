package com.example

import java.io.Serializable

data class HomeScreenPage(
    val pageId: String,
    val apps: List<String> = emptyList(),
    val widgets: List<String> = emptyList()
) : Serializable
