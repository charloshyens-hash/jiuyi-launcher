package com.example.weather

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量级文件日志工具。
 * 日志写入：Android/data/com.example.jiuyilauncher/files/weather_debug.txt
 * 超过 512KB 自动清空，避免占用过多空间。
 * 使用方法：DebugLogger.log(context, "TAG", "消息内容")
 */
object DebugLogger {

    private const val LOG_FILE = "weather_debug.txt"
    private const val MAX_SIZE = 512 * 1024L  // 512 KB

    private val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    fun log(context: Context, tag: String, msg: String) {
        try {
            val file = File(context.getExternalFilesDir(null), LOG_FILE)
            // 超过上限则清空重来
            if (file.exists() && file.length() > MAX_SIZE) {
                file.writeText("")
            }
            val line = "[${sdf.format(Date())}] $tag: $msg\n"
            file.appendText(line)
            android.util.Log.d(tag, msg)  // 同时打到 Logcat
        } catch (e: Exception) {
            android.util.Log.e("DebugLogger", "写日志失败: ${e.message}")
        }
    }

    /** 清空日志文件 */
    fun clear(context: Context) {
        try {
            File(context.getExternalFilesDir(null), LOG_FILE).writeText("")
        } catch (e: Exception) {
            android.util.Log.e("DebugLogger", "清空日志失败: ${e.message}")
        }
    }

    /** 返回日志文件路径，方便用文件管理器找到 */
    fun getLogPath(context: Context): String {
        return File(context.getExternalFilesDir(null), LOG_FILE).absolutePath
    }
}
