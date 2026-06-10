package com.example

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import com.example.weather.WeatherNotificationParser
import java.io.ByteArrayOutputStream

class JiuYiMediaService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private var controllerListener: MediaController.Callback? = null
    private var activeController: MediaController? = null

    companion object {
        const val ACTION_MEDIA_UPDATE = "com.example.LAUNCHER_MEDIA_UPDATE"
        var isServiceRunning = false

        private var instance: JiuYiMediaService? = null

        fun sendMediaAction(action: String) {
            instance?.performMediaAction(action)
        }

        fun getActiveSessionPkg(): String {
            return instance?.activeController?.packageName ?: ""
        }

        /** 返回当前活跃播放器的 sessionActivity（用于唤醒到前台不切歌） */
        fun getSessionActivity(): PendingIntent? {
            return instance?.activeController?.sessionActivity
        }

        fun requestRefresh() {
            instance?.sendUpdate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceRunning = true
        Log.d("JiuYiMedia", "Service onCreate executed")
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

        controllerListener = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { sendUpdate() }
            override fun onPlaybackStateChanged(state: PlaybackState?) { sendUpdate() }
            override fun onSessionDestroyed() {
                activeController = null
                updateActiveController()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val sbnNotNull = sbn ?: return
        val notification = sbnNotNull.notification ?: return
        val extras = notification.extras ?: return

        // 1. 从通知中直接拦截 MediaSession.Token（最稳健方式）
        if (extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) {
            val token = extras.get(android.app.Notification.EXTRA_MEDIA_SESSION)
                    as? android.media.session.MediaSession.Token
            if (token != null) {
                try {
                    val controller = MediaController(this, token)
                    bindNewController(controller)
                } catch (e: Exception) {
                    Log.e("JiuYiMedia", "Error binding controller from notification: ${e.message}")
                }
            }
        }

        // 2. 兜底：解析音乐通知文本
        tryParseMusicNotification(sbnNotNull)

        // 3. 天气通知解析 → 委托给 WeatherNotificationParser（职责分离）
        WeatherNotificationParser.tryParse(this, sbnNotNull)
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        try { updateActiveController() } catch (e: Exception) {}
    }

    private fun tryParseMusicNotification(sbn: android.service.notification.StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: ""
            val extras = sbn.notification?.extras ?: return

            val isMusicApp = pkg.contains("music", ignoreCase = true) ||
                    pkg.contains("player", ignoreCase = true) ||
                    pkg.contains("kugou", ignoreCase = true) ||
                    pkg.contains("kuwo", ignoreCase = true) ||
                    pkg.contains("netease", ignoreCase = true) ||
                    pkg.contains("qqmusic", ignoreCase = true)

            if (isMusicApp) {
                val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
                var artist = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

                if (title.isNotEmpty() && (activeController == null || activeController?.packageName == pkg)) {
                    if (artist.contains(" - ") || artist.contains(" -- ")) {
                        val pts = artist.split(" - ", " -- ")
                        if (pts.isNotEmpty()) artist = pts[0]
                    }
                    val intent = Intent(ACTION_MEDIA_UPDATE).apply {
                        setPackage(packageName)
                        putExtra("title", title)
                        putExtra("artist", artist)
                        putExtra("is_playing", true)
                        putExtra("packageName", pkg)
                        putExtra("position", 0L)
                        putExtra("duration", 0L)
                    }
                    sendBroadcast(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error in tryParseMusicNotification: ${e.message}")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            updateActiveController()
            sessionManager?.addOnActiveSessionsChangedListener(
                { _ -> updateActiveController() },
                ComponentName(this, JiuYiMediaService::class.java)
            )
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error in onListenerConnected: ${e.message}")
        }
        try {
            activeNotifications?.forEach { WeatherNotificationParser.tryParse(this, it) }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error scanning notifications at start: ${e.message}")
        }
    }

    private fun bindNewController(controller: MediaController) {
        if (activeController?.packageName == controller.packageName) {
            try { activeController?.unregisterCallback(controllerListener!!) } catch (e: Exception) {}
            activeController = controller
            try { activeController?.registerCallback(controllerListener!!) } catch (e: Exception) {}
            sendUpdate()
            return
        }
        try { activeController?.unregisterCallback(controllerListener!!) } catch (e: Exception) {}
        activeController = controller
        try { activeController?.registerCallback(controllerListener!!) } catch (e: Exception) {}
        sendUpdate()
    }

    private fun updateActiveController() {
        try {
            val component = ComponentName(this, JiuYiMediaService::class.java)
            val controllers = sessionManager?.getActiveSessions(component)
            if (!controllers.isNullOrEmpty()) {
                val playingController = controllers.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                }
                bindNewController(playingController ?: controllers.first())
            } else {
                sendUpdate()
            }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error in updateActiveController: ${e.message}")
        }
    }

    // ── 媒体键分发（通用，适配所有播放器） ────────────────────────────────────
    private fun dispatchKey(keyCode: Int) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val now = SystemClock.uptimeMillis()
            audioManager?.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "dispatchKey($keyCode) failed: ${e.message}")
        }
    }

    /**
     * 执行媒体控制指令
     *
     * 双轨策略，兼容所有播放器：
     *   轨道1 — TransportControls（MediaSession API）：精准控制已注册 Session 的播放器
     *   轨道2 — AudioManager.dispatchMediaKeyEvent（系统媒体键）：兜底路由
     *
     * 为何需要双轨：
     *   网易云、QQ音乐等在暂停/停止状态下，transportControls.play() 可能被忽略，
     *   但系统媒体键走 AudioManager 路由，绕过 Session API，几乎对所有播放器有效。
     *   两者同时发出，哪条先到哪条生效，不会重复触发（播放器内部有防抖）。
     */
    fun performMediaAction(action: String) {
        if (activeController == null) updateActiveController()
        val controller = activeController

        try {
            when (action) {
                "play_pause" -> {
                    val state = controller?.playbackState?.state
                    if (state == PlaybackState.STATE_PLAYING) {
                        // 正在播放 → 暂停：两轨同时发
                        controller?.transportControls?.pause()
                        dispatchKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                    } else {
                        // 暂停 / 停止 / 未知 → 播放
                        // 先发系统键（更快绕过 Session 限制），再发 TransportControls
                        dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                        controller?.transportControls?.play()
                    }
                }
                "next" -> {
                    controller?.transportControls?.skipToNext()
                    dispatchKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                }
                "prev" -> {
                    controller?.transportControls?.skipToPrevious()
                    dispatchKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error performing action $action: ${e.message}")
        }
    }

    // ── 核心广播：封面 Base64 + 播放进度 ──────────────────────────────────────
    private fun sendUpdate() {
        val controller = activeController
        val metadata = controller?.metadata
        val state = controller?.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val position = state?.position ?: 0L
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val artBitmap: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val artBase64: String = if (artBitmap != null) {
            try {
                val stream = ByteArrayOutputStream()
                val scaled = Bitmap.createScaledBitmap(artBitmap, 128, 128, true)
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) { "" }
        } else ""

        val intent = Intent(ACTION_MEDIA_UPDATE).apply {
            setPackage(packageName)
            putExtra("title", title)
            putExtra("artist", artist)
            putExtra("is_playing", isPlaying)
            putExtra("packageName", controller?.packageName ?: "")
            putExtra("position", position)
            putExtra("duration", duration)
            putExtra("art_base64", artBase64)
        }
        sendBroadcast(intent)
    }
}