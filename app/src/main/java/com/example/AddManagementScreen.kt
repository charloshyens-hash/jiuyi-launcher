package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddManagementScreen(
    viewModel: LauncherViewModel,
    themeColor: Color,
    onClose: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    val appList by viewModel.appList.collectAsState()
    val homePages by viewModel.homePages.collectAsState()
    val activePageIndex by viewModel.activePageIndex.collectAsState()
    val showLabels by viewModel.showLabels.collectAsState()
    val iconPackFilter by viewModel.iconPackFilter.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var thumbnailGroupIndex by remember { mutableIntStateOf(0) }
    val maxThumbnailsPerGroup = 3
    val totalGroups = (homePages.size + maxThumbnailsPerGroup - 1) / maxThumbnailsPerGroup

    var thumbnailBounds by remember { mutableStateOf(emptyMap<Int, RectBounds>()) }

    // ── 直接读取 ViewModel 的 mutableStateOf 属性，让 Compose 自动追踪重组 ──
    // 不要用 remember { derivedStateOf { ... } } 包裹，否则无法建立订阅
    val isDragging = viewModel.isDraggingActive
    val dragOffset = viewModel.dragOffset
    val draggedApp = viewModel.draggedApp

    val handleDragEnd: () -> Unit = {
        val dragged = viewModel.draggedApp
        if (dragged != null) {
            val offset = viewModel.dragOffset
            var droppedOnThumbnailIndex: Int? = null

            for ((index, rect) in thumbnailBounds) {
                if (offset.x >= rect.left && offset.x <= rect.right &&
                    offset.y >= rect.top && offset.y <= rect.bottom
                ) {
                    droppedOnThumbnailIndex = index
                    break
                }
            }

            if (droppedOnThumbnailIndex != null) {
                if (dragged.packageName.startsWith("WIDGET:")) {
                    val widgetName = dragged.packageName.substring(7)
                    viewModel.addWidgetToPage(droppedOnThumbnailIndex, widgetName)
                    showToast("已添加 $widgetName 到第 ${droppedOnThumbnailIndex + 1} 页")
                } else {
                    viewModel.addAppToPage(droppedOnThumbnailIndex, dragged.packageName)
                    showToast("已添加快捷方式 ${dragged.label} 到第 ${droppedOnThumbnailIndex + 1} 页")
                }
            } else {
                if (dragged.packageName.startsWith("WIDGET:")) {
                    val widgetName = dragged.packageName.substring(7)
                    viewModel.addWidgetToPage(activePageIndex, widgetName)
                    showToast("已自动添加 $widgetName 到当前页")
                } else {
                    viewModel.addAppToPage(activePageIndex, dragged.packageName)
                    showToast("已自动添加快捷方式 ${dragged.label} 到当前页")
                }
            }
        }
        viewModel.draggedApp = null
        viewModel.isDraggingActive = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE6131313))
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
    ) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ── 上方 7/9 区域 ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(7f)
                    .fillMaxWidth()
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        text = "久以桌面个性管理",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showToast("点击快捷添加或长按图标拖拽到下方目标卡片") }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "提示", tint = Color.White.copy(alpha = 0.5f))
                    }
                }

                // Tab 栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf("应用程序", "小组件", "我的手机").forEachIndexed { idx, name ->
                        val isSelected = selectedTab == idx
                        Column(
                            modifier = Modifier
                                .clickable { selectedTab = idx }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) themeColor else Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .width(32.dp)
                                    .background(if (isSelected) themeColor else Color.Transparent)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        // ── Tab 0: 应用程序 ──────────────────────────────
                        0 -> {
                            val itemsPerPage = 16
                            val totalAppPages = maxOf(1, (appList.size + itemsPerPage - 1) / itemsPerPage)
                            val appPagerState = rememberPagerState(pageCount = { totalAppPages })

                            Column(modifier = Modifier.fillMaxSize()) {
                                HorizontalPager(
                                    state = appPagerState,
                                    modifier = Modifier.weight(1f)
                                ) { pageIdx ->
                                    val startIdx = pageIdx * itemsPerPage
                                    val endIdx = minOf(startIdx + itemsPerPage, appList.size)
                                    val pageApps = appList.subList(startIdx, endIdx)

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(4),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(pageApps) { _, app ->
                                            var itemScreenX by remember { mutableStateOf(0f) }
                                            var itemScreenY by remember { mutableStateOf(0f) }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(80.dp)
                                                    .onGloballyPositioned { bounds ->
                                                        val coords = bounds.positionInWindow()
                                                        itemScreenX = coords.x / density
                                                        itemScreenY = coords.y / density
                                                    }
                                                    .pointerInput(app) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                viewModel.draggedApp = app
                                                                viewModel.isDraggingActive = true
                                                                viewModel.isDraggingFromDock = false
                                                                viewModel.isDraggingFromDrawer = false
                                                                viewModel.dragSourceIndex = -1
                                                                viewModel.dragDistance = -1f
                                                                viewModel.dragOffset = androidx.compose.ui.geometry.Offset(
                                                                    x = itemScreenX + 24f,
                                                                    y = itemScreenY + 24f
                                                                )
                                                            },
                                                            onDragEnd = { handleDragEnd() },
                                                            onDragCancel = {
                                                                viewModel.draggedApp = null
                                                                viewModel.isDraggingActive = false
                                                            },
                                                            onDrag = { change, amt ->
                                                                change.consume()
                                                                viewModel.dragOffset = viewModel.dragOffset +
                                                                    androidx.compose.ui.geometry.Offset(
                                                                        x = amt.x / density,
                                                                        y = amt.y / density
                                                                    )
                                                            }
                                                        )
                                                    }
                                                    .clickable {
                                                        viewModel.addAppToPage(activePageIndex, app.packageName)
                                                        showToast("添加快捷方式：${app.label}")
                                                    }
                                            ) {
                                                IconStylingCard(
                                                    app = app,
                                                    filter = iconPackFilter,
                                                    themeColor = themeColor,
                                                    modifier = Modifier.size(44.dp)
                                                )
                                                if (showLabels) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = app.label,
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (totalAppPages > 1) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        repeat(totalAppPages) { idx ->
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .padding(horizontal = 2.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (appPagerState.currentPage == idx) themeColor
                                                        else Color.White.copy(alpha = 0.2f)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Tab 1: 小组件 ────────────────────────────────
                        1 -> {
                            val widgetPresets = listOf("RAM Booster", "Music Cassette", "Quick Tasks", "Power Battery")
                            val widgetScroll = rememberScrollState()

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(widgetScroll)
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                widgetPresets.forEach { widget ->
                                    var itemScreenX by remember { mutableStateOf(0f) }
                                    var itemScreenY by remember { mutableStateOf(0f) }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { bounds ->
                                                val coords = bounds.positionInWindow()
                                                itemScreenX = coords.x / density
                                                itemScreenY = coords.y / density
                                            }
                                            .pointerInput(widget) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        viewModel.draggedApp = AppModel(widget, "WIDGET:$widget", "")
                                                        viewModel.isDraggingActive = true
                                                        viewModel.isDraggingFromDock = false
                                                        viewModel.isDraggingFromDrawer = false
                                                        viewModel.dragSourceIndex = -1
                                                        viewModel.dragDistance = -1f
                                                        viewModel.dragOffset = androidx.compose.ui.geometry.Offset(
                                                            x = itemScreenX + 100f,
                                                            y = itemScreenY + 40f
                                                        )
                                                    },
                                                    onDragEnd = { handleDragEnd() },
                                                    onDragCancel = {
                                                        viewModel.draggedApp = null
                                                        viewModel.isDraggingActive = false
                                                    },
                                                    onDrag = { change, amt ->
                                                        change.consume()
                                                        viewModel.dragOffset = viewModel.dragOffset +
                                                            androidx.compose.ui.geometry.Offset(
                                                                x = amt.x / density,
                                                                y = amt.y / density
                                                            )
                                                    }
                                                )
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Widgets, contentDescription = null, tint = themeColor, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(text = widget, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                                IconButton(onClick = {
                                                    viewModel.addWidgetToPage(activePageIndex, widget)
                                                    showToast("已添加 $widget 到当前页")
                                                }) {
                                                    Icon(imageVector = Icons.Default.AddCircle, contentDescription = "添加", tint = themeColor)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "长按此卡片并拖拽到下方目标卡片进行放置，或点击右上角直接置入当前页",
                                                color = Color.White.copy(alpha = 0.45f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Tab 2: 我的手机 ──────────────────────────────
                        2 -> {
                            val utilsList = listOf(
                                SystemUtilEntrance("电池状态", Icons.Default.BatteryChargingFull, "查看电量电压指标") { showToast("物理电池温度: ${viewModel.batteryTemperature}°C | 电压: ${viewModel.batteryVoltage}V") },
                                SystemUtilEntrance("存储管理", Icons.Default.Storage, "清理垃圾缓存文件") { showToast("系统可用存储: ${String.format("%.1f", viewModel.realFreeStorageGb)} GB / ${String.format("%.1f", viewModel.realTotalStorageGb)} GB") },
                                SystemUtilEntrance("网络速率", Icons.Default.Wifi, "测试通信延迟丢包") { showToast("实时基站连接状态正常，测速延迟: ${viewModel.networkPingMs}ms") },
                                SystemUtilEntrance("一键加速", Icons.Default.Cyclone, "彻底释放运行内存") { viewModel.boostRam() },
                                SystemUtilEntrance("文件管理器", Icons.Default.FolderOpen, "组织并管理历史目录") { showToast("正在打开本地资源管家...") },
                                SystemUtilEntrance("桌面设置", Icons.Default.Settings, "配置指示器与图标样式") { showToast("请关闭此卡片返回并点击轻手势「设置」") },
                                SystemUtilEntrance("个性主题", Icons.Default.Palette, "全局变色与壁纸匹配") { showToast("个性设置中心已开启，可在桌面设置切换") }
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(utilsList) { _, util ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { util.action() },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(themeColor.copy(alpha = 0.15f), shape = CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(imageVector = util.icon, contentDescription = null, tint = themeColor, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(text = util.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(text = util.desc, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 下方 2/9 区域：主屏幕缩略图目标区 ────────────────────────
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isDragging) "↓  松手放置到目标主屏幕  ↓" else "主屏幕预览及管理 • 多组滑动翻页",
                    color = if (isDragging) themeColor else Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        if (thumbnailGroupIndex > 0) {
                            IconButton(onClick = { thumbnailGroupIndex-- }) {
                                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "左翻", tint = Color.White)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val startIdx = thumbnailGroupIndex * maxThumbnailsPerGroup
                        val endIdx = minOf(startIdx + maxThumbnailsPerGroup, homePages.size)

                        for (idx in startIdx until endIdx) {
                            val page = homePages[idx]
                            val isCurrentPage = idx == activePageIndex

                            // 判断当前拖拽是否悬停在此缩略图上
                            val isHovered = isDragging && (thumbnailBounds[idx]?.let { rect ->
                                dragOffset.x >= rect.left && dragOffset.x <= rect.right &&
                                        dragOffset.y >= rect.top && dragOffset.y <= rect.bottom
                            } ?: false)

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .scale(if (isHovered) 1.06f else 1f)
                                    .onGloballyPositioned { bounds ->
                                        val coords = bounds.positionInWindow()
                                        val left = coords.x / density
                                        val top = coords.y / density
                                        val w = bounds.size.width / density
                                        val h = bounds.size.height / density
                                        thumbnailBounds = thumbnailBounds + (idx to RectBounds(left, top, left + w, top + h))
                                    }
                                    .clickable {
                                        viewModel.activePageIndex.value = idx
                                        showToast("已切换到主页 ${idx + 1}")
                                    }
                                    .border(
                                        width = if (isHovered || isCurrentPage) 2.dp else 1.dp,
                                        color = when {
                                            isHovered -> themeColor
                                            isCurrentPage -> themeColor
                                            else -> Color.White.copy(alpha = 0.15f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isHovered -> themeColor.copy(alpha = 0.28f)
                                        isCurrentPage -> themeColor.copy(alpha = 0.18f)
                                        else -> Color(0xFF1E1E1E)
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val realAppsCount = page.apps.count { it != "EMPTY" && it.isNotEmpty() }

                                        Text(
                                            text = if (idx == 0) "主屏幕 1" else "主屏幕 ${idx + 1}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (isHovered) {
                                            Icon(
                                                imageVector = Icons.Default.AddCircle,
                                                contentDescription = null,
                                                tint = themeColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        } else {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (idx == 0) Icon(imageVector = Icons.Default.WatchLater, contentDescription = null, tint = themeColor, modifier = Modifier.size(10.dp))
                                                if (page.widgets.isNotEmpty()) {
                                                    Icon(imageVector = Icons.Default.Widgets, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(10.dp))
                                                    Text(text = "${page.widgets.size}", color = Color.Gray, fontSize = 8.sp)
                                                }
                                                if (realAppsCount > 0) {
                                                    Icon(imageVector = Icons.Default.Apps, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(10.dp))
                                                    Text(text = "$realAppsCount", color = Color.Gray, fontSize = 8.sp)
                                                }
                                                if (idx > 0 && realAppsCount == 0 && page.widgets.isEmpty()) {
                                                    Text(text = "空白页", color = Color.Gray, fontSize = 8.sp)
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (idx > 0) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowLeft,
                                                    contentDescription = "左移",
                                                    tint = Color.White.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp).clickable {
                                                        viewModel.reorderHomePage(idx, idx - 1)
                                                        showToast("主屏幕已向前移动")
                                                    }
                                                )
                                            }
                                            if (idx > 0 && realAppsCount == 0 && page.widgets.isEmpty()) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    tint = Color.Red.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(14.dp).clickable {
                                                        viewModel.deleteHomePage(idx)
                                                        showToast("已成功删除空白页")
                                                    }
                                                )
                                            }
                                            if (idx < homePages.size - 1) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowRight,
                                                    contentDescription = "右移",
                                                    tint = Color.White.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp).clickable {
                                                        viewModel.reorderHomePage(idx, idx + 1)
                                                        showToast("主屏幕已向后移动")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val isLastGroup = (thumbnailGroupIndex + 1) == totalGroups || totalGroups == 0
                        if (isLastGroup && homePages.size < 20) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .clickable {
                                        viewModel.addHomePage()
                                        showToast("已成功创建新主屏幕页")
                                    },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "新增", tint = themeColor, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "添加空白页", color = Color.Gray, fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        if ((thumbnailGroupIndex + 1) < totalGroups) {
                            IconButton(onClick = { thumbnailGroupIndex++ }) {
                                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "右翻", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // ── 拖拽浮层 Ghost Icon（zIndex 最高，叠加在一切之上）──────────────
        // isDragging / dragOffset / draggedApp 均为 viewModel 的 mutableStateOf，
        // 在 Composable 函数体内直接读取即可触发重组，无需 remember/derivedStateOf
        if (isDragging) {
            val densityPx = LocalDensity.current.density
            Box(
                modifier = Modifier
                    .zIndex(999f)
                    .offset {
                        IntOffset(
                            x = (dragOffset.x * densityPx - 28 * densityPx).roundToInt(),
                            y = (dragOffset.y * densityPx - 28 * densityPx).roundToInt()
                        )
                    }
                    .size(56.dp)
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .background(Color(0xCC1A1A1A), RoundedCornerShape(16.dp))
                    .border(1.5.dp, themeColor.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .scale(1.15f),
                contentAlignment = Alignment.Center
            ) {
                if (draggedApp != null) {
                    if (draggedApp.packageName.startsWith("WIDGET:")) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        IconStylingCard(
                            app = draggedApp,
                            filter = iconPackFilter,
                            themeColor = themeColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class SystemUtilEntrance(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val desc: String,
    val action: () -> Unit
)