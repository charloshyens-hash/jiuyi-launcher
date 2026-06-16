package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
//  音乐播放相关扩展方法（从 LauncherViewModel 拆分，功能与签名完全不变）
// ═══════════════════════════════════════════════════════════════════════════

fun LauncherViewModel.updateMusicWidgetMode(mode: Int) { prefs.musicWidgetMode = mode; musicWidgetMode.value = mode }

fun LauncherViewModel.updatePreferredMusicPackage(pkg: String) {
    prefs.preferredMusicPackage = pkg
    preferredMusicPackage.value = pkg
    currentTrackName = "久以金曲"
    currentTrackArtist = "打开任意音乐播放器即可显示"
    isMusicPlaying = false
    currentPosition = 0L
    currentDuration = 0L
    currentArtBase64 = ""
    if (JiuYiMediaService.isServiceRunning) {
        JiuYiMediaService.onPreferredPackageChanged()
    }
}

fun LauncherViewModel.launchPreferredMusicApp(context: Context) {
    try {
        val pkg = preferredMusicPackage.value
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val genIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genIntent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "无法启动首选音频App，请先在设置中绑定", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun LauncherViewModel.dispatchSystemMediaKey(keyCode: Int) {
    try {
        val context = getApplication<Application>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        if (audioManager != null) {
            val now = android.os.SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0))
        }
    } catch (e: Exception) {
        android.util.Log.e("LauncherVM", "Error dispatching media key: ${e.message}")
    }
}

fun LauncherViewModel.sendMediaKeyToPackage(pkg: String, keyCode: Int) {
    if (pkg.isEmpty()) return
    try {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setPackage(pkg)
        val now = android.os.SystemClock.uptimeMillis()
        val keyDown = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
        val keyUp   = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP,   keyCode, 0)
        val services = pm.queryIntentServices(mediaButtonIntent, 0)
        if (!services.isNullOrEmpty()) {
            for (resolved in services) {
                val svcIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    component = android.content.ComponentName(resolved.serviceInfo.packageName, resolved.serviceInfo.name)
                    putExtra(Intent.EXTRA_KEY_EVENT, keyDown)
                }
                val svcIntentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    component = android.content.ComponentName(resolved.serviceInfo.packageName, resolved.serviceInfo.name)
                    putExtra(Intent.EXTRA_KEY_EVENT, keyUp)
                }
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(svcIntent); context.startForegroundService(svcIntentUp)
                    } else {
                        context.startService(svcIntent); context.startService(svcIntentUp)
                    }
                } catch (e: Exception) {
                    try { context.startService(svcIntent); context.startService(svcIntentUp) } catch (ex: Exception) {}
                }
            }
        }
        val receivers = pm.queryBroadcastReceivers(mediaButtonIntent, 0)
        if (!receivers.isNullOrEmpty()) {
            for (resolved in receivers) {
                val componentName = android.content.ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name)
                val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply { component = componentName; putExtra(Intent.EXTRA_KEY_EVENT, keyDown) }
                val intentUp   = Intent(Intent.ACTION_MEDIA_BUTTON).apply { component = componentName; putExtra(Intent.EXTRA_KEY_EVENT, keyUp) }
                context.sendBroadcast(intentDown); context.sendBroadcast(intentUp)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("LauncherVM", "sendMediaKeyToPackage failed: ${e.message}")
    }
}

fun LauncherViewModel.findAnyInstalledMusicPackage(): String {
    val context = getApplication<Application>()
    val pm = context.packageManager
    try {
        val musicIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MUSIC) }
        val list = pm.queryIntentActivities(musicIntent, 0)
        if (!list.isNullOrEmpty()) { val pkg = list[0].activityInfo.packageName; if (pkg.isNotEmpty()) return pkg }
    } catch (e: Exception) {}
    try {
        val commonMusicPkgs = listOf(
            "com.tencent.qqmusic", "com.netease.cloudmusic", "com.kugou.android", "cn.kuwo.player",
            "com.android.music", "com.miui.player", "com.heytap.music", "com.android.mediacenter",
            "com.vivo.music", "com.cootek.smartdialer", "com.apple.android.music",
            "com.google.android.apps.youtube.music"
        )
        for (p in commonMusicPkgs) { try { pm.getPackageInfo(p, 0); return p } catch (e: Exception) {} }
        val packages = pm.getInstalledPackages(0)
        for (info in packages) {
            val name = info.packageName.lowercase()
            if (name.contains("music") || name.contains("player") || name.contains("audio")) return info.packageName
        }
    } catch (e: Exception) {}
    return ""
}

