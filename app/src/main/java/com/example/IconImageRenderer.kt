package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun IconImageRenderer(
    drawable: Drawable,
    iconStyle: String,
    themeColor: Color,
    modifier: Modifier = Modifier
) {
    // Generate bitmap in memory from resources
    val imageBitmap = remember(drawable) {
        val width = drawable.intrinsicWidth.coerceAtLeast(48)
        val height = drawable.intrinsicHeight.coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }

    Canvas(modifier = modifier) {
        val filterColor = when (iconStyle) {
            "Minimalist" -> ColorFilter.tint(themeColor) // Pure minimalist tint
            "Vintage Pixel" -> ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix().apply {
                    // Vintage sepia/low-saturated filter
                    setToScale(0.9f, 0.85f, 0.75f, 1.0f)
                }
            )
            "Sketch Outline" -> ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,  // invert and outline high contrast style
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            else -> null // Raw Native
        }

        drawImage(
            image = imageBitmap,
            colorFilter = filterColor
        )
    }
}
