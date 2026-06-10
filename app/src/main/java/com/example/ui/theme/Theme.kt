package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JiuyiDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFA5F3D),       // Brand signature Warm Sun Orange
    onPrimary = Color.White,
    primaryContainer = Color(0x3BFA5F3D),
    secondary = Color(0xFF7000FF),     // Deep Electric Violet
    onSecondary = Color.White,
    tertiary = Color(0xFFFEB127),      // Amber Glow
    background = Color(0xFF131313),    // Jiuyi Surface Dark
    surface = Color(0xFF131313),
    onBackground = Color(0xFFE5E2E1),
    onSurface = Color(0xFFE5E2E1)
)

@Composable
fun MyApplicationTheme(
    primaryColor: Color = Color(0xFFFA5F3D), // Support selectable brand theme colors dynamically
    darkTheme: Boolean = true, // Force dark mode experience
    dynamicColor: Boolean = false, // Disable Android 12 dynamic themes
    content: @Composable () -> Unit,
) {
    val dynamicDarkColorScheme = darkColorScheme(
        primary = primaryColor,
        onPrimary = Color.White,
        primaryContainer = primaryColor.copy(alpha = 0.23f),
        secondary = Color(0xFF7000FF),     // Deep Electric Violet
        onSecondary = Color.White,
        tertiary = Color(0xFFFEB127),      // Amber Glow
        background = Color(0xFF131313),    // Jiuyi Surface Dark
        surface = Color(0xFF131313),
        onBackground = Color(0xFFE5E2E1),
        onSurface = Color(0xFFE5E2E1)
    )

    MaterialTheme(
        colorScheme = dynamicDarkColorScheme,
        typography = Typography,
        content = content
    )
}
