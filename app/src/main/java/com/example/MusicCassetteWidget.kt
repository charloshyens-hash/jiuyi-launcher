package com.example

import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Music Cassette 主面板。
 *
 * 架构原则：
 * - 久以桌面仅作为 Android 系统媒体控制组件
 * - 通过 MediaSession / NotificationListenerService 读取播放信息
 * - 禁止内置任何音乐资源或破解任何第三方 API
 *
 * 交互：
 * - 点击封面 / 歌曲名 → 启动原播放器 App
 * - 长按卡片 → 展开详情（进度条 + 时间）
 * - 控制按钮（上一首 / 播放暂停 / 下一首）独立于点击区域
 */
@Composable
fun MusicCassetteWidget(
    themeColor: Color,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ── 权限状态 ──────────────────────────────────────────────────────────────
    val hasPermission = remember {
        isNotificationListenerEnabled(context)
    }
    var showPermissionDialog by remember { mutableStateOf(false) }
    // 每次组合时检查一次权限，从设置页返回后可刷新
    LaunchedEffect(Unit) {
        if (!hasPermission && !showPermissionDialog) {
            showPermissionDialog = true
        }
    }

    // ── 播放信息（来自 ViewModel，由广播更新） ───────────────────────────────
    val isPlaying   = viewModel.isMusicPlaying
    val trackName   = viewModel.currentTrackName
    val trackArtist = viewModel.currentTrackArtist
    val artBase64   = viewModel.currentArtBase64
    val position    = viewModel.currentPosition
    val duration    = viewModel.currentDuration

    // 解码专辑封面
    val artBitmap = remember(artBase64) {
        if (artBase64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(artBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) { null }
        } else null
    }

    // ── 黑胶旋转动画 ─────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val rotAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylAngle"
    )

    // ── 展开/折叠详情 ─────────────────────────────────────────────────────────
    var isExpanded by remember { mutableStateOf(false) }

    // ── 授权弹窗 ──────────────────────────────────────────────────────────────
    if (showPermissionDialog) {
        MusicPermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onGoGrant = { showPermissionDialog = false }
        )
    }

    // ── 卡片主体 ──────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { isExpanded = !isExpanded }
                )
            }
    ) {
        // 主行：封面 + 歌曲信息 + 控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面 / 黑胶唱片
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .rotate(if (isPlaying) rotAngle else 0f)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .clickable { viewModel.launchPreferredMusicApp(context) },
                contentAlignment = Alignment.Center
            ) {
                if (artBitmap != null) {
                    Image(
                        bitmap = artBitmap.asImageBitmap(),
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 黑胶中心孔
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    )
                } else {
                    // 无封面时的默认黑胶样式
                    VinylPlaceholder(themeColor)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 歌曲信息（点击进入原播放器）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.launchPreferredMusicApp(context) }
            ) {
                Text(
                    text = trackName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = trackArtist,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 无权限提示标签
                if (!hasPermission) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠ 需要通知访问权限",
                        color = Color(0xFFFFA040).copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable { showPermissionDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 控制按钮区（完全隔离于点击跳转区域）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaControlButton(
                    onClick = { viewModel.prevTrack() },
                    size = 28,
                    iconSize = 17
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // 播放 / 暂停（带主题色圆形背景）
                IconButton(
                    onClick = { viewModel.toggleMusicPlayback() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(themeColor, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                MediaControlButton(
                    onClick = { viewModel.nextTrack() },
                    size = 28,
                    iconSize = 17
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // ── 展开区域：进度条 + 时间（长按触发） ─────────────────────────────
        if (isExpanded && duration > 0L) {
            Spacer(modifier = Modifier.height(12.dp))
            PlaybackProgressBar(
                position = position,
                duration = duration,
                themeColor = themeColor
            )
        }
    }
}

// ── 黑胶唱片占位符 ─────────────────────────────────────────────────────────────
@Composable
private fun VinylPlaceholder(themeColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        themeColor.copy(alpha = 0.6f),
                        Color.Black
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // 唱片纹路圆环
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.Transparent, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color(0xFF1A1A1A), CircleShape)
        )
        // 中心孔
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
        )
    }
}

// ── 播放进度条 ─────────────────────────────────────────────────────────────────
@Composable
private fun PlaybackProgressBar(
    position: Long,
    duration: Long,
    themeColor: Color
) {
    val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val posText = formatDuration(position)
    val durText = formatDuration(duration)

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = themeColor,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = posText, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
            Text(text = durText, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
        }
    }
}

// ── 媒体控制按钮（通用） ──────────────────────────────────────────────────────
@Composable
private fun MediaControlButton(
    onClick: () -> Unit,
    size: Int,
    iconSize: Int,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp)
    ) {
        content()
    }
}

// ── 工具：检查 NotificationListenerService 是否已授权 ────────────────────────
fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    return try {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        flat.contains(pkgName)
    } catch (e: Exception) { false }
}

// ── 工具：毫秒 → mm:ss ────────────────────────────────────────────────────────
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}