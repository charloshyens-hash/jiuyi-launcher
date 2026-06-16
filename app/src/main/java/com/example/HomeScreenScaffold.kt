package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenScaffold(
    viewModel: LauncherViewModel,
    themeColor: Color,
    isAppDrawerOpen: Boolean,
    isSettingsOpen: Boolean,
    isGesturePanelOpen: Boolean,
    isAddScreenOpen: Boolean,
    isEffectSystemOpen: Boolean,
    globalParticles: androidx.compose.runtime.snapshots.SnapshotStateList<TouchParticle>,
    selectedTouchEffect: String,
    touchPool: String,
    crossProgress: Float,
    activeCrossType: String,
    effectSystemOffset: Dp,
    appDrawerOffset: Dp,
    settingsOffset: Dp,
    wallpaperName: String,
    clockStyle: String,
    dockPackages: List<String>,
    appList: List<AppModel>,
    showLabels: Boolean,
    iconPackFilter: String,
    homePages: List<HomeScreenPage>,
    activePageIndex: Int,
    showToast: (String) -> Unit,
    screenWidth: Int,
    screenHeight: Int,
    onDragSourcePageChange: (Int) -> Unit,
    handleGlobalDrop: () -> Unit,
    onAppDrawerOpen: () -> Unit,
    onAppDrawerClose: () -> Unit,
    onSettingsOpen: () -> Unit,
    onGesturePanelOpen: () -> Unit,
    onGesturePanelClose: () -> Unit,
    onAddScreenOpen: () -> Unit,
    onAddScreenClose: () -> Unit,
    onEffectSystemOpen: () -> Unit,
    onEffectSystemClose: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // ── 跨屏动画 graphicsLayer lambda（主屏与 Dock 共用）──────────────────
    val homeLayerBlock: GraphicsLayerScope.() -> Unit = {
        val progress = crossProgress
        alpha = (1f - progress)
        cameraDistance = 8f * this.density
        if (progress > 0f) {
            when (activeCrossType) {
                "默认" -> alpha = 1f
                "内缩放" -> { val s = 1f + progress * 0.4f; scaleX = s; scaleY = s }
                "外缩放" -> { val s = 1f - progress * 0.4f; scaleX = s; scaleY = s }
                "风车" -> { val s = 1f - progress; scaleX = s; scaleY = s; rotationZ = -(progress * 180f) }
                "电视机" -> {
                    val tp = 1f - progress
                    scaleX = if (tp > 0.5f) 1f else tp * 2f
                    scaleY = if (tp < 0.5f) 0.05f else (tp - 0.5f) * 2f
                }
            }
        } else { scaleX = 1f; scaleY = 1f; rotationZ = 0f }
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
                                        val dx = newX - oldOffset.x
                                        val dy = newY - oldOffset.y
                                        viewModel.dragDistance += Math.abs(dx) + Math.abs(dy)
                                    }
                                }
                                viewModel.dragOffset = androidx.compose.ui.geometry.Offset(newX, newY)
                                change.consume()

                                if (viewModel.isDraggingFromDrawer && viewModel.draggedApp?.packageName?.startsWith("WIDGET:") != true) {
                                    val itemsPerPage = when (viewModel.drawerGrid.value) { "5x5" -> 25; else -> 24 }
                                    val displayApps = viewModel.filteredApps.value
                                    val totalPages = Math.max(1, (displayApps.size + itemsPerPage - 1) / itemsPerPage) + 2
                                    viewModel.checkDrawerEdgeScroll(viewModel.dragOffset.x, sw, totalPages)
                                } else {
                                    viewModel.checkHomeEdgeScroll(viewModel.dragOffset.x, sw, homePages.size)
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

        // ── 主屏幕页面区域 ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        if (!viewModel.isDraggingActive) onGesturePanelOpen()
                    })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var isTriggered = false
                        do {
                            val event = awaitPointerEvent()
                            val pc = event.changes.firstOrNull()
                            if (pc != null && !viewModel.isDraggingActive) {
                                val dragX = pc.position.x - startX
                                val dragY = pc.position.y - startY
                                if (dragY < -100f && kotlin.math.abs(dragY) > kotlin.math.abs(dragX) * 1.8f && !isTriggered) {
                                    onGesturePanelOpen()
                                    isTriggered = true
                                    pc.consume()
                                }
                                if (isTriggered) pc.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer(homeLayerBlock)
        ) {
            HomeScreenPages(
                viewModel = viewModel,
                themeColor = themeColor,
                homePages = homePages,
                activePageIndex = activePageIndex,
                clockStyle = clockStyle,
                appList = appList,
                showLabels = showLabels,
                iconPackFilter = iconPackFilter,
                showToast = showToast,
                onDragSourcePageChange = onDragSourcePageChange
            )
        }

        // ── Dock 栏 ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp, start = 12.dp, end = 12.dp)
                .graphicsLayer(homeLayerBlock),
            contentAlignment = Alignment.BottomCenter
        ) {
            HomeScreenDock(
                viewModel = viewModel,
                themeColor = themeColor,
                dockPackages = dockPackages,
                appList = appList,
                iconPackFilter = iconPackFilter,
                showToast = showToast,
                onAppDrawerOpen = onAppDrawerOpen
            )
        }

        // ── 手势菜单面板 ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isGesturePanelOpen,
            enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            GesturePanel(
                themeColor = themeColor,
                iconPackFilter = iconPackFilter,
                viewModel = viewModel,
                showToast = showToast,
                onClose = onGesturePanelClose,
                onAddScreenOpen = onAddScreenOpen,
                onEffectSystemOpen = onEffectSystemOpen,
                onSettingsOpen = onSettingsOpen
            )
        }

        // ── App Drawer ────────────────────────────────────────────────────
        val drawerAlpha by animateFloatAsState(
            targetValue = if (viewModel.isDraggingActive) {
                if (viewModel.isDraggingFromDrawer) 1f else 0.15f
            } else 1f,
            label = "drawerAlpha"
        )

        val isDefaultCross = activeCrossType == "默认"
        val showDrawer = isAppDrawerOpen || if (isDefaultCross) appDrawerOffset < 1180.dp else crossProgress > 0.05f

        if (showDrawer) {
            val drawerOffsetY = if (isAppDrawerOpen) 0.dp
            else if (isDefaultCross) { if (appDrawerOffset >= 1180.dp) 5000.dp else 0.dp }
            else { if (crossProgress <= 0.05f) 5000.dp else 0.dp }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = drawerOffsetY)
                    .graphicsLayer {
                        val progress = crossProgress
                        cameraDistance = 8f * this.density
                        if (isDefaultCross) {
                            translationY = appDrawerOffset.toPx()
                            alpha = drawerAlpha
                        } else {
                            alpha = progress
                            if (progress > 0f) {
                                when (activeCrossType) {
                                    "内缩放" -> { val s = 0.6f + progress * 0.4f; scaleX = s; scaleY = s }
                                    "外缩放" -> { val s = 1.4f - progress * 0.4f; scaleX = s; scaleY = s }
                                    "风车" -> { val s = progress; scaleX = s; scaleY = s; rotationZ = (1f - progress) * 180f }
                                    "电视机" -> {
                                        scaleX = if (progress > 0.5f) 1f else progress * 2f
                                        scaleY = if (progress < 0.5f) 0.05f else (progress - 0.5f) * 2f
                                    }
                                }
                            } else { scaleX = 1f; scaleY = 1f; rotationZ = 0f }
                        }
                    }
            ) {
                LauncherAppDrawer(
                    viewModel = viewModel,
                    themeColor = themeColor,
                    onClose = onAppDrawerClose,
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
                        onAppDrawerClose()
                    },
                    onDrop = handleGlobalDrop
                )
            }
        }

        // ── 设置面板 ──────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().offset(y = settingsOffset)) {
            LauncherSettingsPanel(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = { /* onSettingsClose */ },
                showToast = showToast
            )
        }

        // ── 编辑模式顶部删除/卸载 Bar ─────────────────────────────────────
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
                modifier = Modifier.fillMaxWidth().height(82.dp).shadow(16.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Delete, "Delete", tint = if (isOverLeftDelete) Color(0xFFEF5350) else Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("删除图标", color = if (isOverLeftDelete) Color(0xFFEF5350) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0x1BFFFFFF)))
                    Box(
                        modifier = Modifier
                            .weight(1f).fillMaxHeight()
                            .background(if (isOverRightUninstall) Color(0x3DF44336) else Color.Transparent)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Cancel, "Uninstall", tint = if (isOverRightUninstall) Color(0xFFEF5350) else Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("卸载应用", color = if (isOverRightUninstall) Color(0xFFEF5350) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── 城市选择弹窗 ──────────────────────────────────────────────────
        if (viewModel.showCitySelectorDialog) {
            CitySelectorDialog(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = { viewModel.showCitySelectorDialog = false }
            )
        }

        // ── 添加管理屏幕 ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isAddScreenOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            AddManagementScreen(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = onAddScreenClose,
                showToast = showToast,
                onDrop = handleGlobalDrop
            )
        }

        // ── 粒子 Canvas ───────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize().alpha(1f)) {
            val drawScope: DrawScope = this
            globalParticles.forEach { p -> drawParticleOnCanvas(p, drawScope) }
        }

        // ── 动效引擎面板 ──────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().offset(y = effectSystemOffset)) {
            LauncherAnimationCenter(
                viewModel = viewModel,
                themeColor = themeColor,
                onClose = onEffectSystemClose,
                showToast = showToast
            )
        }

        // ── 拖拽浮层 ──────────────────────────────────────────────────────
        DragOverlay(
            viewModel = viewModel,
            themeColor = themeColor,
            iconPackFilter = iconPackFilter,
            isAddScreenOpen = isAddScreenOpen
        )
    }
}