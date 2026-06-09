package com.example.weather

import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 被动天气通知解析器。
 *
 * 职责：从系统/第三方天气 App 推送的通知中，提取城市、天气状况、温度，
 * 并广播 "com.example.LAUNCHER_WEATHER_UPDATE" 供 ViewModel 更新 UI。
 *
 * 本类由 JiuYiMediaService（NotificationListenerService）调用，
 * 自身不持有任何 Service 生命周期，纯粹的无状态工具类。
 *
 * 数据流：
 *   第三方天气 App 推送通知
 *     → JiuYiMediaService.onNotificationPosted()
 *     → WeatherNotificationParser.tryParse()
 *     → sendBroadcast("com.example.LAUNCHER_WEATHER_UPDATE")
 *     → LauncherViewModel.weatherUpdateReceiver
 *     → WeatherUiState 更新
 */
object WeatherNotificationParser {

    private const val TAG = "WeatherNotifParser"
    const val ACTION_WEATHER_UPDATE = "com.example.LAUNCHER_WEATHER_UPDATE"

    // 已知天气 App 包名关键词
    private val WEATHER_PKG_KEYWORDS = listOf(
        "weather", "tianqi", "totemweather",
        "miui.weather", "huawei", "coloros.weather",
        "oppo.weather", "vivo.weather"
    )

    // 天气状态词，从长到短排列（优先匹配更精确的词）
    private val WEATHER_STATES = listOf(
        "晴间多云", "多云转晴", "雷阵雨", "雨夹雪", "沙尘暴",
        "大暴雨", "特大暴雨", "小到中雨", "中到大雨",
        "大雨", "中雨", "小雨", "阵雨", "暴雪", "大雪",
        "中雪", "小雪", "阵雪", "暴雨", "多云", "阴天",
        "晴", "阴", "雨", "雪", "霾", "雾", "风"
    )

    /**
     * 尝试从通知中解析天气信息并广播。
     * @param context 用于发送广播，建议传入 Service 的 applicationContext
     * @param sbn     来自 onNotificationPosted 的 StatusBarNotification
     */
    fun tryParse(context: Context, sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName ?: return
            val extras = sbn.notification?.extras ?: return

            val title   = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text    = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            val combined = "$title $text $subText"

            // 判断是否为天气通知：包名命中 或 内容同时含有温度符号+天气状态词
            val isWeatherPkg = WEATHER_PKG_KEYWORDS.any { pkg.contains(it, ignoreCase = true) }
            val hasTempSymbol = combined.contains("°") || combined.contains("℃") || combined.contains("°C")
            val hasWeatherWord = WEATHER_STATES.any { combined.contains(it) }

            if (!isWeatherPkg && !(hasTempSymbol && hasWeatherWord)) return

            // 提取温度
            val extractedTemp = extractTemperature(combined)

            // 提取天气状况
            val extractedCond = WEATHER_STATES.firstOrNull { combined.contains(it) } ?: ""

            // 提取城市
            val extractedCity = extractCity(combined, title)

            if (extractedTemp.isEmpty() && extractedCond.isEmpty()) return

            Log.d(TAG, "Parsed weather from $pkg: city=$extractedCity cond=$extractedCond temp=$extractedTemp")

            val intent = Intent(ACTION_WEATHER_UPDATE).apply {
                setPackage(context.packageName)
                putExtra("city", extractedCity.ifEmpty { "本地" })
                putExtra("weather", extractedCond)
                putExtra("temp", extractedTemp)
            }
            context.sendBroadcast(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing weather notification: ${e.message}")
        }
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private fun extractTemperature(text: String): String {
        val regex = Regex("""(-?\d+)\s*(°C|°|℃)""")
        val match = regex.find(text)
        if (match != null) return "${match.groupValues[1]}°"
        // 兜底：只有数字+°的情况
        val fallback = Regex("""(-?\d+)°""").find(text)
        if (fallback != null) return "${fallback.groupValues[1]}°"
        return ""
    }

    private fun extractCity(combined: String, title: String): String {
        // 优先匹配"XX市/区/县"
        val cityRegex = Regex("""([\u4e00-\u9fa5]{2,6})(市|区|县)""")
        val match = cityRegex.find(combined)
        if (match != null) return match.groupValues[1]

        // 其次：标题是 2~5 个纯汉字且不含干扰词时认为是城市名
        val cleanTitle = title.trim()
        val noiseWords = listOf("天气", "温度", "预警", "通知", "更新", "实况", "预报")
        if (cleanTitle.length in 2..5 &&
            cleanTitle.all { it in '\u4e00'..'\u9fa5' } &&
            noiseWords.none { cleanTitle.contains(it) }
        ) {
            return cleanTitle
        }
        return ""
    }
}