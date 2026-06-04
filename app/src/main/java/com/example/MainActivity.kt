package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.theme.PurpleBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.EmeraldTeal
import com.example.ui.theme.VelvetPink
import com.example.ui.theme.AmberYellow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Schedule periodic weather sync in background via WorkManager
        try {
            com.example.weather.WeatherSyncScheduler.scheduleWeatherSync(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule weather sync: ${e.message}")
        }

        // Supports full edge-to-edge immersive visualization
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val themeColorIndex by viewModel.currentThemeIndex.collectAsState()
                
                // Get the synchronized active color
                val themeColors = listOf(PurpleBlue, NeonCyan, EmeraldTeal, VelvetPink, AmberYellow)
                val activeThemeColor = themeColors.getOrElse(themeColorIndex) { PurpleBlue }

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
        // 1. Trigger live weather updates dynamically on resuming desktop focus
        try {
            viewModel.fetchRealWeather()
        } catch (e: Exception) {}

        // 2. Revive media notification listener state instantly when returning to the layout
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

@Composable
fun LauncherHomeScreen(
    viewModel: LauncherViewModel,
    themeColor: Color
) {
    val context = LocalContext.current
    val themeColorIndex by viewModel.currentThemeIndex.collectAsState()
    
    // UI Panel visibility states
    var isAppDrawerOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isGesturePanelOpen by remember { mutableStateOf(false) }

    // Floating Pinned Widgets on desktop
    var pinnedWidgets by remember { mutableStateOf(setOf<String>("RAM Booster", "Music Cassette")) }

    // Collect settings from StateFlow
    val wallpaperName by viewModel.wallpaperName.collectAsState()
    val clockStyle by viewModel.clockStyle.collectAsState()
    val dockPackages by viewModel.dockPackages.collectAsState()
    val appList by viewModel.appList.collectAsState()
    val showLabels by viewModel.showLabels.collectAsState()
    val iconPackFilter by viewModel.iconPackFilter.collectAsState()

    // Slide-up trigger animation
    val appDrawerOffset by animateDpAsState(
        targetValue = if (isAppDrawerOpen) 0.dp else 1200.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "appDrawer"
    )

    // Side screen transition offset for Settings
    val settingsOffset by animateDpAsState(
        targetValue = if (isSettingsOpen) 0.dp else 1200.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "settingsOffset"
    )

    // Unified helper Toast feedback
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
            val isOverDockZone = dropY >= (screenHeight - 160)

            if (isOverDockZone) {
                val currentDockList = dockPackages.toMutableList()
                
                if (viewModel.isDraggingFromDock && viewModel.dragSourceIndex in currentDockList.indices) {
                    currentDockList.removeAt(viewModel.dragSourceIndex)
                }
                
                val itemsCount = currentDockList.size
                val centerX = screenWidth / 2f
                val itemWidthDp = 68f
                val totalWidth = itemsCount * itemWidthDp
                val startX = centerX - totalWidth / 2f
                
                val targetIndex = ((dropX - startX) / itemWidthDp).toInt().coerceIn(0, itemsCount)
                
                if (app.packageName == "MENU_BUTTON") {
                    currentDockList.add(targetIndex, "MENU_BUTTON")
                } else {
                    currentDockList.removeAll { it == app.packageName }
                    currentDockList.add(targetIndex, app.packageName)
                }
                
                viewModel.updateDockConfiguration(currentDockList)
                showToast("已调整 Dock 快捷排列")
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
                }
            }
        }
        
        viewModel.draggedApp = null
        viewModel.isDraggingActive = false
        viewModel.isDraggingFromDock = false
        viewModel.dragSourceIndex = -1
    }

    // Intercept standard hardware system Android Back Press
    BackHandler(enabled = isAppDrawerOpen || isSettingsOpen || isGesturePanelOpen) {
        when {
            isSettingsOpen -> isSettingsOpen = false
            isGesturePanelOpen -> isGesturePanelOpen = false
            isAppDrawerOpen -> {
                if (viewModel.drawerPageIndex.value > 0) {
                    // Send deep force-reset signal to snap page state back to index 0
                    viewModel.drawerPageIndex.value = 0
                    viewModel.backToFirstScreenEvent.tryEmit(Unit)
                } else {
                    // Exactly at the first page of "应用" tab, close the drawer
                    isAppDrawerOpen = false
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Dynamic Wallpaper Draw Engine (Centers & Animated Particles)
        LauncherBackground(wallpaperName = wallpaperName)

        // GestureDetector overlay for Swipe Up and Long Press actions
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            isGesturePanelOpen = true
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y
                        var isTriggered = false
                        do {
                            val event = awaitPointerEvent()
                            val pointerChange = event.changes.firstOrNull()
                            if (pointerChange != null) {
                                val dragY = pointerChange.position.y - startY
                                if (dragY < -60f && !isTriggered) {
                                    isGesturePanelOpen = true
                                    isTriggered = true
                                    pointerChange.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 120.dp) // Leave blank space at bottom for floating Dock
            ) {
                // 2. Centerpiece clocks widget
                LauncherClock(
                    clockStyle = clockStyle,
                    themeColor = themeColor,
                    viewModel = viewModel,
                    modifier = Modifier.padding(top = 40.dp)
                )

                // 3. Desktop scroll deck harboring dynamic pinned widgets
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pinnedWidgets.forEach { widget ->
                        LauncherCustomWidgets(
                            widgetType = widget,
                            themeColor = themeColor,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        // 4. Floating Capsule Custom Dock Navigation Area (Dynamically centered & fitted capsule layout)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp, start = 12.dp, end = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .wrapContentWidth() // centers overall and adjusts with number of items
                    .height(82.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xC70F172A) // Sleek Premium frosted Dark Glass
                ),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF))
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                                                x = itemScreenX + 27f,
                                                y = itemScreenY + 27f
                                            )
                                        },
                                        onDragEnd = {
                                            handleGlobalDrop()
                                        },
                                        onDragCancel = {
                                            viewModel.draggedApp = null
                                            viewModel.isDraggingActive = false
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            viewModel.dragOffset = viewModel.dragOffset + androidx.compose.ui.geometry.Offset(
                                                x = dragAmount.x / density,
                                                y = dragAmount.y / density
                                            )
                                        }
                                    )
                                }
                        ) {
                            if (isTrigger) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .shadow(4.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(themeColor, themeColor.copy(alpha = 0.7f))
                                            )
                                        )
                                        .clickable { isAppDrawerOpen = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = "开启应用抽屉",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                val linkedApp = appList.firstOrNull { it.packageName == pkg }
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            linkedApp?.launch(context) ?: showToast("未绑定或包已被卸载")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconStylingCard(
                                            app = linkedApp ?: AppModel("未安装", pkg, ""),
                                            filter = iconPackFilter,
                                            themeColor = themeColor,
                                            modifier = Modifier.size(38.dp)
                                        )
                                        if (showLabels) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = linkedApp?.label?.take(3) ?: pkg.takeLast(3),
                                                fontSize = 9.sp,
                                                color = Color.White.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.width(44.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Translucent Functioning Gestural Overlay Layer (4x2 entrances)
        AnimatedVisibility(
            visible = isGesturePanelOpen,
            enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD8000000)) // Translucent matte dim background
                    .clickable { isGesturePanelOpen = false },
                contentAlignment = Alignment.Center
            ) {
                // Frosted card holding 4x2 functioning icons
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clickable(enabled = false) {}, // prevent click-through
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF21E293B)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "久以轻手势 • 半透明功能层",
                            color = themeColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Text(
                            text = "内置酷炫动力学组件与复古91交互，体验极致定制魅力",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // 4x2 Grid layout representation
                        val gridItems = listOf(
                            GestureMenuOption("添加小部件", Icons.Default.AddBox) {
                                isGesturePanelOpen = false
                                isAppDrawerOpen = true
                                showToast("请滑动转入「小部件」标签添加")
                            },
                            GestureMenuOption("换壁纸特效", Icons.Default.Landscape) {
                                isGesturePanelOpen = false
                                isSettingsOpen = true
                                showToast("请在设置中自选动态壁纸款式")
                            },
                            GestureMenuOption("极速美化", Icons.Default.AutoAwesome) {
                                isGesturePanelOpen = false
                                // Toggle icon filter cycle
                                val list = listOf("Minimalist", "Vintage Pixel", "Sketch Outline", "Raw Native")
                                val nextIdx = (list.indexOf(iconPackFilter) + 1) % list.size
                                viewModel.updateIconPackFilter(list[nextIdx])
                                showToast("极速美化已切换图标风格至: ${list[nextIdx]}")
                            },
                            GestureMenuOption("系统设置", Icons.Default.SystemUpdateAlt) {
                                isGesturePanelOpen = false
                                isSettingsOpen = true
                            },
                            GestureMenuOption("个性主题", Icons.Default.ColorLens) {
                                isGesturePanelOpen = false
                                val nextIndex = (themeColorIndex + 1) % 5
                                viewModel.updateTheme(nextIndex)
                                showToast("已切换桌面原色主题")
                            },
                            GestureMenuOption("加速清理", Icons.Default.Cyclone) {
                                isGesturePanelOpen = false
                                viewModel.boostRam()
                                showToast("火箭喷射！系统物理 RAM 清理运行中...")
                            },
                            GestureMenuOption("屏幕预览", Icons.Default.Fullscreen) {
                                isGesturePanelOpen = false
                                // Toggle active widgets list
                                pinnedWidgets = if (pinnedWidgets.isEmpty()) {
                                    setOf("RAM Booster", "Music Cassette")
                                } else {
                                    emptySet()
                                }
                                showToast("屏幕极简视差模式已被切换")
                            },
                            GestureMenuOption("开启抽屉", Icons.Default.Apps) {
                                isGesturePanelOpen = false
                                isAppDrawerOpen = true
                            }
                        )

                        // Draw Grid row-by-row
                        for (i in 0 until 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (j in 0 until 4) {
                                    val item = gridItems[i * 4 + j]
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { item.action() }
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(themeColor.copy(alpha = 0.12f), shape = CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = item.icon, contentDescription = item.label, tint = themeColor)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = item.label,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
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

        // 6. Sliding applications Horizontal Drawer
        val drawerAlpha by animateFloatAsState(
            targetValue = if (viewModel.isDraggingActive) 0.15f else 1f,
            label = "drawerAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = appDrawerOffset)
                .alpha(drawerAlpha)
        ) {
            LauncherAppDrawer(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = { isAppDrawerOpen = false },
                showToast = showToast,
                pinnedWidgets = pinnedWidgets,
                onPinWidgetToggle = { widgetName ->
                    val current = pinnedWidgets.toMutableSet()
                    if (current.contains(widgetName)) {
                        current.remove(widgetName)
                        showToast("已在桌面主屏移除: $widgetName")
                    } else {
                        current.add(widgetName)
                        showToast("已成功贴合至主屏: $widgetName")
                    }
                    pinnedWidgets = current
                    isAppDrawerOpen = false
                },
                onDrop = handleGlobalDrop
            )
        }

        // 7. Dynamic sliding settings board Center Console
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

        // 8. Live Drag and Drop Floating Overlay rendering on top of everything (isolated to prevent full-screen recomposition)
        DragOverlay(
            viewModel = viewModel,
            themeColor = themeColor,
            iconPackFilter = iconPackFilter
        )

        // 9. Floating City Selector Dialog overlay
        if (viewModel.showCitySelectorDialog) {
            CitySelectorDialog(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = { viewModel.showCitySelectorDialog = false }
            )
        }

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
    iconPackFilter: String
) {
    val draggedApp = viewModel.draggedApp
    val isDraggingActive = viewModel.isDraggingActive
    if (draggedApp != null && isDraggingActive) {
        val dragOffset = viewModel.dragOffset
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f)) // dim background slightly
        ) {
            // Dim helper dock zone representation at the bottom 160.dp
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

            // Dragged replica following absolute coordinates
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
