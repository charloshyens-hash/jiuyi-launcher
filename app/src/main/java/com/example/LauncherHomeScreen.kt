package com.example

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withFrameNanos

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherHomeScreen(
    viewModel: LauncherViewModel,
    themeColor: Color
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // ── 面板开关状态 ──────────────────────────────────────────────────────────
    var isAppDrawerOpen    by remember { mutableStateOf(false) }
    var isSettingsOpen     by remember { mutableStateOf(false) }
    var isGesturePanelOpen by remember { mutableStateOf(false) }
    var isAddScreenOpen    by remember { mutableStateOf(false) }
    var isEffectSystemOpen by remember { mutableStateOf(false) }

    // ── 触摸粒子 ──────────────────────────────────────────────────────────────
    val globalParticles       = remember { mutableStateListOf<TouchParticle>() }
    val selectedTouchEffect   by viewModel.touchEffect.collectAsState()
    val touchPool             by viewModel.touchRandomPool.collectAsState()

    // ── 跨屏过渡动画 ──────────────────────────────────────────────────────────
    val selectedCrossTransition by viewModel.crossTransition.collectAsState()
    val crossPool               by viewModel.crossRandomPool.collectAsState()

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

    // ── 动效系统 offset ───────────────────────────────────────────────────────
    val effectSystemOffset by animateDpAsState(
        targetValue = if (isEffectSystemOpen) 0.dp else 1200.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "effectSystem"
    )

    // ── 粒子动画帧循环 ────────────────────────────────────────────────────────
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
                            val nextVy = when (p.type) {
                                "balloon", "butterfly" -> p.vy - 0.4f * dt
                                "money", "confetti"    -> p.vy + 0.15f * dt
                                else                   -> p.vy + 3.5f * dt
                            }
                            val drift = when (p.type) {
                                "balloon", "butterfly", "money", "confetti" ->
                                    Math.sin(nextLife * 12.0 + p.id).toFloat() * 1.5f
                                else -> 0f
                            }
                            iterator.set(
                                p.copy(
                                    x     = p.x + (p.vx + drift) * 100f * dt,
                                    y     = p.y + nextVy * 100f * dt,
                                    vy    = nextVy,
                                    life  = nextLife,
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

    // ── ViewModel 状态订阅 ────────────────────────────────────────────────────
    val wallpaperName   by viewModel.wallpaperName.collectAsState()
    val clockStyle      by viewModel.clockStyle.collectAsState()
    val dockPackages    by viewModel.dockPackages.collectAsState()
    val appList         by viewModel.appList.collectAsState()
    val showLabels      by viewModel.showLabels.collectAsState()
    val iconPackFilter  by viewModel.iconPackFilter.collectAsState()
    val homePages       by viewModel.homePages.collectAsState()
    val activePageIndex by viewModel.activePageIndex.collectAsState()

    var dragSourcePageIndex by remember { mutableStateOf(0) }

    // ── 面板滑动偏移动画 ──────────────────────────────────────────────────────
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

    val config      = LocalConfiguration.current
    val screenWidth = config.screenWidthDp
    val screenHeight = config.screenHeightDp

    // ── 全局拖拽落点处理 ──────────────────────────────────────────────────────
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
                        droppedOnThumbnailIndex = index; break
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
                    ) { droppedOnPage = idx; break }
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
                        ) { droppedOnDrawerIndex = globalIdx; break }
                    }
                    if (droppedOnDrawerIndex == null) {
                        var minDistance = Float.MAX_VALUE
                        var closestIdx: Int? = null
                        for ((globalIdx, rect) in viewModel.drawerItemBounds) {
                            val cx = (rect.left + rect.right) / 2f
                            val cy = (rect.top + rect.bottom) / 2f
                            val dist = (dropX - cx) * (dropX - cx) + (dropY - cy) * (dropY - cy)
                            if (dist < minDistance) { minDistance = dist; closestIdx = globalIdx }
                        }
                        if (minDistance < 20000f) droppedOnDrawerIndex = closestIdx
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
                            if (viewModel.dragSourceIndex in currentDockList.indices)
                                currentDockList.removeAt(viewModel.dragSourceIndex)
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
                        val itemsCount      = currentDockList.size
                        val dockMaxWidth    = 500f
                        val dockActualWidthDp = screenWidth.coerceAtMost(dockMaxWidth.toInt()) - 24f
                        val dockStartX      = (screenWidth - dockActualWidthDp) / 2f
                        val activeStartX    = dockStartX + 8f
                        val activeWidth     = dockActualWidthDp - 16f
                        val cellWidthDp     = if (itemsCount > 0) activeWidth / itemsCount else 68f
                        val targetIndex     = if (itemsCount > 0)
                            ((dropX - activeStartX) / cellWidthDp).toInt().coerceIn(0, itemsCount)
                        else 0
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
                            ) { targetSlotIndex = cellIdx; break }
                        }
                        if (targetSlotIndex == null) {
                            var minDistance = Float.MAX_VALUE
                            var closestIdx: Int? = null
                            for ((cellIdx, rect) in viewModel.homeGridBounds) {
                                val cx = (rect.left + rect.right) / 2f
                                val cy = (rect.top + rect.bottom) / 2f
                                val dist = (dropX - cx) * (dropX - cx) + (dropY - cy) * (dropY - cy)
                                if (dist < minDistance) { minDistance = dist; closestIdx = cellIdx }
                            }
                            if (minDistance < 25000f) targetSlotIndex = closestIdx
                        }
                        val currentActivePageIdx = viewModel.activePageIndex.value
                        val isCrossPage = !viewModel.isDraggingFromDock &&
                                currentActivePageIdx != dragSourcePageIndex

                        if (targetSlotIndex != null) {
                            if (viewModel.isDraggingFromDock) {
                                val currentDockList = dockPackages.toMutableList()
                                if (viewModel.dragSourceIndex in currentDockList.indices)
                                    currentDockList.removeAt(viewModel.dragSourceIndex)
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
                                    if (viewModel.dragSourceIndex in currentDockList.indices)
                                        currentDockList.removeAt(viewModel.dragSourceIndex)
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

    // ── BackHandler ───────────────────────────────────────────────────────────
    val preUninstallApp by viewModel.preUninstallApp.collectAsState()
    BackHandler(
        enabled = isAppDrawerOpen || isSettingsOpen || isGesturePanelOpen ||
                preUninstallApp != null || viewModel.isEditingHomeScreen ||
                isAddScreenOpen || isEffectSystemOpen
    ) {
        when {
            isEffectSystemOpen -> isEffectSystemOpen = false
            isAddScreenOpen    -> isAddScreenOpen = false
            viewModel.preUninstallApp.value != null -> viewModel.preUninstallApp.value = null
            viewModel.isEditingHomeScreen -> viewModel.isEditingHomeScreen = false
            isSettingsOpen     -> isSettingsOpen = false
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

    // ── 根容器 ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 触摸粒子生成
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
            // 全局拖拽坐标跟踪
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        if (isAddScreenOpen) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            continue
                        }
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (viewModel.isDraggingActive) {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                val d = density.density
                                val sw = context.resources.displayMetrics.widthPixels / d
                                val newX = change.position.x / d
                                val newY = change.position.y / d

                                if (viewModel.dragDistance == -1f) {
                                    viewModel.dragDistance = 0f
                                    viewModel.dragOffset = androidx.compose.ui.geometry.Offset(newX, newY)
                                } else {
                                    val oldOffset = viewModel.dragOffset
                                    if (oldOffset != androidx.compose.ui.geometry.Offset.Zero) {
                                        viewModel.dragDistance += Math.abs(newX - oldOffset.x) + Math.abs(newY - oldOffset.y)
                                    }
                                }
                                viewModel.dragOffset = androidx.compose.ui.geometry.Offset(newX, newY)
                                change.consume()

                                if (viewModel.isDraggingFromDrawer &&
                                    viewModel.draggedApp?.packageName?.startsWith("WIDGET:") != true) {
                                    val itemsPerPage = when (viewModel.drawerGrid.value) {
                                        "5x5" -> 25; else -> 24
                                    }
                                    val displayApps = viewModel.filteredApps.value
                                    val totalPages = Math.max(1, (displayApps.size + itemsPerPage - 1) / itemsPerPage) + 2
                                    viewModel.checkDrawerEdgeScroll(viewModel.dragOffset.x, sw, totalPages)
                                } else {
                                    viewModel.checkHomeEdgeScroll(viewModel.dragOffset.x, sw, homePages.size)
                                }

                                val anyPressed = event.changes.any { it.pressed }
                                if (!anyPressed) {
                                    if (viewModel.dragDistance < 12f) {
                                        if (viewModel.isDraggingFromDrawer)
                                            viewModel.preUninstallApp.value = viewModel.draggedApp
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

        // 壁纸背景
        LauncherBackground(wallpaperName = wallpaperName)

        // ── 主屏幕区域（含手势检测、翻页过渡、图标网格） ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            if (!viewModel.isDraggingActive) isGesturePanelOpen = true
                        }
                    )
                }
                // 上滑触发手势面板
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
                                if (dragY < -100f &&
                                    kotlin.math.abs(dragY) > kotlin.math.abs(dragX) * 1.8f &&
                                    !isTriggered
                                ) {
                                    isGesturePanelOpen = true
                                    isTriggered = true
                                    pointerChange.consume()
                                }
                                if (isTriggered) pointerChange.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                // 跨屏过渡动画（主屏方向）
                .graphicsLayer {
                    val progress = crossProgress
                    alpha = 1f - progress
                    cameraDistance = 8f * this.density
                    if (progress > 0f) {
                        when (activeCrossType) {
                            "默认"  -> alpha = 1f
                            "内缩放" -> { val s = 1f + progress * 0.4f; scaleX = s; scaleY = s }
                            "外缩放" -> { val s = 1f - progress * 0.4f; scaleX = s; scaleY = s }
                            "风车"  -> { val s = 1f - progress; scaleX = s; scaleY = s; rotationZ = -(progress * 180f) }
                            "电视机" -> {
                                val tv = 1f - progress
                                scaleX = if (tv > 0.5f) 1f else tv * 2f
                                scaleY = if (tv < 0.5f) 0.05f else (tv - 0.5f) * 2f
                            }
                        }
                    } else { scaleX = 1f; scaleY = 1f; rotationZ = 0f }
                }
        ) {
            val pagerState = rememberPagerState(
                initialPage = activePageIndex.coerceIn(0, maxOf(1, homePages.size) - 1),
                pageCount   = { homePages.size }
            )

            LaunchedEffect(pagerState.currentPage) {
                if (viewModel.activePageIndex.value != pagerState.currentPage)
                    viewModel.activePageIndex.value = pagerState.currentPage
            }
            LaunchedEffect(activePageIndex) {
                val bounded = activePageIndex.coerceIn(0, homePages.size - 1)
                if (pagerState.currentPage != bounded && homePages.isNotEmpty())
                    pagerState.animateScrollToPage(bounded)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 140.dp)
            ) {
                HorizontalPager(
                    state    = pagerState,
                    modifier = Modifier.weight(1f)
                ) { idx ->
                    val page       = homePages.getOrNull(idx) ?: HomeScreenPage(idx.toString())
                    val pageOffset = (pagerState.currentPage - idx) + pagerState.currentPageOffsetFraction
                    val selectedHomeTransition by viewModel.homeTransition.collectAsState()
                    val homePool               by viewModel.homeRandomPool.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .pagerTransition(
                                pageOffset = pageOffset,
                                effect     = selectedHomeTransition,
                                randomPool = homePool,
                                pageIndex  = idx
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        if (idx == 0) {
                            LauncherClock(
                                clockStyle = clockStyle,
                                themeColor = themeColor,
                                viewModel  = viewModel,
                                modifier   = Modifier.padding(top = 40.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(30.dp))
                        }

                        // 页面小组件
                        page.widgets.forEach { widget ->
                            var showMenu by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(widget) {
                                        detectTapGestures(onLongPress = { showMenu = true })
                                    }
                            ) {
                                LauncherCustomWidgets(
                                    widgetType = widget,
                                    themeColor = themeColor,
                                    viewModel  = viewModel
                                )
                                DropdownMenu(
                                    expanded          = showMenu,
                                    onDismissRequest  = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text    = { Text("从桌面页面移除此组件") },
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

                        // 图标网格（提取到 HomeScreenComponents.kt）
                        HomePageGrid(
                            pageIndex                = idx,
                            page                     = page,
                            appList                  = appList,
                            showLabels               = showLabels,
                            iconPackFilter           = iconPackFilter,
                            themeColor               = themeColor,
                            viewModel                = viewModel,
                            dragSourcePageIndexRef   = { dragSourcePageIndex },
                            onDragSourcePageIndexChange = { dragSourcePageIndex = it },
                            showToast                = showToast
                        )
                    }
                }
            }

            // 页码指示点
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
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        homePages.forEachIndexed { idx, _ ->
                            val isCurrent = idx == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(if (isCurrent) 8.dp else 6.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) themeColor
                                        else Color.White.copy(alpha = 0.40f)
                                    )
                            )
                        }
                    }
                }
            }
        } // end 主屏幕 Box

        // ── Dock 栏 ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp, start = 12.dp, end = 12.dp)
                .graphicsLayer {
                    val progress = crossProgress
                    alpha = 1f - progress
                    cameraDistance = 8f * this.density
                    if (progress > 0f) {
                        when (activeCrossType) {
                            "默认"  -> alpha = 1f
                            "内缩放" -> { val s = 1f + progress * 0.4f; scaleX = s; scaleY = s }
                            "外缩放" -> { val s = 1f - progress * 0.4f; scaleX = s; scaleY = s }
                            "风车"  -> { val s = 1f - progress; scaleX = s; scaleY = s; rotationZ = -(progress * 180f) }
                            "电视机" -> {
                                val tv = 1f - progress
                                scaleX = if (tv > 0.5f) 1f else tv * 2f
                                scaleY = if (tv < 0.5f) 0.05f else (tv - 0.5f) * 2f
                            }
                        }
                    } else { scaleX = 1f; scaleY = 1f; rotationZ = 0f }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            HomeScreenDockBar(
                dockPackages   = dockPackages,
                appList        = appList,
                iconPackFilter = iconPackFilter,
                themeColor     = themeColor,
                viewModel      = viewModel,
                onOpenDrawer   = { isAppDrawerOpen = true },
                showToast      = showToast
            )
        }

        // ── 手势面板 ───────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isGesturePanelOpen,
            enter   = fadeIn() + expandIn(expandFrom = Alignment.Center),
            exit    = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            GestureMenuPanel(
                themeColor        = themeColor,
                viewModel         = viewModel,
                onClose           = { isGesturePanelOpen = false },
                onOpenAddScreen   = { isAddScreenOpen = true },
                onOpenEffectSystem = { isEffectSystemOpen = true },
                onOpenSettings    = { isSettingsOpen = true },
                showToast         = showToast
            )
        }

        // ── App 抽屉 ───────────────────────────────────────────────────────────
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
            val drawerOffsetY = if (isAppDrawerOpen) 0.dp
            else if (isDefaultCross) {
                if (appDrawerOffset >= 1180.dp) 5000.dp else 0.dp
            } else {
                if (crossProgress <= 0.05f) 5000.dp else 0.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = drawerOffsetY)
                    .graphicsLayer {
                        cameraDistance = 8f * this.density
                        if (isDefaultCross) {
                            translationY = appDrawerOffset.toPx()
                            alpha        = drawerAlpha
                        } else {
                            alpha = crossProgress
                            if (crossProgress > 0f) {
                                when (activeCrossType) {
                                    "内缩放" -> { val s = 0.6f + crossProgress * 0.4f; scaleX = s; scaleY = s }
                                    "外缩放" -> { val s = 1.4f - crossProgress * 0.4f; scaleX = s; scaleY = s }
                                    "风车"  -> { val s = crossProgress; scaleX = s; scaleY = s; rotationZ = (1f - crossProgress) * 180f }
                                    "电视机" -> {
                                        scaleX = if (crossProgress > 0.5f) 1f else crossProgress * 2f
                                        scaleY = if (crossProgress < 0.5f) 0.05f else (crossProgress - 0.5f) * 2f
                                    }
                                }
                            } else { scaleX = 1f; scaleY = 1f; rotationZ = 0f }
                        }
                    }
            ) {
                LauncherAppDrawer(
                    viewModel       = viewModel,
                    themeColor      = themeColor,
                    onClose         = { isAppDrawerOpen = false },
                    showToast       = showToast,
                    pinnedWidgets   = homePages.getOrNull(activePageIndex)?.widgets?.toSet() ?: emptySet(),
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

        // ── 设置面板 ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = settingsOffset)
        ) {
            LauncherSettingsPanel(
                viewModel  = viewModel,
                themeColor = themeColor,
                onClose    = { isSettingsOpen = false },
                showToast  = showToast
            )
        }

        // ── 编辑模式顶部删除/卸载工具栏 ───────────────────────────────────────
        AnimatedVisibility(
            visible  = viewModel.isEditingHomeScreen,
            enter    = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit     = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val dropX = viewModel.dragOffset.x
            val dropY = viewModel.dragOffset.y
            val isOverLeftDelete    = dropY <= 100 && dropX < screenWidth / 2f && viewModel.isDraggingActive
            val isOverRightUninstall = dropY <= 100 && dropX >= screenWidth / 2f && viewModel.isDraggingActive

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp)),
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF21C1C1E)),
                border = BorderStroke(1.dp, Color(0x2BFFFFFF))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f).fillMaxHeight()
                            .background(if (isOverLeftDelete) Color(0x3DF44336) else Color.Transparent)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment   = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector     = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint   = if (isOverLeftDelete) Color(0xFFEF5350) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text  = "删除图标",
                                color = if (isOverLeftDelete) Color(0xFFEF5350) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight().width(1.dp)
                            .background(Color(0x1BFFFFFF))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f).fillMaxHeight()
                            .background(if (isOverRightUninstall) Color(0x3DF44336) else Color.Transparent)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment   = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector     = Icons.Default.Cancel,
                                contentDescription = "Uninstall",
                                tint   = if (isOverRightUninstall) Color(0xFFEF5350) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text  = "卸载应用",
                                color = if (isOverRightUninstall) Color(0xFFEF5350) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── 城市选择对话框 ────────────────────────────────────────────────────
        if (viewModel.showCitySelectorDialog) {
            CitySelectorDialog(
                viewModel  = viewModel,
                themeColor = themeColor,
                onClose    = { viewModel.showCitySelectorDialog = false }
            )
        }

        // ── 添加/管理屏幕 ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isAddScreenOpen,
            enter   = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit    = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            AddManagementScreen(
                viewModel  = viewModel,
                themeColor = themeColor,
                onClose    = { isAddScreenOpen = false },
                showToast  = showToast,
                onDrop     = handleGlobalDrop
            )
        }

        // ── 粒子画布 ──────────────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize().alpha(1f)) {
            val drawScope: androidx.compose.ui.graphics.drawscope.DrawScope = this
            globalParticles.forEach { p -> drawParticleOnCanvas(p, drawScope) }
        }

        // ── 动效系统面板 ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = effectSystemOffset)
        ) {
            LauncherAnimationCenter(
                viewModel  = viewModel,
                themeColor = themeColor,
                onClose    = { isEffectSystemOpen = false },
                showToast  = showToast
            )
        }

        // ── 拖拽悬浮层 ────────────────────────────────────────────────────────
        DragOverlay(
            viewModel      = viewModel,
            themeColor     = themeColor,
            iconPackFilter = iconPackFilter,
            isAddScreenOpen = isAddScreenOpen
        )

    } // end 根 Box
}