fun LauncherViewModel.wakeMusicAppBackground(pkg: String) {
    if (pkg.isEmpty()) return
    val context = getApplication<Application>()
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        if (audioManager != null) {
            val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN).apply {
                setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setWillPauseWhenDucked(false); setAcceptsDelayedFocusGain(true); setOnAudioFocusChangeListener({})
            }.build()
            audioManager.requestAudioFocus(focusRequest)
        }
    } catch (e: Exception) {}
    try {
        val pm = context.packageManager
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply { setPackage(pkg) }
        val now = android.os.SystemClock.uptimeMillis()
        val keyDown = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0)
        val keyUp   = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP,   android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0)
        try {
            val services = pm.queryIntentServices(mediaButtonIntent, 0)
            if (!services.isNullOrEmpty()) {
                for (resolved in services) {
                    val svcIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        component = android.content.ComponentName(resolved.serviceInfo.packageName, resolved.serviceInfo.name)
                        putExtra(Intent.EXTRA_KEY_EVENT, keyDown)
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(svcIntent)
                        else context.startService(svcIntent)
                    } catch (e: Exception) { try { context.startService(svcIntent) } catch (ex: Exception) {} }
                }
            }
        } catch (e: Exception) {}
        val receivers = pm.queryBroadcastReceivers(mediaButtonIntent, 0)
        if (!receivers.isNullOrEmpty()) {
            val componentName = android.content.ComponentName(receivers[0].activityInfo.packageName, receivers[0].activityInfo.name)
            val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply { component = componentName; putExtra(Intent.EXTRA_KEY_EVENT, keyDown) }
            val intentUp   = Intent(Intent.ACTION_MEDIA_BUTTON).apply { component = componentName; putExtra(Intent.EXTRA_KEY_EVENT, keyUp) }
            context.sendBroadcast(intentDown); context.sendBroadcast(intentUp)
        } else {
            val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply { setPackage(pkg); putExtra(Intent.EXTRA_KEY_EVENT, keyDown) }
            val intentUp   = Intent(Intent.ACTION_MEDIA_BUTTON).apply { setPackage(pkg); putExtra(Intent.EXTRA_KEY_EVENT, keyUp) }
            context.sendBroadcast(intentDown); context.sendBroadcast(intentUp)
        }
    } catch (e: Exception) {
        android.util.Log.e("LauncherVM", "wakeMusicAppBackground failed: ${e.message}")
    }
}

fun LauncherViewModel.connectAndPlayViaMediaBrowser(pkg: String) {
    if (pkg.isEmpty()) return
    val context = getApplication<Application>()
    val pm = context.packageManager
    val browserIntent = Intent("android.media.browse.MediaBrowserService")
    val services = pm.queryIntentServices(browserIntent, 0)
    val targetService = services.firstOrNull { it.serviceInfo.packageName == pkg }
    if (targetService != null) {
        val componentName = android.content.ComponentName(targetService.serviceInfo.packageName, targetService.serviceInfo.name)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            try {
                var mediaBrowser: android.media.browse.MediaBrowser? = null
                val connectionCallback = object : android.media.browse.MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        try {
                            val token = mediaBrowser?.sessionToken
                            if (token != null) {
                                val controller = android.media.session.MediaController(context, token)
                                controller.transportControls.play()
                                val now = android.os.SystemClock.uptimeMillis()
                                controller.dispatchMediaButtonEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0))
                                controller.dispatchMediaButtonEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP,   android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LauncherVM", "MediaBrowser session play failed: ${e.message}")
                        } finally { try { mediaBrowser?.disconnect() } catch (e: Exception) {} }
                    }
                    override fun onConnectionFailed()    { try { mediaBrowser?.disconnect() } catch (e: Exception) {} }
                    override fun onConnectionSuspended() { try { mediaBrowser?.disconnect() } catch (e: Exception) {} }
                }
                mediaBrowser = android.media.browse.MediaBrowser(context, componentName, connectionCallback, null)
                mediaBrowser.connect()
            } catch (e: Exception) {
                android.util.Log.e("LauncherVM", "MediaBrowser setup failed: ${e.message}")
            }
        }
    }
}

fun LauncherViewModel.toggleMusicPlayback() {
    val targetPkg = preferredMusicPackage.value
    if (targetPkg.isNotEmpty()) {
        if (JiuYiMediaService.isServiceRunning && JiuYiMediaService.matchesPkg(targetPkg)) {
            JiuYiMediaService.sendMediaAction("play_pause")
        } else {
            connectAndPlayViaMediaBrowser(targetPkg)
            wakeMusicAppBackground(targetPkg)
            viewModelScope.launch {
                delay(1200)
                if (JiuYiMediaService.isServiceRunning && JiuYiMediaService.matchesPkg(targetPkg)) {
                    JiuYiMediaService.sendMediaAction("play_pause")
                } else {
                    val keyCode = if (isMusicPlaying) android.view.KeyEvent.KEYCODE_MEDIA_PAUSE else android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                    sendMediaKeyToPackage(targetPkg, keyCode)
                    dispatchSystemMediaKey(keyCode)
                }
            }
        }
        return
    }
    if (JiuYiMediaService.isServiceRunning) { JiuYiMediaService.sendMediaAction("play_pause"); return }
    val fallbackPkg = findAnyInstalledMusicPackage()
    if (fallbackPkg.isNotEmpty()) {
        connectAndPlayViaMediaBrowser(fallbackPkg)
        wakeMusicAppBackground(fallbackPkg)
        viewModelScope.launch {
            delay(1200)
            if (JiuYiMediaService.isServiceRunning) {
                JiuYiMediaService.sendMediaAction("play_pause")
            } else {
                dispatchSystemMediaKey(if (isMusicPlaying) android.view.KeyEvent.KEYCODE_MEDIA_PAUSE else android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            }
        }
    } else {
        dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }
}

fun LauncherViewModel.nextTrack() {
    val targetPkg = preferredMusicPackage.value
    if (targetPkg.isNotEmpty()) {
        if (JiuYiMediaService.isServiceRunning && JiuYiMediaService.matchesPkg(targetPkg)) JiuYiMediaService.sendMediaAction("next")
        else sendMediaKeyToPackage(targetPkg, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
        return
    }
    if (JiuYiMediaService.isServiceRunning) { JiuYiMediaService.sendMediaAction("next"); return }
    dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
}

fun LauncherViewModel.prevTrack() {
    val targetPkg = preferredMusicPackage.value
    if (targetPkg.isNotEmpty()) {
        if (JiuYiMediaService.isServiceRunning && JiuYiMediaService.matchesPkg(targetPkg)) JiuYiMediaService.sendMediaAction("prev")
        else sendMediaKeyToPackage(targetPkg, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return
    }
    if (JiuYiMediaService.isServiceRunning) { JiuYiMediaService.sendMediaAction("prev"); return }
    dispatchSystemMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
}
