package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun LauncherBackground(
    wallpaperName: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (wallpaperName) {
            "Warm Sunlight" -> WarmSunlightWallpaper()
            "Cosmic Wave" -> CosmicWaveWallpaper()
            "Interactive Matrix" -> DigitalMatrixRainWallpaper()
            "Starfield Warp" -> StarfieldWarpWallpaper()
            else -> MinimalSlateWallpaper()
        }
    }
}

@Composable
fun WarmSunlightWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "warmSunlight")
    
    // Smooth breathing of warm light centers
    val glow1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow1"
    )

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFFFEF9C3))) { // bright light warm yellow base
        // Large warm sun-glow / orange-yellow gradient in top-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFEDD5), Color(0xFFFEF9C3), Color.Transparent),
                center = Offset(size.width * 0.8f, size.height * 0.15f),
                radius = size.width * glow1
            ),
            radius = size.width * glow1,
            center = Offset(size.width * 0.8f, size.height * 0.15f)
        )

        // Soft peach warm glow in bottom-left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFDFD5), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.85f),
                radius = size.width * 1.2f
            ),
            radius = size.width * 1.2f,
            center = Offset(size.width * 0.1f, size.height * 0.85f)
        )
        
        // Gentle sunny abstract wave / subtle circles for decorative depth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x60FFE4E6), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.width * 0.6f
            ),
            radius = size.width * 0.6f,
            center = Offset(size.width * 0.5f, size.height * 0.5f)
        )
    }
}

@Composable
fun MinimalSlateWallpaper() {
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF131313))) {
        // Draw elegant circular glowing gradients in the corners for depth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x156366F1), Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.width * 0.7f
            ),
            radius = size.width * 0.7f,
            center = Offset(0f, 0f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1006B6D4), Color.Transparent),
                center = Offset(size.width, size.height),
                radius = size.width * 0.8f
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width, size.height)
        )
    }
}

@Composable
fun CosmicWaveWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic")
    
    // Wave 1 offset animation
    val wave1X by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1X"
    )
    val wave1Y by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1Y"
    )

    // Cosmic stars blinking animation
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    // Keep some static particles layout
    val starCount = 40
    val starPositions = remember {
        List(starCount) {
            Offset(Random.nextFloat(), Random.nextFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF090D16))) {
        // Cosmic space ambient base
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x356366F1), Color.Transparent),
                center = Offset(size.width * 0.3f + wave1X, size.height * 0.4f + wave1Y),
                radius = size.width * 0.9f
            ),
            radius = size.width * 0.9f,
            center = Offset(size.width * 0.3f + wave1X, size.height * 0.4f + wave1Y)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x25EC4899), Color.Transparent),
                center = Offset(size.width * 0.8f - wave1X, size.height * 0.7f - wave1Y),
                radius = size.width * 0.8f
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.8f - wave1X, size.height * 0.7f - wave1Y)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x200284C7), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.9f + wave1X * 0.5f),
                radius = size.width * 0.7f
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * 0.5f, size.height * 0.9f + wave1X * 0.5f)
        )

        // Render space particles stars
        starPositions.forEachIndexed { index, pos ->
            val scaleBlink = if (index % 3 == 0) blinkProgress else if (index % 3 == 1) (1.2f - blinkProgress) else 0.6f
            drawCircle(
                color = Color.White.copy(alpha = 0.45f * scaleBlink),
                radius = if (index % 5 == 0) 3.5f else 1.8f,
                center = Offset(pos.x * size.width, pos.y * size.height)
            )
        }
    }
}

@Composable
fun StarfieldWarpWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "starfield")
    val warpProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "warp"
    )

    val starCount = 60
    val starsData = remember {
        List(starCount) {
            // Speed factor, horizontal factor, vertical angle/direction
            val angle = Random.nextFloat() * 2 * Math.PI
            val speed = Random.nextFloat() * 0.7f + 0.3f
            Triple(angle, speed, Random.nextFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF030712))) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxRadius = Math.max(centerX, centerY)

        // Draw radial cyber light in core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1E06B6D4), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = size.width * 0.4f
            ),
            radius = size.width * 0.4f,
            center = Offset(centerX, centerY)
        )

        starsData.forEach { (angle, speed, offset) ->
            // Warp travel algorithm: radius expands outwards based on progress and static offset shift
            val currentProgress = (warpProgress + offset) % 1.0f
            val calculatedDist = currentProgress * maxRadius * speed
            val starX = centerX + Math.cos(angle).toFloat() * calculatedDist
            val starY = centerY + Math.sin(angle).toFloat() * calculatedDist

            // Star trails get longer and thinner as speed increases
            val tailLength = currentProgress * 24f * speed
            val tailX = centerX + Math.cos(angle).toFloat() * (calculatedDist - tailLength)
            val tailY = centerY + Math.sin(angle).toFloat() * (calculatedDist - tailLength)

            if (starX in 0f..size.width && starY in 0f..size.height) {
                // Draw rocket starry trail
                drawLine(
                    color = Color(0xFFA5F3FC).copy(alpha = currentProgress * 0.8f),
                    start = Offset(tailX, tailY),
                    end = Offset(starX, starY),
                    strokeWidth = 1.2f + currentProgress * 2.2f
                )
            }
        }
    }
}

@Composable
fun DigitalMatrixRainWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix")
    val tick by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 100,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tick"
    )

    val streamCount = 20
    val streams = remember {
        List(streamCount) { index ->
            MatrixStream(
                colIndex = index,
                speed = Random.nextInt(3, 8),
                characters = List(15) { Random.nextInt(33, 126).toChar() },
                startY = Random.nextFloat() * -500f
            )
        }
    }

    // Paint Matrix Canvas
    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF020617))) {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GREEN
            textSize = 34f
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
            setARGB(255, 16, 185, 129) // Emerald Theme Color
        }

        drawIntoCanvas { canvas ->
            val colWidth = size.width / streamCount
            streams.forEachIndexed { i, stream ->
                val colX = i * colWidth + 5f
                // Flow down based on vertical animation ticks
                val flowY = stream.startY + (tick * stream.speed * 4) % (size.height + 500f)
                
                stream.characters.forEachIndexed { charIdx, char ->
                    val charY = flowY - (charIdx * 45f)
                    if (charY in 0f..size.height) {
                        // Top item is bright highlights, tails fade out
                        val opacity = (1.0f - (charIdx / stream.characters.size.toFloat())).coerceIn(0.1f, 1.0f)
                        paint.setARGB((opacity * 255).toInt(), 34, 197, 94)
                        if (charIdx == 0) {
                            // Bright white leader
                            paint.setARGB(255, 220, 252, 231)
                        }
                        
                        // Keep flickering character selection dynamically
                        val flickeringChar = if (Random.nextFloat() > 0.95f) Random.nextInt(33, 126).toChar() else char
                        
                        canvas.nativeCanvas.drawText(
                            flickeringChar.toString(),
                            colX,
                            charY,
                            paint
                        )
                    }
                }
            }
        }
    }
}

private data class MatrixStream(
    val colIndex: Int,
    val speed: Int,
    val characters: List<Char>,
    val startY: Float
)
