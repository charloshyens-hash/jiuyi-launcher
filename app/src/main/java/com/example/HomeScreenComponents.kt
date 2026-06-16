package com.example

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// GestureMenuOption  （数据类，供 GestureMenuPanel 使用）
// ─────────────────────────────────────────────────────────────────────────────

data class GestureMenuOption(
    val label: String,
    val icon: ImageVector,
    val action: () -> Unit
)

// ─────────────────────────────────────────────────────────────────────────────
// GestureMenuPanel  —— 长按/上滑弹出的 8 宫格快捷面板
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GestureMenuPanel(
    themeColor: Color,
    viewModel: LauncherViewModel,
    onClose: () -> Unit,
    onOpenAddScreen: () -> Unit,
    onOpenEffectSystem: () -> Unit,
    onOpenSettings: () -> Unit,
    showToast: (String) -> Unit
) {
    val context = LocalContext.current
    val iconPackFilter by viewModel.iconPackFilter.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEC131313)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
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
                        onClose()
                        onOpenAddScreen()
                    },
                    GestureMenuOption("换特效", Icons.Default.AutoAwesome) {
                        onClose()
                        onOpenEffectSystem()
                        showToast("Launcher 动效引擎已开启")
                    },
                    GestureMenuOption("快速美化", Icons.Default.Brush) {
                        onClose()
                        val list = listOf("Minimalist", "Vintage Pixel", "Sketch Outline", "Raw Native")
                        val nextIdx = (list.indexOf(iconPackFilter) + 1) % list.size
                        viewModel.updateIconPackFilter(list[nextIdx])
                        showToast("桌面图标样式切换为: ${list[nextIdx]}")
                    },
                    GestureMenuOption("系统设置", Icons.Default.Settings) {
                        onClose()
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            onOpenSettings()
                        }
                    },
                    GestureMenuOption("个性主题", Icons.Default.Palette) {
                        onClose()
                        onOpenSettings()
                    },
                    GestureMenuOption("个人中心", Icons.Default.Person) {
                        onClose()
                        showToast("久以智能桌面 • 用户中心 v2.0")
                    },
                    GestureMenuOption("屏幕预览", Icons.Default.Visibility) {
                        onClose()
                        onOpenAddScreen()
                        showToast("已开启桌面屏幕多页管理预览")
                    },
                    GestureMenuOption("桌面设置", Icons.Default.GridView) {
                        onClose()
                        onOpenSettings()
                    }
                )

                for (i in 0 until 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
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

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreenDockBar  —— 底部 Dock 栏
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreenDockBar(
    dockPackages: List<String>,
    appList: List<AppModel>,
    iconPackFilter: String,
    themeColor: Color,
    viewModel: LauncherViewModel,
    onOpenDrawer: () -> Unit,
    showToast: (String) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 500.dp)
            .height(82.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xB3131313)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF))
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
                var itemScreenX by remember { mutableStateOf(0f) }
                var itemScreenY by remember { mutableStateOf(0f) }
                val isBeingDragged = viewModel.isDraggingActive &&
                        viewModel.isDraggingFromDock &&
                        viewModel.dragSourceIndex == index

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { bounds ->
                            val coords = bounds.positionInWindow()
                            itemScreenX = coords.x / density
                            itemScreenY = coords.y / density
                        }
                        .then(
                            if (isBeingDragged) Modifier.graphicsLayer { alpha = 0.05f }
                            else Modifier
                        )
                        .pointerInput(pkg) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    if (pkg == "MENU_BUTTON") {
                                        viewModel.draggedApp = AppModel("控制面板", "MENU_BUTTON", "")
                                    } else {
                                        viewModel.draggedApp = appList.firstOrNull { it.packageName == pkg }
                                            ?: AppModel("应用", pkg, "")
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
                                onDrag = { change, _ -> change.consume() }
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
                                .clickable { onOpenDrawer() },
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
                                    linkedApp?.launch(
                                        androidx.compose.ui.platform.LocalContext.current.also {}
                                    ) ?: showToast("未绑定或包已被卸载")
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

// ─────────────────────────────────────────────────────────────────────────────
// HomePageGrid  —— 单页桌面图标网格（4×6，共 24 格）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomePageGrid(
    pageIndex: Int,
    page: HomeScreenPage,
    appList: List<AppModel>,
    showLabels: Boolean,
    iconPackFilter: String,
    themeColor: Color,
    viewModel: LauncherViewModel,
    dragSourcePageIndexRef: () -> Int,
    onDragSourcePageIndexChange: (Int) -> Unit,
    showToast: (String) -> Unit
) {
    val densityVal = androidx.compose.ui.platform.LocalDensity.current.density

    val pageAppsList = page.apps.toMutableList()
    while (pageAppsList.size < 24) pageAppsList.add("EMPTY")
    if (pageAppsList.size > 24) pageAppsList.subList(24, pageAppsList.size).clear()

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

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { bounds ->
                                val coords = bounds.positionInWindow()
                                val x = coords.x / densityVal
                                val y = coords.y / densityVal
                                val w = bounds.size.width / densityVal
                                val h = bounds.size.height / densityVal
                                viewModel.homeGridBounds = viewModel.homeGridBounds +
                                        (cellIdx to androidx.compose.ui.geometry.Rect(x, y, x + w, y + h))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (app != null) {
                            var showShortCutMenu by remember { mutableStateOf(false) }
                            var itemScreenX by remember { mutableStateOf(0f) }
                            var itemScreenY by remember { mutableStateOf(0f) }
                            val context = LocalContext.current

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
                                            onDragStart = {
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
                                                onDragSourcePageIndexChange(pageIndex)
                                            },
                                            onDragEnd = {},
                                            onDragCancel = {},
                                            onDrag = { change, _ ->
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
                                                viewModel.removeAppFromPage(pageIndex, app.packageName)
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

// ─────────────────────────────────────────────────────────────────────────────
// DragOverlay  —— 拖拽时的悬浮图标 + 底部 Dock 区蒙层提示
// ─────────────────────────────────────────────────────────────────────────────

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
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
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