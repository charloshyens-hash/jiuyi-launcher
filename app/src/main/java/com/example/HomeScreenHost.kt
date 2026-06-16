package com.example

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext

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
        } else selectedCrossTransition
    }

    val effectSystemOffset by animateDpAsState(
        targetValue = if (isEffectSystemOpen) 0.dp else 1200.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "effectSystem"
    )

    // 粒子物理帧循环
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
        HomeScreenDropHandler.handle(
            viewModel = viewModel,
            dockPackages = dockPackages,
            homePages = homePages,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isAddScreenOpen = isAddScreenOpen,
            dragSourcePageIndex = dragSourcePageIndex,
            showToast = showToast
        )
    }

    val preUninstallApp by viewModel.preUninstallApp.collectAsState()
    BackHandler(enabled = isAppDrawerOpen || isSettingsOpen || isGesturePanelOpen || preUninstallApp != null || viewModel.isEditingHomeScreen || isAddScreenOpen || isEffectSystemOpen) {
        when {
            isEffectSystemOpen -> isEffectSystemOpen = false
            isAddScreenOpen -> isAddScreenOpen = false
            viewModel.preUninstallApp.value != null -> viewModel.preUninstallApp.value = null
            viewModel.isEditingHomeScreen -> viewModel.isEditingHomeScreen = false
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

    HomeScreenScaffold(
        viewModel = viewModel,
        themeColor = themeColor,
        isAppDrawerOpen = isAppDrawerOpen,
        isSettingsOpen = isSettingsOpen,
        isGesturePanelOpen = isGesturePanelOpen,
        isAddScreenOpen = isAddScreenOpen,
        isEffectSystemOpen = isEffectSystemOpen,
        globalParticles = globalParticles,
        selectedTouchEffect = selectedTouchEffect,
        touchPool = touchPool,
        crossProgress = crossProgress,
        activeCrossType = activeCrossType,
        effectSystemOffset = effectSystemOffset,
        appDrawerOffset = appDrawerOffset,
        settingsOffset = settingsOffset,
        wallpaperName = wallpaperName,
        clockStyle = clockStyle,
        dockPackages = dockPackages,
        appList = appList,
        showLabels = showLabels,
        iconPackFilter = iconPackFilter,
        homePages = homePages,
        activePageIndex = activePageIndex,
        showToast = showToast,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        onDragSourcePageChange = { dragSourcePageIndex = it },
        handleGlobalDrop = handleGlobalDrop,
        onAppDrawerOpen = { isAppDrawerOpen = true },
        onAppDrawerClose = { isAppDrawerOpen = false },
        onSettingsOpen = { isSettingsOpen = true },
        onGesturePanelOpen = { isGesturePanelOpen = true },
        onGesturePanelClose = { isGesturePanelOpen = false },
        onAddScreenOpen = { isAddScreenOpen = true },
        onAddScreenClose = { isAddScreenOpen = false },
        onEffectSystemOpen = { isEffectSystemOpen = true },
        onEffectSystemClose = { isEffectSystemOpen = false }
    )
}