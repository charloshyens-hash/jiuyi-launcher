package com.example

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.Toast

data class AppModel(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable? = null,
    val isSystem: Boolean = false
) {
    fun launch(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                // Add flags to prevent crashing the launcher process and run smoothly
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "无法启动：${label}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "启动出错：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
