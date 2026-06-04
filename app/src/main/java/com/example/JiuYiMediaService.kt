package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

class JiuYiMediaService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private var controllerListener: MediaController.Callback? = null
    private var activeController: MediaController? = null

    companion object {
        const val ACTION_MEDIA_UPDATE = "com.example.LAUNCHER_MEDIA_UPDATE"
        var isServiceRunning = false
        
        // Static references for direct control from view model
        private var instance: JiuYiMediaService? = null
        
        fun sendMediaAction(action: String) {
            instance?.performMediaAction(action)
        }
        
        fun getActiveSessionPkg(): String {
            return instance?.activeController?.packageName ?: ""
        }

        fun requestRefresh() {
            instance?.sendUpdate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceRunning = true
        android.util.Log.d("JiuYiMedia", "Service onCreate executed")
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        
        controllerListener = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                sendUpdate()
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                sendUpdate()
            }

            override fun onSessionDestroyed() {
                activeController = null
                updateActiveController()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (instance == this) {
            instance = null
        }
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val sbnNotNull = sbn ?: return
        val notification = sbnNotNull.notification ?: return
        val extras = notification.extras ?: return
        
        // 1. Intercept MediaSession.Token directly from active notifications (extremely robust)
        if (extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) {
            val token = extras.get(android.app.Notification.EXTRA_MEDIA_SESSION) as? android.media.session.MediaSession.Token
            if (token != null) {
                try {
                    val controller = MediaController(this, token)
                    bindNewController(controller)
                } catch (e: Exception) {
                    Log.e("JiuYiMedia", "Error binding controller from notification: ${e.message}")
                }
            }
        }

        // 2. Failsafe Music Notification Text Parser
        tryParseMusicNotification(sbnNotNull)

        // 3. Automated Weather App Notification Synchronizer (MIUI, Huawei, vivo, OPPO, Moji, etc.)
        tryParseWeatherNotification(sbnNotNull)
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        try {
            updateActiveController()
        } catch (e: Exception) {}
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
                    // Check if artist is empty or has long duration/lyrics info; sanitize
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
                    }
                    sendBroadcast(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error in tryParseMusicNotification: ${e.message}")
        }
    }

    private fun tryParseWeatherNotification(sbn: android.service.notification.StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: ""
            val extras = sbn.notification?.extras ?: return
            
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            
            val isWeatherApp = pkg.contains("weather", ignoreCase = true) ||
                               pkg.contains("tianqi", ignoreCase = true) ||
                               pkg.contains("totemweather", ignoreCase = true)
                               
            val combinedText = "$title $text $subText"
            val hasTempSymbol = combinedText.contains("°") || combinedText.contains("℃") || combinedText.contains("°C")
            
            if (isWeatherApp || (hasTempSymbol && (
                    combinedText.contains("晴") || combinedText.contains("多云") || 
                    combinedText.contains("阴") || combinedText.contains("雨") || 
                    combinedText.contains("雪") || combinedText.contains("霾") || 
                    combinedText.contains("雾") || combinedText.contains("风")
                ))) {
                
                // 1. Extra temperature
                val tempRegex = """(-?\d+)\s*(°C|°|℃)""".toRegex()
                val tempMatch = tempRegex.find(combinedText)
                var extractedTemp = ""
                if (tempMatch != null) {
                    val value = tempMatch.groupValues[1]
                    extractedTemp = "${value}°"
                } else {
                    // fallback if no direct match, check if there's any single number ending with °
                    val fallbackRegex = """(-?\d+)°""".toRegex()
                    val fallbackMatch = fallbackRegex.find(combinedText)
                    if (fallbackMatch != null) {
                        extractedTemp = "${fallbackMatch.groupValues[1]}°"
                    }
                }
                
                // 2. Extra weather condition state
                val weatherStates = listOf(
                    "晴间多云", "多云转晴", "多云", "阴天", "雷阵雨", "雨夹雪", "沙尘暴", 
                    "大暴雨", "特大暴雨", "大雨", "中雨", "小雨", "阵雨", "暴雪", "大雪", 
                    "中雪", "小雪", "阵雪", "小到中雨", "中到大雨", "暴雨", "晴", "阴", 
                    "雨", "雪", "霾", "雾", "风"
                )
                var extractedCond = ""
                for (state in weatherStates) {
                    if (text.contains(state) || title.contains(state) || subText.contains(state)) {
                        extractedCond = state
                        break
                    }
                }
                
                // 3. Extract city
                var extractedCity = ""
                val cityRegex = """([\u4e00-\u9fa5]{2,6})(市|区|县)""".toRegex()
                val cityMatch = cityRegex.find(combinedText)
                if (cityMatch != null) {
                    extractedCity = cityMatch.groupValues[1]
                } else {
                    val cleanTitle = title.trim()
                    if (cleanTitle.length in 2..5 && cleanTitle.all { it in '\u4e00'..'\u9fa5' } && 
                        !cleanTitle.contains("天气") && !cleanTitle.contains("温度") && !cleanTitle.contains("预警")) {
                        extractedCity = cleanTitle
                    }
                }
                
                if (extractedTemp.isNotEmpty() || extractedCond.isNotEmpty()) {
                    if (extractedCity.isEmpty()) {
                        extractedCity = "本地"
                    }
                    
                    val intent = Intent("com.example.LAUNCHER_WEATHER_UPDATE").apply {
                        setPackage(packageName)
                        putExtra("city", extractedCity)
                        putExtra("weather", extractedCond)
                        putExtra("temp", extractedTemp)
                    }
                    sendBroadcast(intent)
                    Log.d("JiuYiWeather", "Captured system weather from notification: $extractedCity, $extractedCond, $extractedTemp (from $pkg)")
                }
            }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error in tryParseWeatherNotification: ${e.message}")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            updateActiveController()
            sessionManager?.addOnActiveSessionsChangedListener(
                { _ ->
                    updateActiveController()
                },
                ComponentName(this, JiuYiMediaService::class.java)
            )
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error in onListenerConnected: ${e.message}")
        }
        try {
            val activeNotifications = activeNotifications
            if (activeNotifications != null) {
                for (sbn in activeNotifications) {
                    tryParseWeatherNotification(sbn)
                }
            }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error scanning notifications at start: ${e.message}")
        }
    }

    private fun bindNewController(controller: MediaController) {
        if (activeController?.packageName == controller.packageName) {
            // Already bound, but let's re-register callback safely to be sure
            try {
                activeController?.unregisterCallback(controllerListener!!)
            } catch (e: Exception) {}
            activeController = controller
            try {
                activeController?.registerCallback(controllerListener!!)
            } catch (e: Exception) {}
            sendUpdate()
            return
        }

        try {
            activeController?.unregisterCallback(controllerListener!!)
        } catch (e: Exception) {}

        activeController = controller
        try {
            activeController?.registerCallback(controllerListener!!)
        } catch (e: Exception) {}
        sendUpdate()
    }

    private fun updateActiveController() {
        try {
            val component = ComponentName(this, JiuYiMediaService::class.java)
            val controllers = sessionManager?.getActiveSessions(component)
            if (!controllers.isNullOrEmpty()) {
                // Prioritize any controller that is active and currently playing
                val playingController = controllers.firstOrNull { 
                    it.playbackState?.state == PlaybackState.STATE_PLAYING 
                }
                val target = playingController ?: controllers.first()
                bindNewController(target)
            } else {
                sendUpdate()
            }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error in updateActiveController: ${e.message}")
        }
    }

    fun performMediaAction(action: String) {
        if (activeController == null) {
            updateActiveController()
        }
        val controller = activeController ?: return
        try {
            when (action) {
                "play_pause" -> {
                    val state = controller.playbackState?.state
                    if (state == PlaybackState.STATE_PLAYING) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                }
                "next" -> controller.transportControls.skipToNext()
                "prev" -> controller.transportControls.skipToPrevious()
            }
        } catch (e: Exception) {
            Log.e("JiuYiMedia", "Error performing action $action: ${e.message}")
        }
    }

    private fun sendUpdate() {
        val controller = activeController
        val metadata = controller?.metadata
        val state = controller?.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        val intent = Intent(ACTION_MEDIA_UPDATE).apply {
            setPackage(packageName) // Guarantee delivery targeting only our package ID
            putExtra("title", title)
            putExtra("artist", artist)
            putExtra("is_playing", isPlaying)
            putExtra("packageName", controller?.packageName ?: "")
        }
        sendBroadcast(intent)
    }
}
