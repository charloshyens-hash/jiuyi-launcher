package com.example

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 首次启用 Music Cassette 时的授权引导弹窗。
 * 说明久以桌面不读取版权内容，仅通过系统 MediaSession / NotificationListenerService
 * 读取当前播放信息并控制播放，引导用户前往系统通知访问权限页面。
 *
 * onDismiss  —— 用户点击"暂不授权"或关闭弹窗
 * onGoGrant  —— 用户点击"前往授权"，跳转系统 NotificationAccess 设置页
 */
@Composable
fun MusicPermissionDialog(
    onDismiss: () -> Unit,
    onGoGrant: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(
                    color = Color(0xF0131820),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // 图标
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF1DB954).copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "启用 Music Cassette",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "是否允许久以桌面读取当前媒体播放信息？",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 权限说明列表
                PermissionNoteRow("✅", "读取当前歌曲、歌手、专辑封面、播放状态")
                PermissionNoteRow("✅", "控制播放 / 暂停 / 上一首 / 下一首")
                PermissionNoteRow("🔒", "不读取歌单、不访问账号、不联网获取音乐")
                PermissionNoteRow("🔒", "不内置任何音乐资源，不破解版权接口")

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "久以桌面仅作为 Android 系统媒体控制组件，\n兼容网易云、QQ 音乐、Spotify 等所有播放器。",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 暂不授权
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Text("暂不授权", fontSize = 13.sp)
                    }

                    // 前往授权
                    Button(
                        onClick = {
                            onGoGrant()
                            // 跳转系统通知访问权限设置页
                            try {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 极少数 ROM 不支持直跳，降级到系统设置
                                try {
                                    val fallback = Intent(Settings.ACTION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(fallback)
                                } catch (ex: Exception) {}
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                    ) {
                        Text("前往授权", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionNoteRow(emoji: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = emoji, fontSize = 13.sp, modifier = Modifier.width(22.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}