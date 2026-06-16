package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Random

// 🚀 Particle Class for Touch Effects
data class TouchParticle(
    val id: Long,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val color: Color,
    val alpha: Float = 1f,
    val life: Float = 1f, // 1.0 down to 0.0
    val maxLife: Float = 1f,
    val extra: Float = 0f, // Rotation or flapping state
    val type: String = "" // e.g. "firework", "confetti", "exposure", "balloon", "heart", "star", "money", "butterfly"
)

// 🌟 Shared Pager Transition Modifier for Home Grid & App Drawer Grid
fun Modifier.pagerTransition(
    pageOffset: Float,
    effect: String,
    randomPool: String,
    pageIndex: Int
): Modifier = graphicsLayer {
    val absOffset = Math.abs(pageOffset)
    
    val activeEffect = if (effect == "随机") {
        val pool = randomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (pool.isNotEmpty()) {
            // Stable selection per page index
            pool[(pageIndex % pool.size).coerceIn(0, pool.size - 1)]
        } else {
            "默认"
        }
    } else {
        effect
    }
    
    if (activeEffect == "默认" || absOffset >= 1f) {
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        return@graphicsLayer
    }
    
    cameraDistance = 8f * density
    
    when (activeEffect) {
        "卡片堆" -> {
            if (pageOffset < 0f) {
                val scale = 0.85f + (1f - absOffset) * 0.15f
                scaleX = scale
                scaleY = scale
                alpha = 1f - absOffset
                translationX = pageOffset * size.width * 0.4f
            } else {
                translationX = 0f
                alpha = 1f
            }
        }
        "翻滚" -> {
            translationX = pageOffset * size.width
            rotationZ = pageOffset * 60f
            alpha = 1f - absOffset
        }
        "翻转" -> {
            translationX = pageOffset * size.width
            rotationY = pageOffset * 180f
            alpha = if (absOffset > 0.5f) 0f else 1f
        }
        "钟摆" -> {
            transformOrigin = TransformOrigin(0.5f, 0f)
            rotationZ = pageOffset * -30f
            alpha = 1f - absOffset
        }
        "立方体（内）" -> {
            translationX = pageOffset * size.width
            if (pageOffset < 0) {
                transformOrigin = TransformOrigin(1f, 0.5f)
                rotationY = 90f * pageOffset
            } else {
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = 90f * pageOffset
            }
        }
        "立方体（外）" -> {
            translationX = pageOffset * size.width
            if (pageOffset < 0) {
                transformOrigin = TransformOrigin(1f, 0.5f)
                rotationY = -90f * pageOffset
            } else {
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = -90f * pageOffset
            }
        }
        "百叶窗" -> {
            translationX = pageOffset * size.width
            val isLeft = pageOffset > 0
            transformOrigin = TransformOrigin(if (isLeft) 1f else 0f, 0.5f)
            rotationY = pageOffset * 90f
            val s = (1f - absOffset).coerceAtLeast(0f)
            scaleX = s
            alpha = s
        }
        "弦" -> {
            // Chord: pages swing along a curved, string-like path
            translationX = pageOffset * size.width * 0.6f
            val s = 1f - absOffset * 0.15f
            scaleX = s
            scaleY = s
            rotationY = pageOffset * -30f
            rotationZ = pageOffset * -20f
            transformOrigin = TransformOrigin(0.5f, 0f) // Swing from top like a string
            translationY = absOffset * 150f
            alpha = (1f - absOffset).coerceIn(0f, 1f)
        }
        "兄弟连" -> {
            // Join side-by-side like brothers, pivoting at their joint
            val isLeft = pageOffset > 0
            transformOrigin = TransformOrigin(if (isLeft) 1f else 0f, 0.5f)
            rotationY = pageOffset * -45f
            translationX = pageOffset * size.width * 0.8f
            val s = 1f - absOffset * 0.1f
            scaleX = s
            scaleY = s
        }
        "风火轮" -> {
            rotationZ = pageOffset * 360f
            scaleX = 1f - absOffset
            scaleY = 1f - absOffset
            alpha = 1f - absOffset
        }
        "球" -> {
            // Sphere: page wraps around a 3D sphere
            val isLeft = pageOffset > 0
            transformOrigin = TransformOrigin(if (isLeft) 1f else 0f, 0.5f)
            rotationY = pageOffset * 60f
            rotationZ = pageOffset * -15f
            val s = 1f - absOffset * 0.25f
            scaleX = s
            scaleY = s
            translationX = pageOffset * size.width * 0.8f
            translationY = absOffset * absOffset * 100f
            alpha = (1f - absOffset).coerceIn(0f, 1f)
        }
        "圆柱" -> {
            // Cylinder: wrap around a vertical cylinder drum
            val isLeft = pageOffset > 0
            transformOrigin = TransformOrigin(if (isLeft) 1f else 0f, 0.5f)
            rotationY = pageOffset * 50f
            translationX = pageOffset * size.width * 0.8f
            val s = 0.85f + (1f - absOffset) * 0.15f
            scaleX = s
            scaleY = s
            alpha = (1f - absOffset).coerceIn(0f, 1f)
        }
        "龙卷风" -> {
            // Tornado: spins rapidly and flies up into a vortex
            translationX = pageOffset * size.width
            translationY = -absOffset * size.height * 0.6f
            rotationZ = pageOffset * 720f // Spin twice for rapid high-speed vortex
            val s = 1f - absOffset * 0.85f
            scaleX = s
            scaleY = s
            alpha = (1f - absOffset).coerceIn(0f, 1f)
        }
        "双飞燕" -> {
            // Twin Swallows: pages fly apart like swallows taking flight
            val isLeft = pageOffset > 0
            alpha = (1f - absOffset).coerceIn(0f, 1f)
            val s = 1f - absOffset * 0.4f
            scaleX = s
            scaleY = s
            if (isLeft) {
                // Outgoing page: fly up-left and tilt left
                translationX = pageOffset * size.width * 0.3f
                translationY = -absOffset * size.height * 0.3f
                rotationZ = pageOffset * -25f
            } else {
                // Incoming page: fly down-right and tilt right
                translationX = pageOffset * size.width * 0.3f
                translationY = absOffset * size.height * 0.3f
                rotationZ = pageOffset * -25f
            }
        }
        "太极" -> {
            // Tai Chi: orbital yin-yang rotational slide
            translationX = pageOffset * size.width // Neutralize slide
            val angle = pageOffset * Math.PI
            val radiusX = size.width * 0.4f
            val radiusY = size.height * 0.2f
            translationX += Math.sin(angle).toFloat() * radiusX
            translationY = -Math.sin(angle).toFloat() * radiusY // beautiful 3D elliptic orbit
            rotationZ = pageOffset * 180f
            val s = 1f - absOffset * 0.2f
            scaleX = s
            scaleY = s
            alpha = (1f - absOffset).coerceIn(0f, 1f)
        }
        "吃豆豆" -> {
            // Pacman: rapid vertical biting/chomping motion
            translationX = pageOffset * size.width * 0.7f
            val chew = Math.abs(Math.sin(absOffset * Math.PI * 2.5)).toFloat() // mastication oscillation
            scaleY = 1f - chew * 0.4f // chomp vertical down by 40%
            val s = 1f - absOffset * 0.15f
            scaleX = s
            alpha = (1f - absOffset).coerceIn(0f, 1f)
        }
        "时光隧道" -> {
            // Time Tunnel: outgoing shrinks, incoming expands from center depth
            translationX = pageOffset * size.width // Pin in center
            if (pageOffset > 0) {
                val s = 1f - pageOffset * 0.9f
                scaleX = s
                scaleY = s
                alpha = (1f - pageOffset).coerceIn(0f, 1f)
            } else {
                val s = 0.1f + (1f - absOffset) * 0.9f
                scaleX = s
                scaleY = s
                alpha = (1f - absOffset).coerceIn(0f, 1f)
            }
        }
        "开门" -> {
            // Open Door: outgoing swings open to the left, revealing incoming zoomed behind
            translationX = pageOffset * size.width
            if (pageOffset > 0) {
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = pageOffset * -90f
                alpha = (1f - pageOffset).coerceIn(0f, 1f)
            } else {
                scaleX = 0.85f + (1f - absOffset) * 0.15f
                scaleY = 0.85f + (1f - absOffset) * 0.15f
                alpha = 1f
            }
        }
        "翻页" -> {
            // Page Flip: outgoing page flips to top-left like a book page, revealing incoming
            if (pageOffset > 0) {
                translationX = pageOffset * size.width // Pin in center
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = pageOffset * -180f
                alpha = if (pageOffset > 0.5f) 0f else 1f
            } else {
                translationX = pageOffset * size.width // Pin in center
                alpha = 1f
            }
        }
    }
}

