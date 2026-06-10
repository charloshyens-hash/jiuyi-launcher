package com.example

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun MusicCassetteWidget(
    themeColor: Color,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ── 授权状态 ─────────────────────────────────────────────────────────────
    var hasPermission by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── 播放信息 ─────────────────────────────────────────────────────────────
    val isPlaying   = viewModel.isMusicPlaying
    val trackName   = viewModel.currentTrackName
    val trackArtist = viewModel.currentTrackArtist
    val artBase64   = viewModel.currentArtBase64
    val position    = viewModel.currentPosition
    val duration    = viewModel.currentDuration

    // ── 关键修复：用 collectAsState() 订阅 Flow，Compose 自动感知变化 ─────────
    val activeSessionPkg by JiuYiMediaService.activeSessionPkgFlow.collectAsState()
    val hasActiveSession = JiuYiMediaService.isServiceRunning && activeSessionPkg.isNotEmpty()

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

    var isExpanded by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(
                    text = "音乐控制授权提醒",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "同步显示歌名、歌手、进度条等信息需要获取系统的【通知使用权】权限。\n\n由于 Android 系统的安全隐私机制，第三方软件无法自动获取该权限，必须由您手动在系统设置中找到并勾选此应用。\n\n点击“允许去开启”后，系统将为您打开设置页，请开启本软件的【通知使用权】开关。",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (ex: Exception) {}
                        }
                    }
                ) {
                    Text("允许并去开启", color = themeColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text("禁止", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 未授权：整个卡片点击跳授权设置 ──────────────────────────────────────
    if (!hasPermission) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable {
                    showPermissionDialog = true
                }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    VinylPlaceholder(themeColor)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "久以金曲",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "点此开启媒体控制权限",
                        color = themeColor.copy(alpha = 0.9f),
                        fontSize = 11.sp
                    )
                }
            }
        }
        return
    }

    // ── 已授权：完整播放器卡片 ────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { isExpanded = !isExpanded }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面（点击唤醒播放器）
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .rotate(if (isPlaying) rotAngle else 0f)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .clickable { bringPlayerToFront(context, viewModel) },
                contentAlignment = Alignment.Center
            ) {
                if (artBitmap != null) {
                    Image(
                        bitmap = artBitmap.asImageBitmap(),
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    )
                } else {
                    VinylPlaceholder(themeColor)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 歌曲信息（点击唤醒播放器）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { bringPlayerToFront(context, viewModel) }
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
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 控制按钮：上一首 / 播放·暂停 / 下一首
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaControlButton(
                    onClick = { viewModel.prevTrack() },
                    size = 28,
                    enabled = true   // 不再门控，始终可点，Service 内部双轨兜底
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

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
                    enabled = true   // 不再门控
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

        // 展开区域：进度条 + 时间（长按触发）
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

private fun bringPlayerToFront(context: android.content.Context, viewModel: LauncherViewModel) {
    try {
        val sessionActivity = JiuYiMediaService.getSessionActivity()
        if (sessionActivity != null) {
            sessionActivity.send()
            return
        }
        val pkg = JiuYiMediaService.getActiveSessionPkg().ifEmpty {
            viewModel.preferredMusicPackage.value
        }
        if (pkg.isNotEmpty()) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                context.startActivity(intent)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("MusicCassette", "bringPlayerToFront failed: ${e.message}")
    }
}

@Composable
private fun VinylPlaceholder(themeColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(themeColor.copy(alpha = 0.6f), Color.Black)
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(40.dp).background(Color.Transparent, CircleShape))
        Box(modifier = Modifier.size(20.dp).background(Color(0xFF1A1A1A), CircleShape))
        Box(modifier = Modifier.size(8.dp).background(Color.White.copy(alpha = 0.8f), CircleShape))
    }
}

@Composable
private fun PlaybackProgressBar(position: Long, duration: Long, themeColor: Color) {
    val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = themeColor,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatDuration(position), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
            Text(text = formatDuration(duration), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun MediaControlButton(
    onClick: () -> Unit,
    size: Int,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp),
        enabled = enabled
    ) { content() }
}

fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    return try {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        flat.contains(context.packageName)
    } catch (e: Exception) { false }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}