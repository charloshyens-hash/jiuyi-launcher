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

    // ── 播放状态显示本地状态（用于免授权模式） ─────────────────────────────
    var localIsPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying, hasPermission) {
        if (hasPermission) {
            localIsPlaying = isPlaying
        }
    }
    val finalIsPlaying = if (hasPermission) isPlaying else localIsPlaying

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
                    text = "通知使用权授权说明",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "同步显示歌曲名称、歌手、封面和进度条，需要用到系统的【通知使用权】。\n\n由于 Android 系统的安全隐私机制，任何第三方应用都无法使用简单的弹窗直接开启该权限，必须前往系统设置中手动勾选本软件。\n\n目前即便不开启该权限，您也已经可以直接在桌面进行完美的播放/暂停、上一首、下一首切歌操作，不受任何影响！\n\n您是否仍然需要去系统设置页面开启此同步功能？",
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
                    Text("去开启同步", color = themeColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text("不用了", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 播放器文本显示优化 ─────────────────────────────────────────────────
    val preferredPkg by viewModel.preferredMusicPackage.collectAsState()
    val appNameLabel = remember(preferredPkg) {
        if (preferredPkg.isEmpty()) {
            "首选播放器"
        } else {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(preferredPkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                when (preferredPkg) {
                    "com.netease.cloudmusic" -> "网易云音乐"
                    "com.tencent.qqmusic" -> "QQ音乐"
                    "com.kugou.android" -> "酷狗音乐"
                    else -> "已绑定的播放器"
                }
            }
        }
    }

    val displayTrackName = if (hasPermission) {
        trackName.ifEmpty { appNameLabel }
    } else {
        appNameLabel
    }

    val displayTrackArtist = if (hasPermission) {
        trackArtist.ifEmpty { "正在播放" }
    } else {
        "点击此行同步歌词进度控制"
    }

    // ── 统一、始终可用：完整播放器卡片 ─────────────────────────────────────────
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
                    .rotate(if (finalIsPlaying) rotAngle else 0f)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .clickable { bringPlayerToFront(context, viewModel) },
                contentAlignment = Alignment.Center
            ) {
                if (artBitmap != null && hasPermission) {
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

            // 歌曲信息（未授权点击弹窗，已授权点击唤醒播放器）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (!hasPermission) {
                            showPermissionDialog = true
                        } else {
                            bringPlayerToFront(context, viewModel)
                        }
                    }
            ) {
                Text(
                    text = displayTrackName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = displayTrackArtist,
                    color = if (!hasPermission) themeColor.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 控制按钮：上一首 / 播放·暂停 / 下一首 (始终可点击)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaControlButton(
                    onClick = { 
                        viewModel.prevTrack() 
                        if (!hasPermission) {
                            localIsPlaying = true
                        }
                    },
                    size = 28,
                    enabled = true
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = { 
                        viewModel.toggleMusicPlayback() 
                        if (!hasPermission) {
                            localIsPlaying = !localIsPlaying
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(themeColor, CircleShape)
                ) {
                    Icon(
                        imageVector = if (finalIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (finalIsPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                MediaControlButton(
                    onClick = { 
                        viewModel.nextTrack() 
                        if (!hasPermission) {
                            localIsPlaying = true
                        }
                    },
                    size = 28,
                    enabled = true
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

        // 展开区域：进度条 + 时间（长按触发，且已授权有数据时）
        if (isExpanded && duration > 0L && hasPermission) {
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