// 🎈 Helper: Particle Spawning Logic
fun spawnTouchParticles(
    x: Float,
    y: Float,
    effectType: String,
    randomPool: String,
    activeParticles: SnapshotStateList<TouchParticle>
) {
    val rng = java.util.Random()
    val activeEffect = if (effectType == "随机") {
        val pool = randomPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (pool.isNotEmpty()) pool[rng.nextInt(pool.size)] else "默认"
    } else {
        effectType
    }
    
    if (activeEffect == "默认") return
    
    val count = when (activeEffect) {
        "焰火" -> 25
        "礼花" -> 30
        "曝光" -> 1
        "爱心气球" -> 5
        "心花怒放" -> 12
        "星光四射" -> 15
        "撒钱" -> 8
        "蝴蝶" -> 4
        else -> 0
    }
    
    for (i in 0 until count) {
        val id = rng.nextLong()
        val angle = rng.nextFloat() * 2.0 * Math.PI
        val speed = 2f + rng.nextFloat() * 5f
        val vx = Math.cos(angle).toFloat() * speed
        val vy = Math.sin(angle).toFloat() * speed
        
        val p = when (activeEffect) {
            "焰火" -> {
                val pColors = listOf(Color(0xFFFA5F3D), Color(0xFFFFD15C), Color(0xFFFF488E), Color(0xFF00FFC2), Color(0xFF00D1FF))
                TouchParticle(
                    id = id, x = x, y = y, vx = vx, vy = vy,
                    size = 5f + rng.nextFloat() * 4f,
                    color = pColors[rng.nextInt(pColors.size)],
                    maxLife = 0.5f + rng.nextFloat() * 0.4f,
                    type = "firework"
                )
            }
            "礼花" -> {
                val pColors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color(0xFFFF5722))
                TouchParticle(
                    id = id, x = x, y = y,
                    vx = -2.5f + rng.nextFloat() * 5f,
                    vy = -3f - rng.nextFloat() * 4f,
                    size = 7f + rng.nextFloat() * 6f,
                    color = pColors[rng.nextInt(pColors.size)],
                    maxLife = 1.0f + rng.nextFloat() * 0.6f,
                    type = "confetti"
                )
            }
            "曝光" -> {
                TouchParticle(
                    id = id, x = x, y = y, vx = 0f, vy = 0f,
                    size = 1f,
                    color = Color.White,
                    maxLife = 0.3f,
                    type = "exposure"
                )
            }
            "爱心气球" -> {
                TouchParticle(
                    id = id, x = x, y = y,
                    vx = -0.8f + rng.nextFloat() * 1.6f,
                    vy = -1.2f - rng.nextFloat() * 1.5f,
                    size = 12f + rng.nextFloat() * 8f,
                    color = listOf(Color(0xFFFF3B30), Color(0xFFFF2D55), Color(0xFFFF9500))[rng.nextInt(3)],
                    maxLife = 1.4f + rng.nextFloat() * 0.8f,
                    type = "balloon"
                )
            }
            "心花怒放" -> {
                TouchParticle(
                    id = id, x = x, y = y, vx = vx * 0.7f, vy = vy * 0.7f,
                    size = 8f + rng.nextFloat() * 5f,
                    color = Color(0xFFFF2D55),
                    maxLife = 0.6f + rng.nextFloat() * 0.3f,
                    type = "heart_burst"
                )
            }
            "星光四射" -> {
                TouchParticle(
                    id = id, x = x, y = y, vx = vx * 1.1f, vy = vy * 1.1f,
                    size = 10f + rng.nextFloat() * 6f,
                    color = Color(0xFFFFCC00),
                    maxLife = 0.7f + rng.nextFloat() * 0.4f,
                    type = "star"
                )
            }
            "撒钱" -> {
                TouchParticle(
                    id = id, x = x, y = y,
                    vx = -1.5f + rng.nextFloat() * 3f,
                    vy = -0.8f - rng.nextFloat() * 2f,
                    size = 15f,
                    color = Color(0xFF34C759),
                    maxLife = 1.6f + rng.nextFloat() * 0.6f,
                    type = "money"
                )
            }
            "蝴蝶" -> {
                val pColors = listOf(Color(0xFF5856D6), Color(0xFF5AC8FA), Color(0xFFFF2D55), Color(0xFF4CD964))
                TouchParticle(
                    id = id, x = x, y = y,
                    vx = -1f + rng.nextFloat() * 2f,
                    vy = -0.8f - rng.nextFloat() * 1.5f,
                    size = 11f + rng.nextFloat() * 5f,
                    color = pColors[rng.nextInt(pColors.size)],
                    maxLife = 1.8f + rng.nextFloat() * 0.8f,
                    type = "butterfly"
                )
            }
            else -> null
        }
        if (p != null) {
            activeParticles.add(p)
        }
    }
}

