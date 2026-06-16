package com.example

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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
                    modifier = Modifier.fillMaxSize(),
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
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherHomeScreen(
    viewModel: LauncherViewModel,
    themeColor: Color
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    var isAppDrawerOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isGesturePanelOpen by remember { mutableStateOf(false) }
    var isAddScreenOpen by remember { mutableStateOf(false) }
    var isEffectSystemOpen by remember { mutableStateOf(false) }

    val globalParticles = remember { mutableStateListOf<TouchParticle>() }
    val selectedTouchEffect by viewModel.touchEffect.collectAsState()
    val touchPool by viewModel.touchRandomPool.collectAsState()

    val selectedCrossTransition by viewModel.crossTransition.collectAsState()
    val crossPool by viewModel.crossRandomPool.collectAsState()

    val crossProgress by animateFloatAsState(
        targetValue = if (isAppDrawerOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "crossProgress"
    )

    val activeCrossType = remember(selectedCrossTransition, crossPool, isAppDrawerOpen) {
        if (selectedCrossTransition == "随机") {
            val pool = crossPool.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (pool.isNotEmpty()) {
                val seed = if (isAppDrawerOpen) 1 else 0
                pool[seed % pool.size]
            } else "默认"
        } else {
            selectedCrossTransition
        }
    }

    val effectSystemOffset by animateDpAsState(
        targetValue = if (isEffectSystemOpen) 0.dp else 1200.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "effectSystem"
    )

    LaunchedEffect(globalParticles.size) {
        if (globalParticles.isNotEmpty()) {
            var lastTime = System.nanoTime()
            while (globalParticles.isNotEmpty()) {
                withFrameNanos { frameTime ->
                    val dt = ((frameTime - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastTime = frameTime
                    val iterator = globalParticles.listIterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        val nextLife = p.life - dt / p.maxLife
                        if (nextLife <= 0f) {
                            iterator.remove()
                        } else {
                            val nextVy = if (p.type == "balloon" || p.type == "butterfly") {
                                p.vy - 0.4f * dt
                            } else if (p.type == "money" || p.type == "confetti") {
                                p.vy + 0.15f * dt
                            } else {
                                p.vy + 3.5f * dt
                            }
                            
                            val drift = if (p.type == "balloon" || p.type == "butterfly" || p.type == "money" || p.type == "confetti") {
                                Math.sin(nextLife * 12.0 + p.id).toFloat() * 1.5f
                            } else 0f
                            
                            iterator.set(
                                p.copy(
                                    x = p.x + (p.vx + drift) * 100f * dt,
                                    y = p.y + nextVy * 100f * dt,
                                    vy = nextVy,
                                    life = nextLife,
                                    alpha = nextLife,
                                    extra = p.extra + p.vx * dt * 4f
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    val wallpaperName by viewModel.wallpaperName.collectAsState()
    val clockStyle by viewModel.clockStyle.collectAsState()
    val dockPackages by viewModel.dockPackages.collectAsState()
    val appList by viewModel.appList.collectAsState()
    val showLabels by viewModel.showLabels.collectAsState()
    val iconPackFilter by viewModel.iconPackFilter.collectAsState()

    val homePages by viewModel.homePages.collectAsState()
    val activePageIndex by viewModel.activePageIndex.collectAsState()

    var dragSourcePageIndex by remember { mutableStateOf(0) }

    val appDrawerOffset by animateDpAsState(
        targetValue = if (isAppDrawerOpen) 0.dp else 1200.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "appDrawer"
    )

    val settingsOffset by animateDpAsState(
        targetValue = if (isSettingsOpen) 0.dp else 1200.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "settingsOffset"
    )

    val showToast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp
    val screenHeight = config.screenHeightDp

    val handleGlobalDrop: () -> Unit = {
        val app = viewModel.draggedApp
        if (app != null) {
            val dropX = viewModel.dragOffset.x
            val dropY = viewModel.dragOffset.y

            if (isAddScreenOpen) {
                var droppedOnThumbnailIndex: Int? = null
                for ((index, rect) in viewModel.addScreenThumbnailBounds) {
                    if (dropX >= rect.left && dropX <= rect.right &&
                        dropY >= rect.top && dropY <= rect.bottom
                    ) {
                        droppedOnThumbnailIndex = index
                        break
                    }
                }

                if (droppedOnThumbnailIndex != null) {
                    if (app.packageName.startsWith("WIDGET:")) {
                        val widgetName = app.packageName.substring(7)
                        viewModel.addWidgetToPage(droppedOnThumbnailIndex, widgetName)
                        showToast("已成功添加 $widgetName 到第 ${droppedOnThumbnailIndex + 1} 页")
                    } else {
                        viewModel.addAppToPage(droppedOnThumbnailIndex, app.packageName)
                        showToast("已成功添加快捷方式 ${app.label} 到第 ${droppedOnThumbnailIndex + 1} 页")
                    }
                }
                viewModel.draggedApp = null
                viewModel.isDraggingActive = false
            } else if (viewModel.isDraggingFromDrawer) {
                var droppedOnPage: Int? = null
                for ((idx, rect) in viewModel.drawerThumbnailBounds) {
                    if (dropX >= rect.left && dropX <= rect.right &&
                        dropY >= rect.top && dropY <= rect.bottom
                    ) {
                        droppedOnPage = idx
                        break
                    }
                }
                if (droppedOnPage != null) {
                    if (app.packageName.startsWith("WIDGET:")) {
                        val widgetName = app.packageName.substring(7)
                        viewModel.addWidgetToPage(droppedOnPage, widgetName)
                        showToast("已成功将 $widgetName 添加到第 ${droppedOnPage + 1} 页主屏")
                    } else {
                        viewModel.addAppToPage(droppedOnPage, app.packageName)
                        showToast("已成功将 ${app.label} 添加到第 ${droppedOnPage + 1} 页主屏")
                    }
                } else {
                    var droppedOnDrawerIndex: Int? = null
                    for ((globalIdx, rect) in viewModel.drawerItemBounds) {
                        if (dropX >= rect.left && dropX <= rect.right &&
                            dropY >= rect.top && dropY <= rect.bottom
                        ) {
                            droppedOnDrawerIndex = globalIdx
                            break
                        }
                    }
                    if (droppedOnDrawerIndex == null) {
                        var minDistance = Float.MAX_VALUE
                        var closestIdx: Int? = null
                        for ((globalIdx, rect) in viewModel.drawerItemBounds) {
                            val centerX = (rect.left + rect.right) / 2f
                            val centerY = (rect.top + rect.bottom) / 2f
                            val dist = (dropX - centerX) * (dropX - centerX) + (dropY - centerY) * (dropY - centerY)
                            if (dist < minDistance) {
                                minDistance = dist
                                closestIdx = globalIdx
                            }
                        }
                        if (minDistance < 20000f) {
                            droppedOnDrawerIndex = closestIdx
                        }
                    }

                    if (droppedOnDrawerIndex != null) {
                        viewModel.reorderDrawerApp(app.packageName, droppedOnDrawerIndex)
                        showToast("已调整应用抽屉图标排序")
                    } else {
                        showToast("请将图标/组件挪动至主屏幕缩略图上以完成添加")
                    }
                }
            } else {
                val isOverTopBar = dropY <= 100f && dropY > 0f
                if (isOverTopBar) {
                    if (dropX < screenWidth / 2f) {
                        if (viewModel.isDraggingFromDock) {
                            val currentDockList = dockPackages.toMutableList()
                            if (viewModel.dragSourceIndex in currentDockList.indices) {
                                currentDockList.removeAt(viewModel.dragSourceIndex)
                            }
                            viewModel.updateDockConfiguration(currentDockList)
                            showToast("已移除 ${app.label} 快捷图标")
                        } else {
                            viewModel.removeAppFromPage(viewModel.activePageIndex.value, app.packageName)
                            showToast("已从桌面移除 ${app.label} 快捷图标")
                        }
                    } else {
                        viewModel.uninstallApp(context, app)
                    }
                } else {
                    val isOverDockZone = dropY >= (screenHeight - 160)

                    if (isOverDockZone) {
                        val currentDockList = dockPackages.toMutableList()

                        if (viewModel.isDraggingFromDock && viewModel.dragSourceIndex in currentDockList.indices) {
                            currentDockList.removeAt(viewModel.dragSourceIndex)
                        } else {
                            if (!app.packageName.startsWith("WIDGET:") && app.packageName != "MENU_BUTTON") {
                                viewModel.removeAppFromPage(viewModel.activePageIndex.value, app.packageName)
                            }
                        }

                        val itemsCount = currentDockList.size
                        val dockMaxWidth = 500f
                        val dockActualWidthDp = screenWidth.coerceAtMost(dockMaxWidth.toInt()) - 24f
                        val dockStartX = (screenWidth - dockActualWidthDp) / 2f
                        val activeStartX = dockStartX + 8f
                        val activeWidth = dockActualWidthDp - 16f
                        val cellWidthDp = if (itemsCount > 0) activeWidth / itemsCount else 68f
                        val targetIndex = if (itemsCount > 0) {
                            ((dropX - activeStartX) / cellWidthDp).toInt().coerceIn(0, itemsCount)
                        } else {
                            0
                        }

                        if (app.packageName == "MENU_BUTTON") {
                            currentDockList.add(targetIndex, "MENU_BUTTON")
                        } else {
                            currentDockList.removeAll { it == app.packageName }
                            currentDockList.add(targetIndex, app.packageName)
                        }

                        viewModel.updateDockConfiguration(currentDockList)
                        showToast("已调整 Dock 快捷排列")
                    } else {
                        var targetSlotIndex: Int? = null
                        for ((cellIdx, rect) in viewModel.homeGridBounds) {
                            if (dropX >= rect.left && dropX <= rect.right &&
                                dropY >= rect.top && dropY <= rect.bottom
                            ) {
                                targetSlotIndex = cellIdx
                                break
                            }
                        }
                        if (targetSlotIndex == null) {
                            var minDistance = Float.MAX_VALUE
                            var closestIdx: Int? = null
                            for ((cellIdx, rect) in viewModel.homeGridBounds) {
                                val centerX = (rect.left + rect.right) / 2f
                                val centerY = (rect.top + rect.bottom) / 2f
                                val dist = (dropX - centerX) * (dropX - centerX) + (dropY - centerY) * (dropY - centerY)
                                if (dist < minDistance) {
                                    minDistance = dist
                                    closestIdx = cellIdx
                                }
                            }
                            if (minDistance < 25000f) {
                                targetSlotIndex = closestIdx
                            }
                        }

                        val currentActivePageIdx = viewModel.activePageIndex.value
                        val isCrossPage = !viewModel.isDraggingFromDock &&
                                currentActivePageIdx != dragSourcePageIndex

                        if (targetSlotIndex != null) {
                            if (viewModel.isDraggingFromDock) {
                                val currentDockList = dockPackages.toMutableList()
                                if (viewModel.dragSourceIndex in currentDockList.indices) {
                                    currentDockList.removeAt(viewModel.dragSourceIndex)
                                }
                                viewModel.updateDockConfiguration(currentDockList)
                                viewModel.addAppToPageAtSlot(currentActivePageIdx, app.packageName, targetSlotIndex)
                                showToast("已将 ${app.label} 移至桌面指定位置")
                            } else if (isCrossPage) {
                                viewModel.removeAppFromPage(dragSourcePageIndex, app.packageName)
                                viewModel.addAppToPageAtSlot(currentActivePageIdx, app.packageName, targetSlotIndex)
                                showToast("已将 ${app.label} 移至第 ${currentActivePageIdx + 1} 页")
                            } else {
                                if (viewModel.dragSourceIndex != -1) {
                                    viewModel.moveAppInPage(currentActivePageIdx, viewModel.dragSourceIndex, targetSlotIndex)
                                    showToast("已调整 ${app.label} 的位置")
                                }
                            }
                        } else {
                            if (viewModel.isDraggingFromDock) {
                                if (app.packageName == "MENU_BUTTON") {
                                    showToast("菜单按钮不能移除！")
                                } else {
                                    val currentDockList = dockPackages.toMutableList()
                                    if (viewModel.dragSourceIndex in currentDockList.indices) {
                                        currentDockList.removeAt(viewModel.dragSourceIndex)
                                    }
                                    viewModel.updateDockConfiguration(currentDockList)
                                    showToast("已从 Dock 移除: ${app.label}")
                                }
                            } else if (isCrossPage) {
                                viewModel.removeAppFromPage(dragSourcePageIndex, app.packageName)
                                viewModel.addAppToPage(currentActivePageIdx, app.packageName)
                                showToast("已将 ${app.label} 移至第 ${currentActivePageIdx + 1} 页")
                            }
                        }
                    }
                }
            }
        }

        viewModel.draggedApp = null
        viewModel.isDraggingActive = false
        viewModel.isDraggingFromDock = false
        viewModel.isDraggingFromDrawer = false
        viewModel.dragSourceIndex = -1
        viewModel.isEditingHomeScreen = false
    }

    val preUninstallApp by viewModel.preUninstallApp.collectAsState()
    BackHandler(enabled = isAppDrawerOpen || isSettingsOpen || isGesturePanelOpen || preUninstallApp != null || viewModel.isEditingHomeScreen || isAddScreenOpen || isEffectSystemOpen) {
        when {
            isEffectSystemOpen -> {
                isEffectSystemOpen = false
            }
            isAddScreenOpen -> {
                isAddScreenOpen = false
            }
            viewModel.preUninstallApp.value != null -> {
                viewModel.preUninstallApp.value = null
            }
            viewModel.isEditingHomeScreen -> {
                viewModel.isEditingHomeScreen = false
            }
            isSettingsOpen -> isSettingsOpen = false
            isGesturePanelOpen -> isGesturePanelOpen = false
            isAppDrawerOpen -> {
                if (viewModel.drawerPageIndex.value > 0) {
                    viewModel.drawerPageIndex.value = 0
                    viewModel.backToFirstScreenEvent.tryEmit(Unit)
                } else {
                    isAppDrawerOpen = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedTouchEffect, touchPool) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    spawnTouchParticles(
                        x = down.position.x,
                        y = down.position.y,
                        effectType = selectedTouchEffect,
                        randomPool = touchPool,
                        activeParticles = globalParticles
                    )
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        // ✅ 修复：isAddScreenOpen 时退出循环，完全不拦截内层手势
                        if (isAddScreenOpen) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            continue
                        }

                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (viewModel.isDraggingActive) {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                val d = density.density
                                val screenWidth = context.resources.displayMetrics.widthPixels / d
                                val newX = change.position.x / d
                                val newY = change.position.y / d

                                if (viewModel.dragDistance == -1f) {
                                    viewModel.dragDistance = 0f
                                    viewModel.dragOffset = androidx.compose.ui.geometry.Offset(newX, newY)
                                } else {
                                    val oldOffset = viewModel.dragOffset
                                    if (oldOffset != androidx.compose.ui.geometry.Offset.Zero) {
                                        val dx = newX - oldOffset.x
                                        val dy = newY - oldOffset.y
                                        viewModel.dragDistance += Math.abs(dx) + Math.abs(dy)
                                    }
                                }

                                viewModel.dragOffset = androidx.compose.ui.geometry.Offset(newX, newY)
                                change.consume()

                                if (viewModel.isDraggingFromDrawer && viewModel.draggedApp?.packageName?.startsWith("WIDGET:") != true) {
                                    val itemsPerPage = when (viewModel.drawerGrid.value) {
                                        "5x5" -> 25
                                        else -> 24
                                    }
                                    val displayApps = viewModel.filteredApps.value
                                    val totalPages = Math.max(1, (displayApps.size + itemsPerPage - 1) / itemsPerPage) + 2
                                    viewModel.checkDrawerEdgeScroll(viewModel.dragOffset.x, screenWidth, totalPages)
                                } else {
                                    viewModel.checkHomeEdgeScroll(viewModel.dragOffset.x, screenWidth, homePages.size)
                                }

                                val anyPressed = event.changes.any { it.pressed }
                                if (!anyPressed) {
                                    if (viewModel.dragDistance < 12f) {
                                        if (viewModel.isDraggingFromDrawer) {
                                            viewModel.preUninstallApp.value = viewModel.draggedApp
                                        }
                                        viewModel.draggedApp = null
                                        viewModel.isDraggingActive = false
                                        viewModel.isDraggingFromDrawer = false
                                        viewModel.isDraggingFromDock = false
                                        viewModel.isEditingHomeScreen = false
                                    } else {
                                        handleGlobalDrop()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {

        LauncherBackground(wallpaperName = wallpaperName)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            if (!viewModel.isDraggingActive) {
                                isGesturePanelOpen = true
                            }
                        }
                    )
                }
                // ── 修复：上滑触发手势面板，拖拽中不触发；消费事件避免穿透到 verticalScroll ──
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var isTriggered = false
                        do {
                            val event = awaitPointerEvent()
                            val pointerChange = event.changes.firstOrNull()
                            if (pointerChange != null && !viewModel.isDraggingActive) {
                                val dragX = pointerChange.position.x - startX
                                val dragY = pointerChange.position.y - startY
                                // 垂直向上滑动距离需大于 100 且明显大于水平滑动距离，以防左右翻页时误触
                                if (dragY < -100f && kotlin.math.abs(dragY) > kotlin.math.abs(dragX) * 1.8f && !isTriggered) {
                                    isGesturePanelOpen = true
                                    isTriggered = true
                                    // 消费后续所有事件，阻断向下传播给任何滚动容器
                                    pointerChange.consume()
                                }
                                if (isTriggered) {
                                    pointerChange.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer {
                    val progress = crossProgress // 0 is Home, 1 is App Drawer
                    alpha = (1f - progress)
                    cameraDistance = 8f * this.density
                    
                    if (progress > 0f) {
                        when (activeCrossType) {
                            "默认" -> {
                                alpha = 1f
                            }
                            "内缩放" -> {
                                val s = 1f + progress * 0.4f
                                scaleX = s
                                scaleY = s
                            }
                            "外缩放" -> {
                                val s = 1f - progress * 0.4f
                                scaleX = s
                                scaleY = s
                            }
                            "风车" -> {
                                val s = 1f - progress
                                scaleX = s
                                scaleY = s
                                rotationZ = -(progress * 180f)
                            }
                            "电视机" -> {
                                val tvProgress = (1f - progress)
                                scaleX = if (tvProgress > 0.5f) 1f else tvProgress * 2f
                                scaleY = if (tvProgress < 0.5f) 0.05f else (tvProgress - 0.5f) * 2f
                            }
                        }
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                        rotationZ = 0f
                    }
                }
        ) {
            val pagerState = rememberPagerState(
                initialPage = activePageIndex.coerceIn(0, maxOf(1, homePages.size) - 1),
                pageCount = { homePages.size }
            )

            LaunchedEffect(pagerState.currentPage) {
                if (viewModel.activePageIndex.value != pagerState.currentPage) {
                    viewModel.activePageIndex.value = pagerState.currentPage
                }
            }
            LaunchedEffect(activePageIndex) {
                val bounded = activePageIndex.coerceIn(0, homePages.size - 1)
                if (pagerState.currentPage != bounded && homePages.isNotEmpty()) {
                    pagerState.animateScrollToPage(bounded)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 140.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { idx ->
                    val page = homePages.getOrNull(idx) ?: HomeScreenPage(idx.toString())
                    val pageOffset = (pagerState.currentPage - idx) + pagerState.currentPageOffsetFraction
                    val selectedHomeTransition by viewModel.homeTransition.collectAsState()
                    val homePool by viewModel.homeRandomPool.collectAsState()

                    // ── 修复核心：移除 verticalScroll，改为不可滚动的固定 Column ──────
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .pagerTransition(
                                pageOffset = pageOffset,
                                effect = selectedHomeTransition,
                                randomPool = homePool,
                                pageIndex = idx
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        if (idx == 0) {
                            LauncherClock(
                                clockStyle = clockStyle,
                                themeColor = themeColor,
                                viewModel = viewModel,
                                modifier = Modifier.padding(top = 40.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(30.dp))
                        }

                        page.widgets.forEach { widget ->
                            var showMenu by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(widget) {
                                        detectTapGestures(
                                            onLongPress = { showMenu = true }
                                        )
                                    }
                            ) {
                                LauncherCustomWidgets(
                                    widgetType = widget,
                                    themeColor = themeColor,
                                    viewModel = viewModel
                                )
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("从桌面页面移除此组件") },
                                        onClick = {
                                            viewModel.removeWidgetFromPage(idx, widget)
                                            showToast("已成功移除组件：$widget")
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val pageAppsList = page.apps.toMutableList()
                        while (pageAppsList.size < 24) {
                            pageAppsList.add("EMPTY")
                        }
                        if (pageAppsList.size > 24) {
                            pageAppsList.subList(24, pageAppsList.size).clear()
                        }

                        val chunkedIndices = (0 until 24).chunked(4)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            chunkedIndices.forEach { rowIndices ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowIndices.forEach { cellIdx ->
                                        val pkg = pageAppsList[cellIdx]
                                        val app = appList.firstOrNull { it.packageName == pkg }
                                        val densityVal = androidx.compose.ui.platform.LocalDensity.current.density

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .onGloballyPositioned { bounds ->
                                                    val coords = bounds.positionInWindow()
                                                    val x = coords.x / densityVal
                                                    val y = coords.y / densityVal
                                                    val w = bounds.size.width / densityVal
                                                    val h = bounds.size.height / densityVal
                                                    viewModel.homeGridBounds = viewModel.homeGridBounds + (cellIdx to androidx.compose.ui.geometry.Rect(x, y, x + w, y + h))
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (app != null) {
                                                var showShortCutMenu by remember { mutableStateOf(false) }
                                                var itemScreenX by remember { mutableStateOf(0f) }
                                                var itemScreenY by remember { mutableStateOf(0f) }

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .onGloballyPositioned { bounds ->
                                                            val coords = bounds.positionInWindow()
                                                            itemScreenX = coords.x / densityVal
                                                            itemScreenY = coords.y / densityVal
                                                        }
                                                        .clickable {
                                                            viewModel.recordAppLaunch(app.packageName)
                                                            app.launch(context)
                                                        }
                                                        .pointerInput(app) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = { localOffset ->
                                                                    viewModel.draggedApp = app
                                                                    viewModel.isDraggingActive = true
                                                                    viewModel.isDraggingFromDock = false
                                                                    viewModel.isDraggingFromDrawer = false
                                                                    viewModel.dragSourceIndex = cellIdx
                                                                    viewModel.dragOffset = androidx.compose.ui.geometry.Offset(
                                                                        x = itemScreenX + 26f,
                                                                        y = itemScreenY + 26f
                                                                    )
                                                                    viewModel.dragDistance = -1f
                                                                    showShortCutMenu = true
                                                                    viewModel.isEditingHomeScreen = true
                                                                    viewModel.homeGridBounds = emptyMap()
                                                                    dragSourcePageIndex = idx
                                                                },
                                                                onDragEnd = {},
                                                                onDragCancel = {},
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    showShortCutMenu = false
                                                                }
                                                            )
                                                        }
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        IconStylingCard(
                                                            app = app,
                                                            filter = iconPackFilter,
                                                            themeColor = themeColor,
                                                            modifier = Modifier.size(54.dp)
                                                        )
                                                        if (showLabels) {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = app.label,
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = showShortCutMenu && !viewModel.isDraggingActive,
                                                            onDismissRequest = { showShortCutMenu = false }
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("从桌面页移除快捷方式") },
                                                                onClick = {
                                                                    viewModel.removeAppFromPage(idx, app.packageName)
                                                                    showToast("已删除 ${app.label} 快捷方式")
                                                                    showShortCutMenu = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                if (viewModel.isEditingHomeScreen) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(54.dp)
                                                            .border(
                                                                width = 1.dp,
                                                                color = Color.White.copy(alpha = 0.25f),
                                                                shape = RoundedCornerShape(14.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "+",
                                                            color = Color.White.copy(alpha = 0.25f),
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.size(54.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }

            if (homePages.size > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 108.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        homePages.forEachIndexed { idx, _ ->
                            val isCurrent = idx == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(if (isCurrent) 8.dp else 6.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(CircleShape)
                                    .background(if (isCurrent) themeColor else Color.White.copy(alpha = 0.40f))
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp, start = 12.dp, end = 12.dp)
                .graphicsLayer {
                    val progress = crossProgress // 0 is Home, 1 is App Drawer
                    alpha = (1f - progress)
                    cameraDistance = 8f * this.density
                    
                    if (progress > 0f) {
                        when (activeCrossType) {
                            "默认" -> {
                                alpha = 1f
                            }
                            "内缩放" -> {
                                val s = 1f + progress * 0.4f
                                scaleX = s
                                scaleY = s
                            }
                            "外缩放" -> {
                                val s = 1f - progress * 0.4f
                                scaleX = s
                                scaleY = s
                            }
                            "风车" -> {
                                val s = 1f - progress
                                scaleX = s
                                scaleY = s
                                rotationZ = -(progress * 180f)
                            }
                            "电视机" -> {
                                val tvProgress = (1f - progress)
                                scaleX = if (tvProgress > 0.5f) 1f else tvProgress * 2f
                                scaleY = if (tvProgress < 0.5f) 0.05f else (tvProgress - 0.5f) * 2f
                            }
                        }
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                        rotationZ = 0f
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 500.dp)
                    .height(82.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xB3131313)
                ),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dockPackages.forEachIndexed { index, pkg ->
                        val isTrigger = pkg == "MENU_BUTTON"
                        val density = androidx.compose.ui.platform.LocalDensity.current.density
                        var itemScreenX by remember { mutableStateOf(0f) }
                        var itemScreenY by remember { mutableStateOf(0f) }
                        val isBeingDragged = viewModel.isDraggingActive && viewModel.isDraggingFromDock && viewModel.dragSourceIndex == index

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { bounds ->
                                    val coords = bounds.positionInWindow()
                                    itemScreenX = coords.x / density
                                    itemScreenY = coords.y / density
                                }
                                .alpha(if (isBeingDragged) 0.05f else 1f)
                                .pointerInput(pkg) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            if (pkg == "MENU_BUTTON") {
                                                viewModel.draggedApp = AppModel("控制面板", "MENU_BUTTON", "")
                                            } else {
                                                viewModel.draggedApp = appList.firstOrNull { it.packageName == pkg } ?: AppModel("应用", pkg, "")
                                            }
                                            viewModel.isDraggingActive = true
                                            viewModel.isDraggingFromDock = true
                                            viewModel.dragSourceIndex = index
                                            viewModel.dragOffset = androidx.compose.ui.geometry.Offset(
                                                x = itemScreenX + 25f,
                                                y = itemScreenY + 25f
                                            )
                                            viewModel.dragDistance = -1f
                                            viewModel.isEditingHomeScreen = true
                                        },
                                        onDragEnd = {},
                                        onDragCancel = {},
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isTrigger) {
                                val infiniteTransition = rememberInfiniteTransition(label = "breathe")
                                val breatheScale by infiniteTransition.animateFloat(
                                    initialValue = 0.93f,
                                    targetValue = 1.07f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1400, easing = EaseInOutSine),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .shadow(6.dp, CircleShape)
                                        .graphicsLayer(scaleX = breatheScale, scaleY = breatheScale)
                                        .border(4.dp, themeColor.copy(alpha = 0.3f), CircleShape)
                                        .clip(CircleShape)
                                        .background(themeColor)
                                        .clickable { isAppDrawerOpen = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(Color.White, shape = CircleShape)
                                    )
                                }
                            } else {
                                val linkedApp = appList.firstOrNull { it.packageName == pkg }
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            linkedApp?.launch(context) ?: showToast("未绑定或包已被卸载")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconStylingCard(
                                        app = linkedApp ?: AppModel("未安装", pkg, ""),
                                        filter = iconPackFilter,
                                        themeColor = themeColor,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isGesturePanelOpen,
            enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3000000))
                    .clickable { isGesturePanelOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEC131313)),
                    border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.3f), shape = CircleShape)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "久以轻手势",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        val gridItems = listOf(
                            GestureMenuOption("添加", Icons.Default.Add) {
                                isGesturePanelOpen = false
                                isAddScreenOpen = true
                            },
                            GestureMenuOption("换特效", Icons.Default.AutoAwesome) {
                                isGesturePanelOpen = false
                                isEffectSystemOpen = true
                                showToast("Launcher 动效引擎已开启")
                            },
                            GestureMenuOption("快速美化", Icons.Default.Brush) {
                                isGesturePanelOpen = false
                                val list = listOf("Minimalist", "Vintage Pixel", "Sketch Outline", "Raw Native")
                                val nextIdx = (list.indexOf(iconPackFilter) + 1) % list.size
                                viewModel.updateIconPackFilter(list[nextIdx])
                                showToast("桌面图标样式切换为: ${list[nextIdx]}")
                            },
                            GestureMenuOption("系统设置", Icons.Default.Settings) {
                                isGesturePanelOpen = false
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    isSettingsOpen = true
                                }
                            },
                            GestureMenuOption("个性主题", Icons.Default.Palette) {
                                isGesturePanelOpen = false
                                isSettingsOpen = true
                            },
                            GestureMenuOption("个人中心", Icons.Default.Person) {
                                isGesturePanelOpen = false
                                showToast("久以智能桌面 • 用户中心 v2.0")
                            },
                            GestureMenuOption("屏幕预览", Icons.Default.Visibility) {
                                isGesturePanelOpen = false
                                isAddScreenOpen = true
                                showToast("已开启桌面屏幕多页管理预览")
                            },
                            GestureMenuOption("桌面设置", Icons.Default.GridView) {
                                isGesturePanelOpen = false
                                isSettingsOpen = true
                            }
                        )

                        for (i in 0 until 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (j in 0 until 4) {
                                    val item = gridItems[i * 4 + j]
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { item.action() }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .background(Color(0x1BFFFFFF), shape = RoundedCornerShape(20.dp))
                                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = item.label,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val drawerAlpha by animateFloatAsState(
            targetValue = if (viewModel.isDraggingActive) {
                if (viewModel.isDraggingFromDrawer) 1f else 0.15f
            } else 1f,
            label = "drawerAlpha"
        )

        val isDefaultCross = activeCrossType == "默认"
        val showDrawer = isAppDrawerOpen || if (isDefaultCross) {
            appDrawerOffset < 1180.dp
        } else {
            crossProgress > 0.05f
        }

        if (showDrawer) {
            val drawerOffsetY = if (isAppDrawerOpen) {
                0.dp
            } else {
                if (isDefaultCross) {
                    if (appDrawerOffset >= 1180.dp) 5000.dp else 0.dp
                } else {
                    if (crossProgress <= 0.05f) 5000.dp else 0.dp
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = drawerOffsetY)
                    .graphicsLayer {
                        val progress = crossProgress // 0 is Home, 1 is App Drawer
                        cameraDistance = 8f * this.density
                        
                        if (isDefaultCross) {
                            translationY = appDrawerOffset.toPx()
                            alpha = drawerAlpha
                        } else {
                            alpha = progress
                            if (progress > 0f) {
                                when (activeCrossType) {
                                    "内缩放" -> {
                                        val s = 0.6f + progress * 0.4f
                                        scaleX = s
                                        scaleY = s
                                    }
                                    "外缩放" -> {
                                        val s = 1.4f - progress * 0.4f
                                        scaleX = s
                                        scaleY = s
                                    }
                                    "风车" -> {
                                        val s = progress
                                        scaleX = s
                                        scaleY = s
                                        rotationZ = (1f - progress) * 180f
                                    }
                                    "电视机" -> {
                                        scaleX = if (progress > 0.5f) 1f else progress * 2f
                                        scaleY = if (progress < 0.5f) 0.05f else (progress - 0.5f) * 2f
                                    }
                                }
                            } else {
                                scaleX = 1f
                                scaleY = 1f
                                rotationZ = 0f
                            }
                        }
                    }
            ) {
                LauncherAppDrawer(
                    viewModel = viewModel,
                    themeColor = themeColor,
                    onClose = { isAppDrawerOpen = false },
                    showToast = showToast,
                    pinnedWidgets = homePages.getOrNull(activePageIndex)?.widgets?.toSet() ?: emptySet(),
                    onPinWidgetToggle = { widgetName ->
                        val activePage = homePages.getOrNull(activePageIndex)
                        if (activePage != null) {
                            if (activePage.widgets.contains(widgetName)) {
                                viewModel.removeWidgetFromPage(activePageIndex, widgetName)
                                showToast("已在桌面主屏移除: $widgetName")
                            } else {
                                viewModel.addWidgetToPage(activePageIndex, widgetName)
                                showToast("已成功贴合至第 ${activePageIndex + 1} 页主屏: $widgetName")
                            }
                        }
                        isAppDrawerOpen = false
                    },
                    onDrop = handleGlobalDrop
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = settingsOffset)
        ) {
            LauncherSettingsPanel(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = { isSettingsOpen = false },
                showToast = showToast
            )
        }

        AnimatedVisibility(
            visible = viewModel.isEditingHomeScreen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val dropX = viewModel.dragOffset.x
            val dropY = viewModel.dragOffset.y
            val isOverLeftDelete = dropY <= 100 && dropX < screenWidth / 2f && viewModel.isDraggingActive
            val isOverRightUninstall = dropY <= 100 && dropX >= screenWidth / 2f && viewModel.isDraggingActive

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xF21C1C1E)
                ),
                border = BorderStroke(1.dp, Color(0x2BFFFFFF))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isOverLeftDelete) Color(0x3DF44336) else Color.Transparent)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = if (isOverLeftDelete) Color(0xFFEF5350) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "删除图标",
                                color = if (isOverLeftDelete) Color(0xFFEF5350) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0x1BFFFFFF))
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isOverRightUninstall) Color(0x3DF44336) else Color.Transparent)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Uninstall",
                                tint = if (isOverRightUninstall) Color(0xFFEF5350) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "卸载应用",
                                color = if (isOverRightUninstall) Color(0xFFEF5350) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (viewModel.showCitySelectorDialog) {
            CitySelectorDialog(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = { viewModel.showCitySelectorDialog = false }
            )
        }

        AnimatedVisibility(
            visible = isAddScreenOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            AddManagementScreen(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = { isAddScreenOpen = false },
                showToast = showToast,
                onDrop = handleGlobalDrop
            )
        }

        Canvas(modifier = Modifier.fillMaxSize().alpha(1f)) {
            val drawScope: androidx.compose.ui.graphics.drawscope.DrawScope = this
            globalParticles.forEach { p ->
                drawParticleOnCanvas(p, drawScope)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = effectSystemOffset)
        ) {
            LauncherAnimationCenter(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = { isEffectSystemOpen = false },
                showToast = showToast
            )
        }

        DragOverlay(
            viewModel = viewModel,
            themeColor = themeColor,
            iconPackFilter = iconPackFilter,
            isAddScreenOpen = isAddScreenOpen
        )
    }
}

private data class GestureMenuOption(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val action: () -> Unit
)

@Composable
fun DragOverlay(
    viewModel: LauncherViewModel,
    themeColor: Color,
    iconPackFilter: String,
    isAddScreenOpen: Boolean = false
) {
    val draggedApp = viewModel.draggedApp
    val isDraggingActive = viewModel.isDraggingActive
    if (draggedApp != null && isDraggingActive) {
        val dragOffset = viewModel.dragOffset
        val hideOverlayZones = viewModel.isDraggingFromDrawer || isAddScreenOpen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (hideOverlayZones) Color.Transparent else Color.Black.copy(alpha = 0.28f))
        ) {
            if (!hideOverlayZones) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x27FFFFFF))
                ) {
                    Text(
                        text = "📥 拖拽到此放置于 Dock 栏 • 拖出松手移除",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = (dragOffset.x - 26).dp,
                        y = (dragOffset.y - 26).dp
                    )
                    .size(52.dp)
            ) {
                IconStylingCard(
                    app = draggedApp,
                    filter = iconPackFilter,
                    themeColor = themeColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}