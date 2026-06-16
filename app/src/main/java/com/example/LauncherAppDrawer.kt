package com.example

import android.content.Intent
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppDrawer(
    viewModel: LauncherViewModel,
    themeColor: Color,
    onClose: () -> Unit,
    showToast: (String) -> Unit,
    onPinWidgetToggle: (String) -> Unit,
    pinnedWidgets: Set<String>,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val showLabels by viewModel.showLabels.collectAsState()
    val drawerGrid by viewModel.drawerGrid.collectAsState()
    val iconPackFilter by viewModel.iconPackFilter.collectAsState()
    val drawerPageIndex by viewModel.drawerPageIndex.collectAsState()

    val itemsPerPage = when (drawerGrid) {
        "5x5" -> 25
        else -> 24
    }
    val appPagesCount = Math.max(1, (filteredApps.size + itemsPerPage - 1) / itemsPerPage)
    val totalDrawerPages = appPagesCount + 2

    val pagerState = rememberPagerState(
        initialPage = drawerPageIndex.coerceIn(0, totalDrawerPages - 1),
        pageCount = { totalDrawerPages }
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.drawerPageIndex.value = pagerState.currentPage
    }

    LaunchedEffect(drawerPageIndex) {
        val bounded = drawerPageIndex.coerceIn(0, totalDrawerPages - 1)
        if (pagerState.currentPage != bounded) {
            pagerState.animateScrollToPage(bounded)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.backToFirstScreenEvent.collect {
            pagerState.scrollToPage(0)
        }
    }

    var showMoreMenu by remember { mutableStateOf(false) }
    var renameHideDialogProduct by remember { mutableStateOf<AppModel?>(null) }
    var isSearchDialogOpen by remember { mutableStateOf(false) }
    var isManageHiddenDialogOpen by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSmartCategoryDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showListSettingsDialog by remember { mutableStateOf(false) }
    var activeOpenedFolder by remember { mutableStateOf<DrawerFolder?>(null) }
    var showRenameFolderDialog by remember { mutableStateOf<DrawerFolder?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xEB131313))
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
    ) {
        // ── 顶部工具栏 ─────────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 标签
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf("应用", "小组件", "我的手机")
                        tabs.forEachIndexed { index, title ->
                            val isSelected = when (index) {
                                0 -> pagerState.currentPage < appPagesCount
                                1 -> pagerState.currentPage == appPagesCount
                                else -> pagerState.currentPage == appPagesCount + 1
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clickable {
                                        coroutineScope.launch {
                                            val targetPage = when (index) {
                                                0 -> 0
                                                1 -> appPagesCount
                                                else -> appPagesCount + 1
                                            }
                                            pagerState.animateScrollToPage(targetPage)
                                        }
                                    }
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .height(3.dp)
                                        .width(36.dp)
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(if (isSelected) themeColor else Color.Transparent)
                                )
                            }
                        }
                    }

                    // 右侧按钮组
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isSearchDialogOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索应用",
                                tint = if (searchQuery.isNotEmpty()) themeColor else Color.White
                            )
                        }

                        // ⋮ 菜单 —— Box 包裹 IconButton + DropdownMenu
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(Color(0xFF131313))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("刷新应用程序", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.refreshInstalledApps()
                                        showMoreMenu = false
                                        showToast("应用程序缓存已更新")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null, tint = themeColor) }
                                )
                                DropdownMenuItem(
                                    text = { Text("应用图标排序", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showSortDialog = true
                                        showMoreMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Sort, null, tint = themeColor) }
                                )
                                DropdownMenuItem(
                                    text = { Text("管理隐藏应用", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        isManageHiddenDialogOpen = true
                                        showMoreMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.VisibilityOff, null, tint = themeColor) }
                                )
                                DropdownMenuItem(
                                    text = { Text("应用智能分类", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showSmartCategoryDialog = true
                                        showMoreMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = themeColor) }
                                )
                                DropdownMenuItem(
                                    text = { Text("新建文件夹", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showNewFolderDialog = true
                                        showMoreMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = themeColor) }
                                )
                                DropdownMenuItem(
                                    text = { Text("应用列表设置", color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        showListSettingsDialog = true
                                        showMoreMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, null, tint = themeColor) }
                                )
                            }
                        } // end Box (⋮ 菜单)
                    } // end Row (右侧按钮组)
                } // end Row (整行)
            } // end Box (52dp 工具栏)
        } // end Column (顶部工具栏)

        Spacer(modifier = Modifier.height(12.dp))

        // ── HorizontalPager 主内容区 ────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
            val drawerTransition by viewModel.drawerTransition.collectAsState()
            val drawerPool by viewModel.drawerRandomPool.collectAsState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pagerTransition(
                        pageOffset = pageOffset,
                        effect = drawerTransition,
                        randomPool = drawerPool,
                        pageIndex = pageIndex
                    )
            ) {
                if (pageIndex < appPagesCount) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (searchQuery.isNotEmpty() && pageIndex == 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, null, tint = themeColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "搜索结果: \"$searchQuery\"",
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "清除搜索",
                                    color = themeColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { viewModel.searchQuery.value = "" }
                                )
                            }
                        }
                        SingleAppsPageGrid(
                            viewModel = viewModel,
                            themeColor = themeColor,
                            showLabels = showLabels,
                            gridMode = drawerGrid,
                            displayApps = filteredApps,
                            pageIdx = pageIndex,
                            itemsPerPage = itemsPerPage,
                            iconFilter = iconPackFilter,
                            onAppLongClicked = { renameHideDialogProduct = it },
                            onFolderClicked = { folder -> activeOpenedFolder = folder },
                            onRenameFolderRequested = { folder -> showRenameFolderDialog = folder },
                            onDrop = onDrop
                        )
                    }
                } else if (pageIndex == appPagesCount) {
                    WidgetsDrawerGrid(
                        pinnedWidgets = pinnedWidgets,
                        onPinWidgetToggle = onPinWidgetToggle,
                        themeColor = themeColor,
                        viewModel = viewModel,
                        onDrop = onDrop
                    )
                } else {
                    MyPhoneDrawerDashboard(
                        viewModel = viewModel,
                        themeColor = themeColor
                    )
                }
            }
        } // end HorizontalPager

        // ── 拖拽时底部缩略图导航栏 ──────────────────────────────────────────
        val density = LocalDensity.current.density
        val isDraggingFromDrawer = viewModel.isDraggingActive && viewModel.isDraggingFromDrawer

        AnimatedVisibility(
            visible = isDraggingFromDrawer,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C0C))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "拖动到缩略图以添加至对应主屏幕页 (小组件同理)",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val homePages by viewModel.homePages.collectAsState()
                val activePageIndex by viewModel.activePageIndex.collectAsState()
                var thumbnailGroupIndex by remember { mutableIntStateOf(0) }
                val maxThumbnailsPerGroup = 3
                val totalGroups = (homePages.size + maxThumbnailsPerGroup - 1) / maxThumbnailsPerGroup

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左翻按钮
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        if (thumbnailGroupIndex > 0) {
                            IconButton(onClick = { thumbnailGroupIndex-- }) {
                                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "左翻", tint = Color.White)
                            }
                        }
                    }

                    // 缩略图
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

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(90.dp)
                                    .onGloballyPositioned { bounds ->
                                        val coords = bounds.positionInWindow()
                                        val left = coords.x / density
                                        val top = coords.y / density
                                        val w = bounds.size.width / density
                                        val h = bounds.size.height / density
                                        viewModel.drawerThumbnailBounds = viewModel.drawerThumbnailBounds +
                                            (idx to androidx.compose.ui.geometry.Rect(left, top, left + w, top + h))
                                    }
                                    .border(
                                        width = if (isCurrentPage) 2.5.dp else 1.2.dp,
                                        color = if (isCurrentPage) themeColor else Color.White.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrentPage) themeColor.copy(alpha = 0.82f) else Color(0xFF3A3A3D)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Text(
                                            text = if (idx == 0) "主屏幕 1" else "主屏幕 ${idx + 1}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (idx == 0) {
                                                Icon(
                                                    imageVector = Icons.Default.WatchLater,
                                                    contentDescription = null,
                                                    tint = if (isCurrentPage) Color.White else themeColor,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                            val realAppsCount = page.apps.count { it != "EMPTY" && it.isNotEmpty() }
                                            if (page.widgets.isNotEmpty()) {
                                                Icon(imageVector = Icons.Default.Widgets, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(10.dp))
                                                Text(text = "${page.widgets.size}", color = Color.White.copy(alpha = 0.9f), fontSize = 8.sp)
                                            }
                                            if (realAppsCount > 0) {
                                                Icon(imageVector = Icons.Default.Apps, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(10.dp))
                                                Text(text = "$realAppsCount", color = Color.White.copy(alpha = 0.9f), fontSize = 8.sp)
                                            }
                                            if (idx > 0 && realAppsCount == 0 && page.widgets.isEmpty()) {
                                                Text(text = "空白页", color = Color.White.copy(alpha = 0.65f), fontSize = 8.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 新增页按钮
                        val isLastGroup = (thumbnailGroupIndex + 1) == totalGroups || totalGroups == 0
                        if (isLastGroup && homePages.size < 20) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(90.dp)
                                    .clickable {
                                        viewModel.addHomePage()
                                        showToast("已成功创建新主屏幕页")
                                    },
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "新增", tint = themeColor, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = "添加空白页", color = Color.Gray, fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 右翻按钮
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        val hasMoreOnRight = (thumbnailGroupIndex + 1) < totalGroups
                        if (hasMoreOnRight) {
                            IconButton(onClick = { thumbnailGroupIndex++ }) {
                                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "右翻", tint = Color.White)
                            }
                        }
                    }
                }
            }
        } // end AnimatedVisibility

        // ── 底部导航指示点 ──────────────────────────────────────────────────
        if (!isDraggingFromDrawer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalDrawerPages) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 16.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) themeColor else Color.White.copy(alpha = 0.25f))
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }
            }
        }

        // ── 所有弹窗（拆分至 AppDrawerDialogs.kt）─────────────────────────
        AppDrawerDialogs(
            viewModel = viewModel,
            themeColor = themeColor,
            showToast = showToast,
            isSearchDialogOpen = isSearchDialogOpen,
            onSearchDialogClose = { isSearchDialogOpen = false },
            isManageHiddenDialogOpen = isManageHiddenDialogOpen,
            onManageHiddenClose = { isManageHiddenDialogOpen = false },
            showSortDialog = showSortDialog,
            onSortDialogClose = { showSortDialog = false },
            showSmartCategoryDialog = showSmartCategoryDialog,
            onSmartCategoryClose = { showSmartCategoryDialog = false },
            showNewFolderDialog = showNewFolderDialog,
            onNewFolderClose = { showNewFolderDialog = false },
            showListSettingsDialog = showListSettingsDialog,
            onListSettingsClose = { showListSettingsDialog = false },
            activeOpenedFolder = activeOpenedFolder,
            onFolderClose = { activeOpenedFolder = null },
            renameHideDialogProduct = renameHideDialogProduct,
            onRenameHideClose = { renameHideDialogProduct = null },
            showRenameFolderDialog = showRenameFolderDialog,
            onRenameFolderClose = { showRenameFolderDialog = null },
            searchQuery = searchQuery
        )
    } // end Column (最外层)
}


// 1. Applications single page grid with dynamic proportional cell spacing based on screen height