// 🎨 Real-time Particle Rendering on Canvas
fun drawParticleOnCanvas(p: TouchParticle, scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
    val heartPath = Path()
    val starPath = Path()
    
    when (p.type) {
        "firework" -> {
            scope.drawCircle(
                color = p.color.copy(alpha = p.alpha),
                radius = p.size * p.life,
                center = Offset(p.x, p.y)
            )
        }
        "confetti" -> {
            scope.drawIntoCanvas { canvas ->
                val halfSize = p.size / 2f
                val paint = Paint().apply {
                    color = p.color.copy(alpha = p.alpha)
                    style = PaintingStyle.Fill
                }
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.extra)
                canvas.drawRoundRect(
                    left = -halfSize,
                    top = -halfSize,
                    right = halfSize,
                    bottom = halfSize,
                    radiusX = 2f,
                    radiusY = 2f,
                    paint = paint
                )
                canvas.restore()
            }
        }
        "exposure" -> {
            val currentRadius = p.life * 130f
            scope.drawCircle(
                color = p.color.copy(alpha = (1f - p.life)),
                radius = currentRadius,
                center = Offset(p.x, p.y),
                style = Stroke(width = 4f * (1f - p.life))
            )
        }
        "balloon" -> {
            scope.drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(p.x, p.y)
                heartPath.reset()
                val size = p.size * (0.8f + (1f - p.life) * 0.2f)
                val x = 0f
                val y = -size / 2f
                heartPath.moveTo(x, y + size / 4)
                heartPath.cubicTo(x - size / 2, y - size / 2, x - size, y + size / 3, x, y + size)
                heartPath.cubicTo(x + size, y + size / 3, x + size / 2, y - size / 2, x, y + size / 4)
                
                canvas.drawPath(heartPath, Paint().apply {
                    color = p.color.copy(alpha = p.alpha)
                    style = PaintingStyle.Fill
                })
                
                // Draw a cute string tail
                val linePaint = Paint().apply {
                    color = Color.White.copy(alpha = p.alpha * 0.4f)
                    style = PaintingStyle.Stroke
                    strokeWidth = 2f
                }
                val tailPath = Path().apply {
                    moveTo(x, y + size)
                    quadraticTo(x + Math.sin(p.extra.toDouble() * 3).toFloat() * 10f, y + size + 15f, x, y + size + 35f)
                }
                canvas.drawPath(tailPath, linePaint)
                canvas.restore()
            }
        }
        "heart_burst" -> {
            scope.drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(p.x, p.y)
                heartPath.reset()
                val size = p.size
                val x = 0f
                val y = -size / 2f
                heartPath.moveTo(x, y + size / 4)
                heartPath.cubicTo(x - size / 2, y - size / 2, x - size, y + size / 3, x, y + size)
                heartPath.cubicTo(x + size, y + size / 3, x + size / 2, y - size / 2, x, y + size / 4)
                
                canvas.drawPath(heartPath, Paint().apply {
                    color = p.color.copy(alpha = p.alpha)
                    style = PaintingStyle.Fill
                })
                canvas.restore()
            }
        }
        "star" -> {
            scope.drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(p.x, p.y)
                starPath.reset()
                val spikes = 5
                val outerRadius = p.size
                val innerRadius = p.size * 0.45f
                var rot = p.extra * Math.PI / 180.0 - Math.PI / 2
                val step = Math.PI / spikes
                starPath.moveTo((Math.cos(rot) * outerRadius).toFloat(), (Math.sin(rot) * outerRadius).toFloat())
                for (i in 0 until spikes * 2) {
                    val r = if (i % 2 == 0) outerRadius else innerRadius
                    rot += step
                    starPath.lineTo((Math.cos(rot) * r).toFloat(), (Math.sin(rot) * r).toFloat())
                }
                starPath.close()
                canvas.drawPath(starPath, Paint().apply {
                    color = p.color.copy(alpha = p.alpha)
                    style = PaintingStyle.Fill
                })
                canvas.restore()
            }
        }
        "money" -> {
            scope.drawIntoCanvas { canvas ->
                val width = p.size * 1.3f
                val height = p.size * 0.7f
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.extra * 15f)
                
                val pBill = Paint().apply {
                    color = p.color.copy(alpha = p.alpha)
                    style = PaintingStyle.Fill
                }
                canvas.drawRoundRect(-width/2f, -height/2f, width/2f, height/2f, 3f, 3f, pBill)
                
                // Outer clean thin border
                val pBorder = Paint().apply {
                    color = Color.White.copy(alpha = p.alpha * 0.7f)
                    style = PaintingStyle.Stroke
                    strokeWidth = 1.5f
                }
                canvas.drawRoundRect(-width/2f + 2f, -height/2f + 2f, width/2f - 2f, height/2f - 2f, 2f, 2f, pBorder)
                canvas.restore()
            }
        }
        "butterfly" -> {
            scope.drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(p.x, p.y)
                
                // wings flap speed derived from current scale/life
                val flap = Math.abs(Math.sin((p.life * 25.0))).toFloat()
                
                val wingPaint = Paint().apply {
                    color = p.color.copy(alpha = p.alpha)
                    style = PaintingStyle.Fill
                }
                
                // Left wings
                canvas.drawOval(-p.size * flap, -p.size * 0.6f, 0f, 0f, wingPaint)
                canvas.drawOval(-p.size * 0.7f * flap, 0f, 0f, p.size * 0.5f, wingPaint)
                
                // Right wings
                canvas.drawOval(0f, -p.size * 0.6f, p.size * flap, 0f, wingPaint)
                canvas.drawOval(0f, 0f, p.size * 0.7f * flap, p.size * 0.5f, wingPaint)
                
                // Tiny body
                canvas.drawOval(-1.5f, -p.size * 0.7f, 1.5f, p.size * 0.7f, Paint().apply {
                    color = Color.White.copy(alpha = p.alpha)
                    style = PaintingStyle.Fill
                })
                
                canvas.restore()
            }
        }
    }
}

