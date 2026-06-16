package com.example

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private var pendingUninstallPackage: String? = null

    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val pkg = pendingUninstallPackage ?: return@registerForActivityResult
        pendingUninstallPackage = null

        val success = try {
            packageManager.getPackageInfo(pkg, 0)
            false
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            true
        }

        viewModel.onUninstallResult(pkg, success)

        if (success) {
            Toast.makeText(this, "卸载成功", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            com.example.weather.WeatherSyncScheduler.scheduleWeatherSync(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule weather sync: ${e.message}")
        }

        lifecycleScope.launch {
            viewModel.uninstallRequestFlow
                .collect { app ->
                    pendingUninstallPackage = app.packageName
                    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                        data = android.net.Uri.parse("package:${app.packageName}")
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        uninstallLauncher.launch(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to launch uninstall: ${e.message}")
                        Toast.makeText(this@MainActivity, "无法启动系统卸载界面", Toast.LENGTH_SHORT).show()
                        pendingUninstallPackage = null
                        viewModel.refreshInstalledApps()
                    }
                }
        }

        enableEdgeToEdge()

        setContent {
            val themeIndex by viewModel.currentThemeIndex.collectAsState()
            val themeColors = listOf(
                Color(0xFFFA5F3D),
                Color(0xFF00D1FF),
                Color(0xFF6366F1),
                Color(0xFF10B981),
                Color(0xFFEC4899),
                Color(0xFFF59E0B)
            )
            val activeThemeColor = themeColors.getOrElse(themeIndex) { Color(0xFFFA5F3D) }

            MyApplicationTheme(primaryColor = activeThemeColor) {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    LauncherHomeScreen(
                        viewModel = viewModel,
                        themeColor = activeThemeColor
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            viewModel.fetchRealWeather()
        } catch (e: Exception) {}

        try {
            if (isNotificationServiceEnabled(this)) {
                toggleNotificationListenerService(this)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    JiuYiMediaService.requestRefresh()
                }, 200)
            }
        } catch (e: Exception) {}
    }

    private fun isNotificationServiceEnabled(context: android.content.Context): Boolean {
        val pkgName = context.packageName
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = android.content.ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) return true
            }
        }
        return false
    }

    private fun toggleNotificationListenerService(context: android.content.Context) {
        try {
            val pm = context.packageManager
            val componentName = android.content.ComponentName(context, JiuYiMediaService::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to force revive media service: ${e.message}")
        }
    }
}