// 🏢 Core Launcher Animation Center Panel Screen
@Composable
fun LauncherAnimationCenter(
    viewModel: LauncherViewModel,
    themeColor: Color,
    onClose: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("指尖", "桌面", "列表", "列表进出")
    
    // Preferences Flows
    val selectedTouchEffect by viewModel.touchEffect.collectAsState()
    val selectedHomeTransition by viewModel.homeTransition.collectAsState()
    val selectedDrawerTransition by viewModel.drawerTransition.collectAsState()
    val selectedCrossTransition by viewModel.crossTransition.collectAsState()
    
    val touchPool by viewModel.touchRandomPool.collectAsState()
    val homePool by viewModel.homeRandomPool.collectAsState()
    val drawerPool by viewModel.drawerRandomPool.collectAsState()
    val crossPool by viewModel.crossRandomPool.collectAsState()
    
    val activeTouchList = listOf("默认", "随机", "焰火", "礼花", "曝光", "爱心气球", "心花怒放", "星光四射", "撒钱", "蝴蝶")
    
    val activeHomeList = listOf(
        "默认", "随机", "卡片堆", "翻滚", "翻转", "钟摆", 
        "立方体（内）", "立方体（外）", "百叶窗", "弦", "兄弟连", 
        "风火轮", "球", "圆柱", "龙卷风", "双飞燕", "太极", 
        "吃豆豆", "时光隧道", "开门", "翻页"
    )
    
    val activeCrossList = listOf("默认", "随机", "内缩放", "外缩放", "风车", "电视机")
    
    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Color(0xF20F0F13) // Deep dark translucent premium theme
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)
        ) {
            // Header Bar Choice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Launcher 动效引擎",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "极致流畅 · 全系统物理运动自选",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClose()
                    },
                    modifier = Modifier
                        .background(Color(0x1BFFFFFF), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Tab Selection Rows
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = themeColor,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = themeColor
                    )
                },
                divider = {
                    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(1F, 1F, 1F, 0.08F)))
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = index
                        },
                        text = {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Core Config Screen Body
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> { // Touch feedback
                        EffectListVerticalGrid(
                            items = activeTouchList,
                            selectedValue = selectedTouchEffect,
                            poolStr = touchPool,
                            themeColor = themeColor,
                            onSelect = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateTouchEffect(it) 
                                showToast("指提手势反馈特效：已设定为 $it")
                            },
                            onTogglePool = { viewModel.toggleTouchRandomPool(it) },
                            type = "touch"
                        )
                    }
                    1 -> { // Home Screen transitions
                        EffectListVerticalGrid(
                            items = activeHomeList,
                            selectedValue = selectedHomeTransition,
                            poolStr = homePool,
                            themeColor = themeColor,
                            onSelect = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateHomeTransition(it)
                                showToast("主屏幕翻页特效：已设定为 $it")
                            },
                            onTogglePool = { viewModel.toggleHomeRandomPool(it) },
                            type = "page"
                        )
                    }
                    2 -> { // App Drawer transitions
                        EffectListVerticalGrid(
                            items = activeHomeList,
                            selectedValue = selectedDrawerTransition,
                            poolStr = drawerPool,
                            themeColor = themeColor,
                            onSelect = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateDrawerTransition(it) 
                                showToast("应用抽屉翻页特效：已设定为 $it")
                            },
                            onTogglePool = { viewModel.toggleDrawerRandomPool(it) },
                            type = "drawer"
                        )
                    }
                    3 -> { // Cross Transition
                        EffectListVerticalGrid(
                            items = activeCrossList,
                            selectedValue = selectedCrossTransition,
                            poolStr = crossPool,
                            themeColor = themeColor,
                            onSelect = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateCrossTransition(it)
                                showToast("抽屉进出切屏特效：已设定为 $it")
                            },
                            onTogglePool = { viewModel.toggleCrossRandomPool(it) },
                            type = "cross"
                        )
                    }
                }
            }
        }
    }
}

// 🎡 High-Performance Config Grid
@Composable
fun EffectListVerticalGrid(
    items: List<String>,
    selectedValue: String,
    poolStr: String,
    themeColor: Color,
    onSelect: (String) -> Unit,
    onTogglePool: (String) -> Unit,
    type: String
) {
    val poolSet = remember(poolStr) { poolStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(items) { idx, name ->
            val isSelected = name == selectedValue
            val isInPool = poolSet.contains(name)
            
            // Animation for scale feedback on select clicking
            var isClicked by remember { mutableStateOf(false) }
            val scaleAnim by animateFloatAsState(
                targetValue = if (isClicked) 0.96f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                finishedListener = { isClicked = false },
                label = "scale"
            )
            
            Box(
                modifier = Modifier
                    .scale(scaleAnim)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x0CFFFFFF))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) themeColor else Color(0x1BFFFFFF),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        isClicked = true
                        onSelect(name)
                    }
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Preview Frame Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0AFFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        LiveEffectPreview(name = name, type = type)
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Information Labels
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Checkbox for "Locking Random Pool Range"
                        if (name != "默认" && name != "随机") {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isInPool) themeColor.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isInPool) themeColor else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onTogglePool(name) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isInPool) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Random Pool Enabled",
                                        tint = themeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (name == "随机") {
                        Text(
                            text = "支持右侧锁定独立候选池",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 9.sp,
                            modifier = Modifier.align(Alignment.Start).padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// 📽️ self-contained Loop Preview Box for visual choice comprehension
@Composable
fun LiveEffectPreview(name: String, type: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "preview")
    
    when (type) {
        "touch" -> {
            // Auto click simulated inside box every 1.5s
            val pList = remember { mutableStateListOf<TouchParticle>() }
            
            var triggerTick by remember { mutableStateOf(0) }
            val ticker by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "touch_ticks"
            )
            
            // Observe ticker fractions to simulate touch down click
            val lastPulseValueVal = remember { mutableStateOf(0f) }
            LaunchedEffect(ticker) {
                if (ticker < lastPulseValueVal.value) {
                    triggerTick += 1
                }
                lastPulseValueVal.value = ticker
            }
            
            LaunchedEffect(triggerTick) {
                if (name != "默认" && name != "随机") {
                    spawnTouchParticles(120f, 150f, name, "", pList)
                }
            }
            
            // Inner mini frame tick particle position translation
            LaunchedEffect(pList.size) {
                if (pList.isNotEmpty()) {
                    while (pList.isNotEmpty()) {
                        delay(16)
                        val dt = 0.016f
                        val iterator = pList.listIterator()
                        while (iterator.hasNext()) {
                            val p = iterator.next()
                            val nextLife = p.life - dt / p.maxLife
                            if (nextLife <= 0f) {
                                iterator.remove()
                            } else {
                                val nextVy = if (p.type == "balloon" || p.type == "butterfly") p.vy - 0.4f * dt else p.vy + 3f * dt
                                val drift = if (p.type == "balloon" || p.type == "butterfly" || p.type == "money" || p.type == "confetti") {
                                    Math.sin(nextLife * 12.0 + p.id).toFloat() * 1.2f
                                } else 0f
                                iterator.set(
                                    p.copy(
                                        x = p.x + (p.vx + drift) * 100f * dt,
                                        y = p.y + nextVy * 100f * dt,
                                        vy = nextVy,
                                        life = nextLife,
                                        alpha = nextLife,
                                        extra = p.extra + p.vx * dt * 4f
                                    )
                                )
                            }
                        }
                    }
                }
            }
            
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Small click circle pulse indicator
                    val radiusPulse = ticker * 20f
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - ticker) * 0.4f),
                        radius = radiusPulse,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                    
                    // Render current live particles
                    pList.forEach { p ->
                        // Normalize positions to match the preview Box relative center coordinate system
                        val relativeP = p.copy(
                            x = size.width / 2f + (p.x - 120f),
                            y = size.height / 2f + (p.y - 150f)
                        )
                        drawParticleOnCanvas(relativeP, this)
                    }
                }
            }
        }
        
        "page", "drawer" -> {
            // Simulated side page swiping back and forth
            val swipeFraction by infiniteTransition.animateFloat(
                initialValue = -1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "swipe"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                // We draw two tiny simulated layout pages
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.7f)
                            .pagerTransition(
                                pageOffset = swipeFraction,
                                effect = name,
                                randomPool = "卡片堆,翻页",
                                pageIndex = 0
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF6366F1).copy(alpha = 0.5f))
                            .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Page A", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.7f)
                            .pagerTransition(
                                pageOffset = swipeFraction - 1f,
                                effect = name,
                                randomPool = "卡片堆,翻页",
                                pageIndex = 1
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEC4899).copy(alpha = 0.5f))
                            .border(1.dp, Color(0xFFEC4899), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Page B", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        "cross" -> {
            // Simulated entering/exiting home pager and draw app grid inside the mini panel
            val crossFraction by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "cross"
            )
            
            val density = LocalDensity.current.density
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Apply cross transition transforms
                val isSelectedHome = crossFraction < 0.5f
                val activeType = if (name == "随机") "内缩放" else name
                
                // Miniature Home
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.85f)
                        .graphicsLayer {
                            cameraDistance = 8f * density
                            val progress = crossFraction // 0 is Home, 1 is Drawer
                            alpha = (1f - progress)
                            when (activeType) {
                                "默认" -> {
                                    // Slide down
                                    translationY = -progress * 120f
                                }
                                "内缩放" -> {
                                    val s = 1f + progress * 0.4f
                                    scaleX = s
                                    scaleY = s
                                }
                                "外缩放" -> {
                                    val s = 1f - progress * 0.4f
                                    scaleX = s
                                    scaleY = s
                                }
                                "风车" -> {
                                    val s = 1f - progress
                                    scaleX = s
                                    scaleY = s
                                    rotationZ = -(progress * 180f)
                                }
                                "电视机" -> {
                                    val tvProgress = (1f - progress)
                                    scaleX = if (tvProgress > 0.5f) 1f else tvProgress * 2f
                                    scaleY = if (tvProgress < 0.5f) 0.05f else (tvProgress - 0.5f) * 2f
                                }
                            }
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF5722).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFFFF5722).copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("主屏幕", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                
                // Miniature Drawer apps list
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.85f)
                        .graphicsLayer {
                            cameraDistance = 8f * density
                            val progress = crossFraction // 0 is Home, 1 is Drawer
                            alpha = progress
                            when (activeType) {
                                "默认" -> {
                                    // Slide up
                                    translationY = (1f - progress) * 120f
                                }
                                "内缩放" -> {
                                    val s = 0.6f + progress * 0.4f
                                    scaleX = s
                                    scaleY = s
                                }
                                "外缩放" -> {
                                    val s = 1.4f - progress * 0.4f
                                    scaleX = s
                                    scaleY = s
                                }
                                "风车" -> {
                                    val s = progress
                                    scaleX = s
                                    scaleY = s
                                    rotationZ = (1f - progress) * 180f
                                }
                                "电视机" -> {
                                    scaleX = if (progress > 0.5f) 1f else progress * 2f
                                    scaleY = if (progress < 0.5f) 0.05f else (progress - 0.5f) * 2f
                                }
                            }
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00bcd4).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFF00bcd4).copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("应用抽屉", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
