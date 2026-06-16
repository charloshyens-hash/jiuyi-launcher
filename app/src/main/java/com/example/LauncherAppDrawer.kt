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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    // Query states from VM
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val showLabels by viewModel.showLabels.collectAsState()
    val drawerGrid by viewModel.drawerGrid.collectAsState() // "4x6" vs "5x5"
    val iconPackFilter by viewModel.iconPackFilter.collectAsState()
    val drawerPageIndex by viewModel.drawerPageIndex.collectAsState()

    val itemsPerPage = when (drawerGrid) {
        "5x5" -> 25
        else -> 24 // 4x6
    }
    val appPagesCount = Math.max(1, (filteredApps.size + itemsPerPage - 1) / itemsPerPage)
    val totalDrawerPages = appPagesCount + 2

    val pagerState = rememberPagerState(initialPage = drawerPageIndex.coerceIn(0, totalDrawerPages - 1), pageCount = { totalDrawerPages })

    // Sync from pagerState.currentPage to viewModel.drawerPageIndex
    LaunchedEffect(pagerState.currentPage) {
        viewModel.drawerPageIndex.value = pagerState.currentPage
    }

    // Sync from viewModel.drawerPageIndex to pagerState.currentPage
    LaunchedEffect(drawerPageIndex) {
        val bounded = drawerPageIndex.coerceIn(0, totalDrawerPages - 1)
        if (pagerState.currentPage != bounded) {
            pagerState.animateScrollToPage(bounded)
        }
    }

    // Instantly snap to 0 on backpress to avoid layout offsets
    LaunchedEffect(Unit) {
        viewModel.backToFirstScreenEvent.collect {
            pagerState.scrollToPage(0)
        }
    }

    // Pop up states
    var showMoreMenu by remember { mutableStateOf(false) }
    var renameHideDialogProduct by remember { mutableStateOf<AppModel?>(null) }
    var isSearchDialogOpen by remember { mutableStateOf(false) }
    // ── 改动1：替换旧的 isManageHiddenDialogOpen ──────────────────────────────
    var showVaultEntry by remember { mutableStateOf(false) }

    var showSortDialog by remember { mutableStateOf(false) }
    var showSmartCategoryDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showListSettingsDialog by remember { mutableStateOf(false) }
    var activeOpenedFolder by remember { mutableStateOf<DrawerFolder?>(null) }
    var showRenameFolderDialog by remember { mutableStateOf<DrawerFolder?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xEB131313)) // Jiuyi Desktop 92% opacity translucent dark surface
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
    ) {
        // Line-1 Toolbar: Tabs ("应用", "小组件", "我的手机") + Search Icon + Options Icon (⋮)
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                // Continuous fine bottom border line (1.dp) - aligned to absolute bottom of Box
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.12f), 
                    thickness = 1.dp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Perfect global horizontal alignment centered across the remaining space
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tabs: Applications, widgets and properties - centered in remaining space
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
                                // Vertically center labels elegantly
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
                                
                                // Indicator line: Bold accent bar at bottom, physical overlap with HorizonalDivider
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

                    // Action buttons positioned to the right end
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search Icon Button
                        IconButton(onClick = { isSearchDialogOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索应用",
                                tint = if (searchQuery.isNotEmpty()) themeColor else Color.White
                            )
                        }

                        // Options popup trigger Options (⋮)
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
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
                                    // ── 改动2：替换旧的 isManageHiddenDialogOpen ──────────
                                    showVaultEntry = true
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
                    }
                }
            }
        }
    }

        Spacer(modifier = Modifier.height(12.dp))

        // Main HorizontalPager to support horizontal swipe navigation across all flat grids & views
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
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
                    // Page is one of the divided Apps Grid pages
                    Column(modifier = Modifier.fillMaxSize()) {
                    if (searchQuery.isNotEmpty() && pageIndex == 0) {
                        // Active search filter indicator label, shown on front page
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
                // Page after apps list: 小组件/小部件 (Widgets)
                WidgetsDrawerGrid(
                    pinnedWidgets = pinnedWidgets,
                    onPinWidgetToggle = onPinWidgetToggle,
                    themeColor = themeColor,
                    viewModel = viewModel,
                    onDrop = onDrop
                )
            } else {
                // Last Page: 我的手机 (My Phone)
                MyPhoneDrawerDashboard(
                    viewModel = viewModel,
                    themeColor = themeColor
                )
            }
        }
    }

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
                    .background(Color(0xFF0C0C0C)) // Contrast deep black bar below
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
                    // Left navigation
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        if (thumbnailGroupIndex > 0) {
                            IconButton(onClick = { thumbnailGroupIndex-- }) {
                                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "左翻", tint = Color.White)
                            }
                        }
                    }

                    // Main 3 thumbnails section
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

                                        // Register current coordinates in map for dropped item targets
                                        viewModel.drawerThumbnailBounds = viewModel.drawerThumbnailBounds + (idx to androidx.compose.ui.geometry.Rect(left, top, left + w, top + h))
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
                                                Icon(imageVector = Icons.Default.WatchLater, contentDescription = null, tint = if (isCurrentPage) Color.White else themeColor, modifier = Modifier.size(10.dp))
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

                        // Add new page thumbnail if in last group and we can add more pages
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

                    // Right navigation
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
        }

        if (!isDraggingFromDrawer) {
            // 3. Multi-in-one unified navigation indicator dots at the bottom of the drawer (maps 1-to-1 to all pages)
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

        // Expanded Search Dialog Picker
        if (isSearchDialogOpen) {
            AlertDialog(
                onDismissRequest = { isSearchDialogOpen = false },
                containerColor = Color(0xFF1C1B1B),
                title = {
                    Text(
                        text = "快速搜索应用",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            viewModel.searchQuery.value = it
                            // Auto-scroll outer pager to "应用" (index 0) if query is typed
                            if (it.isNotEmpty() && pagerState.currentPage != 0) {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(0)
                                }
                            }
                        },
                        placeholder = { Text("可输入拼音/文字/包名", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { isSearchDialogOpen = false }
                    ) {
                        Text("开始查找", color = themeColor, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }

    // ── 改动3：删除旧 Dialog，替换为 App Vault 系统入口 ──────────────────────
    if (showVaultEntry) {
        val vaultIconPackFilter by viewModel.iconPackFilter.collectAsState()
        AppVaultEntryPoint(
            viewModel = viewModel,
            themeColor = themeColor,
            iconPackFilter = vaultIconPackFilter,
            onDismiss = { showVaultEntry = false }
        )
    }

    // --- Organization Dialog overlays (V2) ---
    if (showSortDialog) {
        val currentSortType by viewModel.drawerSortType.collectAsState()
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("应用图标排序", color = Color.White) },
            containerColor = Color(0xFF1E1E1E),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        "按字母排序" to 0,
                        "按安装时间从近到远" to 1,
                        "按安装时间从远到近" to 2,
                        "按使用次数从多到少" to 3
                    )
                    options.forEach { (text, index) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateDrawerSortType(index)
                                    showSortDialog = false
                                    showToast("排序方式已更改：$text")
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text, color = Color.White, fontSize = 14.sp)
                            if (currentSortType == index) {
                                Icon(Icons.Default.Check, contentDescription = "已选择", tint = themeColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("取消", color = themeColor)
                }
            }
        )
    }

    if (showSmartCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showSmartCategoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = themeColor, modifier = Modifier.padding(end = 8.dp))
                    Text("应用智能分类", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "选择智能分类后，算法会将桌面所有应用自动归入以下文件夹中：\n" +
                        "👉 社交、工具、游戏、影音、办公、系统工具、购物、其他 等。\n\n" +
                        "⚠️ 如果有原有文件夹，分类完成后您可以随时选择【恢复布局】进行撤销。",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Button(
                        onClick = {
                            viewModel.smartCategorizeApps()
                            showSmartCategoryDialog = false
                            showToast("智能分类完成，原文件夹已安全备份！")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("开始自动分类", color = Color.Black)
                    }
                    TextButton(
                        onClick = {
                            val ok = viewModel.restoreLayoutSnapshot()
                            showSmartCategoryDialog = false
                            if (ok) {
                                showToast("已成功恢复上次分类前的文件夹布局！")
                            } else {
                                showToast("没有可恢复的备份快照")
                            }
                        }
                    ) {
                        Text("恢复备份布局 (撤销)", color = themeColor)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartCategoryDialog = false }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }

    if (showNewFolderDialog) {
        var folderNameInput by remember { mutableStateOf("新建文件夹") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("新建文件夹", color = Color.White) },
            containerColor = Color(0xFF1E1E1E),
            text = {
                Column {
                    Text("请输入文件夹名称：", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColor,
                            focusedLabelColor = themeColor,
                            unfocusedBorderColor = Color.LightGray
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = folderNameInput.trim().ifEmpty { "新建文件夹" }
                        viewModel.createDrawerFolder(name)
                        showNewFolderDialog = false
                        showToast("文件夹「$name」创建成功")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) {
                    Text("创建", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("取消", color = themeColor)
                }
            }
        )
    }

    if (showListSettingsDialog) {
        val currentRoundness by viewModel.iconRoundness.collectAsState()
        val currentSizeScale by viewModel.iconSizeScale.collectAsState()
        val currentFontSize by viewModel.fontSizeSp.collectAsState()
        val currentShowLabel by viewModel.showLabels.collectAsState()
        val currentShowSys by viewModel.showSystemApps.collectAsState()
        val currentGrid by viewModel.drawerGrid.collectAsState()
        val currentFilter by viewModel.iconPackFilter.collectAsState()

        AlertDialog(
            onDismissRequest = { showListSettingsDialog = false },
            title = { Text("应用列表布局设置", color = Color.White) },
            containerColor = Color(0xFF1E1E1E),
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleShowLabels() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("显示应用名称标签", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = currentShowLabel,
                            onCheckedChange = { viewModel.toggleShowLabels() },
                            colors = SwitchDefaults.colors(checkedThumbColor = themeColor, checkedTrackColor = themeColor.copy(alpha = 0.5f))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleShowSystemApps() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("显示系统应用程序", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = currentShowSys,
                            onCheckedChange = { viewModel.toggleShowSystemApps() },
                            colors = SwitchDefaults.colors(checkedThumbColor = themeColor, checkedTrackColor = themeColor.copy(alpha = 0.5f))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val next = if (currentGrid == "4x6") "5x5" else "4x6"
                            viewModel.updateDrawerGrid(next)
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("抽屉网格列排列: $currentGrid", color = Color.White, fontSize = 13.sp)
                        Button(
                            onClick = {
                                val next = if (currentGrid == "4x6") "5x5" else "4x6"
                                viewModel.updateDrawerGrid(next)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor.copy(alpha = 0.15f), contentColor = themeColor),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("切换", fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("图标艺术滤镜: $currentFilter", color = Color.White, fontSize = 13.sp)
                        Button(
                            onClick = {
                                val filters = listOf("Minimalist", "Vintage Pixel", "Sketch Outline", "Raw Native")
                                val nIdx = (filters.indexOf(currentFilter) + 1) % filters.size
                                viewModel.updateIconPackFilter(filters[nIdx])
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor.copy(alpha = 0.15f), contentColor = themeColor),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("切滤镜", fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("图标圆角大小: ${currentRoundness}dp", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Slider(
                            value = currentRoundness.toFloat(),
                            onValueChange = { viewModel.updateIconRoundness(it.toInt()) },
                            valueRange = 0f..40f,
                            colors = SliderDefaults.colors(thumbColor = themeColor, activeTrackColor = themeColor)
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("图标比例缩放: ${currentSizeScale}%", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Slider(
                            value = currentSizeScale.toFloat(),
                            onValueChange = { viewModel.updateIconSizeScale(it.toInt()) },
                            valueRange = 50f..150f,
                            colors = SliderDefaults.colors(thumbColor = themeColor, activeTrackColor = themeColor)
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("应用字体大小: ${currentFontSize}sp", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Slider(
                            value = currentFontSize.toFloat(),
                            onValueChange = { viewModel.updateFontSizeSp(it.toInt()) },
                            valueRange = 8f..22f,
                            colors = SliderDefaults.colors(thumbColor = themeColor, activeTrackColor = themeColor)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showListSettingsDialog = false }) {
                    Text("确定", color = themeColor)
                }
            }
        )
    }

    if (activeOpenedFolder != null) {
        val foldersList by viewModel.drawerFolders.collectAsState()
        val folder = foldersList.find { it.id == activeOpenedFolder!!.id }
        
        if (folder == null) {
            activeOpenedFolder = null
        } else {
            var showAppsSelector by remember { mutableStateOf(false) }
            var isRenamingFolder by remember { mutableStateOf(false) }
            var updatedNameInput by remember { mutableStateOf(folder.name) }
            
            val rawAppList by viewModel.appList.collectAsState()
            val folderApps = folder.packageNames.mapNotNull { pkg -> rawAppList.firstOrNull { it.packageName == pkg } }

            AlertDialog(
                onDismissRequest = { activeOpenedFolder = null },
                containerColor = Color(0xFF161616),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRenamingFolder) {
                            OutlinedTextField(
                                value = updatedNameInput,
                                onValueChange = { updatedNameInput = it },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColor),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (updatedNameInput.isNotEmpty()) {
                                        viewModel.renameDrawerFolder(folder.id, updatedNameInput)
                                    }
                                    isRenamingFolder = false
                                }
                            ) {
                                Icon(Icons.Default.Save, "保存重命名", tint = themeColor)
                            }
                        } else {
                            Text(
                                text = folder.name,
                                color = Color.White,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isRenamingFolder = true }
                            )
                            IconButton(onClick = { isRenamingFolder = true }) {
                                Icon(Icons.Default.Edit, "重命名", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.deleteDrawerFolder(folder.id)
                                activeOpenedFolder = null
                                showToast("文件夹已解散")
                            }
                        ) {
                            Icon(Icons.Default.Delete, "解散本文件夹", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(folderApps) { app ->
                                var showAppMenu by remember { mutableStateOf(false) }
                                val iconRoundness by viewModel.iconRoundness.collectAsState()
                                val iconSizeScale by viewModel.iconSizeScale.collectAsState()
                                val fontSizeSp by viewModel.fontSizeSp.collectAsState()
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(app) {
                                            detectTapGestures(
                                                onTap = {
                                                    viewModel.recordAppLaunch(app.packageName)
                                                    app.launch(context)
                                                    activeOpenedFolder = null
                                                    onClose()
                                                },
                                                onLongPress = {
                                                    showAppMenu = true
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val filter by viewModel.iconPackFilter.collectAsState()
                                        IconStylingCard(
                                            app = app,
                                            filter = filter,
                                            themeColor = themeColor,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .scale(iconSizeScale / 100f),
                                            roundness = iconRoundness
                                        )
                                        if (showLabels) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = app.label,
                                                color = Color.White,
                                                fontSize = fontSizeSp.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showAppMenu,
                                        onDismissRequest = { showAppMenu = false },
                                        modifier = Modifier.background(Color(0xFF222222))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("移出本文件夹", color = Color.White) },
                                            onClick = {
                                                viewModel.removeAppFromDrawerFolder(folder.id, app.packageName)
                                                showToast("已将 ${app.label} 移出文件夹")
                                                showAppMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("置顶显示", color = Color.White) },
                                            onClick = {
                                                val list = folder.packageNames.toMutableList()
                                                list.remove(app.packageName)
                                                list.add(0, app.packageName)
                                                folder.packageNames.clear()
                                                folder.packageNames.addAll(list)
                                                viewModel.prefs.saveDrawerFolders(foldersList)
                                                viewModel.drawerFolders.value = foldersList.toList()
                                                showToast("置顶成功")
                                                showAppMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clickable { showAppsSelector = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Add, null, tint = Color.White)
                                        }
                                        if (showLabels) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("添加应用", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeOpenedFolder = null }) {
                        Text("关闭", color = themeColor)
                    }
                }
            )

            if (showAppsSelector) {
                AlertDialog(
                    onDismissRequest = { showAppsSelector = false },
                    title = { Text("添加/移除文件夹应用", color = Color.White) },
                    containerColor = Color(0xFF2E2E2E),
                    text = {
                        Column(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                            Text("勾选应用归入本文件夹（未勾选的应用会被移出此文件夹）：", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                            
                            val availableAppsSorted = rawAppList.sortedBy { it.label.lowercase() }
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(availableAppsSorted) { app ->
                                    val isChecked = folder.packageNames.contains(app.packageName)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isChecked) {
                                                    viewModel.removeAppFromDrawerFolder(folder.id, app.packageName)
                                                } else {
                                                    viewModel.addAppToDrawerFolder(folder.id, app.packageName)
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            IconStylingCard(
                                                app = app,
                                                filter = "Raw Native",
                                                themeColor = themeColor,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(app.label, color = Color.White, fontSize = 13.sp)
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (isChecked) {
                                                    viewModel.removeAppFromDrawerFolder(folder.id, app.packageName)
                                                } else {
                                                    viewModel.addAppToDrawerFolder(folder.id, app.packageName)
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = themeColor)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showAppsSelector = false },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                        ) {
                            Text("完成", color = Color.Black)
                        }
                    }
                )
            }
        }
    }

    if (showRenameFolderDialog != null) {
        val renameTarget = showRenameFolderDialog!!
        var renameInput by remember { mutableStateOf(renameTarget.name) }
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = null },
            title = { Text("重命名文件夹", color = Color.White) },
            containerColor = Color(0xFF1E1E1E),
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColor),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.trim().isNotEmpty()) {
                            viewModel.renameDrawerFolder(renameTarget.id, renameInput.trim())
                        }
                        showRenameFolderDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) {
                    Text("保存", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFolderDialog = null }) {
                    Text("取消", color = themeColor)
                }
            }
        )
    }
}


// 1. Applications single page grid with dynamic proportional cell spacing based on screen height
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleAppsPageGrid(
    viewModel: LauncherViewModel,
    themeColor: Color,
    showLabels: Boolean,
    gridMode: String,
    displayApps: List<DrawerItem>,
    pageIdx: Int,
    itemsPerPage: Int,
    iconFilter: String,
    onAppLongClicked: (AppModel) -> Unit,
    onFolderClicked: (DrawerFolder) -> Unit,
    onRenameFolderRequested: (DrawerFolder) -> Unit,
    onDrop: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    val startIdx = pageIdx * itemsPerPage
    val endIdx = Math.min(startIdx + itemsPerPage, displayApps.size)
    
    if (startIdx >= displayApps.size || displayApps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("找不到符合条件的应用", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
            }
        }
        return
    }
    
    val pageItems = displayApps.subList(startIdx, endIdx)
    val cols = if (gridMode == "5x5") 5 else 4
    val rows = if (gridMode == "5x5") 5 else 6

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val usableHeight = maxHeight - 24.dp
        val cellHeight = if (usableHeight > 100.dp) usableHeight / rows else 88.dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
        ) {
            itemsIndexed(pageItems) { localIndex, drawerItem ->
                val globalIdx = startIdx + localIndex
                var itemScreenX by remember { mutableStateOf(0f) }
                var itemScreenY by remember { mutableStateOf(0f) }

                when (drawerItem) {
                    is DrawerItem.App -> {
                        val app = drawerItem.app
                        val preUninstallApp by viewModel.preUninstallApp.collectAsState()
                        val isPreUninstall = preUninstallApp?.packageName == app.packageName

                        Box(
                            contentAlignment = Alignment.TopEnd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .onGloballyPositioned { bounds ->
                                    val coords = bounds.positionInWindow()
                                    itemScreenX = coords.x / density
                                    itemScreenY = coords.y / density
                                    val w = bounds.size.width / density
                                    val h = bounds.size.height / density
                                    viewModel.drawerItemBounds = viewModel.drawerItemBounds + (globalIdx to androidx.compose.ui.geometry.Rect(itemScreenX, itemScreenY, itemScreenX + w, itemScreenY + h))
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(app) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { localOffset ->
                                                viewModel.draggedApp = app
                                                viewModel.isDraggingActive = true
                                                viewModel.isDraggingFromDock = false
                                                viewModel.isDraggingFromDrawer = true
                                                viewModel.dragSourceIndex = -1
                                                viewModel.dragOffset = androidx.compose.ui.geometry.Offset(
                                                    x = itemScreenX + 26f,
                                                    y = itemScreenY + 26f
                                                )
                                                viewModel.dragDistance = -1f
                                                viewModel.preUninstallApp.value = null
                                            },
                                            onDragEnd = {},
                                            onDragCancel = {},
                                            onDrag = { change, dragAmount -> change.consume() }
                                        )
                                    }
                                    .clickable {
                                        viewModel.recordAppLaunch(app.packageName)
                                        app.launch(context)
                                    }
                            ) {
                                val iconRoundness by viewModel.iconRoundness.collectAsState()
                                val iconSizeScale by viewModel.iconSizeScale.collectAsState()
                                val fontSizeSp by viewModel.fontSizeSp.collectAsState()

                                IconStylingCard(
                                    app = app,
                                    filter = iconFilter,
                                    themeColor = themeColor,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .scale(iconSizeScale / 100f),
                                    roundness = iconRoundness
                                )
                                
                                if (showLabels) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = app.label,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = fontSizeSp.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }

                            if (isPreUninstall) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp, top = 2.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                        .clickable {
                                            viewModel.uninstallApp(context, app)
                                            viewModel.preUninstallApp.value = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Uninstall",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                    is DrawerItem.Folder -> {
                        val folder = drawerItem.folder
                        var showFolderMenu by remember { mutableStateOf(false) }

                        Box(
                            contentAlignment = Alignment.TopEnd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .onGloballyPositioned { bounds ->
                                    val coords = bounds.positionInWindow()
                                    itemScreenX = coords.x / density
                                    itemScreenY = coords.y / density
                                    val w = bounds.size.width / density
                                    val h = bounds.size.height / density
                                    viewModel.drawerItemBounds = viewModel.drawerItemBounds + (globalIdx to androidx.compose.ui.geometry.Rect(itemScreenX, itemScreenY, itemScreenX + w, itemScreenY + h))
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(folder) {
                                        detectTapGestures(
                                            onTap = {
                                                onFolderClicked(folder)
                                            },
                                            onLongPress = {
                                                showFolderMenu = true
                                            }
                                        )
                                    }
                            ) {
                                val iconRoundness by viewModel.iconRoundness.collectAsState()
                                val iconSizeScale by viewModel.iconSizeScale.collectAsState()
                                val fontSizeSp by viewModel.fontSizeSp.collectAsState()

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .scale(iconSizeScale / 100f)
                                        .clip(RoundedCornerShape(iconRoundness.dp))
                                        .background(Color.White.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val rawList by viewModel.appList.collectAsState()
                                    val miniApps = folder.packageNames.take(4).mapNotNull { pkg -> rawList.firstOrNull { it.packageName == pkg } }

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.padding(4.dp).fillMaxSize(),
                                        userScrollEnabled = false,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        items(miniApps) { miniApp ->
                                            IconStylingCard(
                                                app = miniApp,
                                                filter = "Raw Native",
                                                themeColor = themeColor,
                                                modifier = Modifier.fillMaxSize(),
                                                roundness = iconRoundness
                                            )
                                        }
                                    }
                                }

                                if (showLabels) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = folder.name,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = fontSizeSp.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showFolderMenu,
                                onDismissRequest = { showFolderMenu = false },
                                modifier = Modifier.background(Color(0xFF222222))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("重命名文件夹", color = Color.White) },
                                    onClick = {
                                        onRenameFolderRequested(folder)
                                        showFolderMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("解散文件夹", color = Color.White) },
                                    onClick = {
                                        viewModel.deleteDrawerFolder(folder.id)
                                        android.widget.Toast.makeText(context, "已解散「${folder.name}」", android.widget.Toast.LENGTH_SHORT).show()
                                        showFolderMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Applying custom 91 retro/minimal overlay layers to basic app icons
@Composable
fun IconStylingCard(
    app: AppModel,
    filter: String,
    themeColor: Color,
    modifier: Modifier = Modifier,
    roundness: Int = 12
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(roundness.dp))
            .background(
                when (filter) {
                    "Vintage Pixel" -> Color(0xFFE2E8F0) // retro matte card
                    "Sketch Outline" -> Color.Black // sketch outline
                    "Minimalist" -> themeColor.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .padding(if (filter == "Raw Native") 0.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Since we are running in an Android environment, displaying package icon or default placeholder
        val pm = LocalContext.current.packageManager
        var pkgIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
        
        LaunchedEffect(app.packageName) {
            try {
                // Background thread loader
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pkgIcon = pm.getApplicationIcon(app.packageName)
                }
            } catch (e: Exception) {
                pkgIcon = app.icon
            }
        }

        if (pkgIcon != null) {
            // Draw custom app logo
            com.example.IconImageRenderer(
                drawable = pkgIcon!!,
                modifier = Modifier.fillMaxSize(),
                iconStyle = filter,
                themeColor = themeColor
            )
        } else {
            // Fallback customizable vector symbol based on label initials
            val initialChar = app.label.firstOrNull()?.toString() ?: "A"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initialChar,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// 2. Beautiful customizable widgets collection selector inside Drawer Tab
@Composable
fun WidgetsDrawerGrid(
    pinnedWidgets: Set<String>,
    onPinWidgetToggle: (String) -> Unit,
    themeColor: Color,
    viewModel: LauncherViewModel,
    onDrop: () -> Unit
) {
    val widgetPresets = listOf("RAM Booster", "Music Cassette", "Quick Tasks", "Power Battery")
    val density = LocalDensity.current.density

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)
    ) {
        itemsIndexed(widgetPresets) { _, widget ->
            val isPinned = pinnedWidgets.contains(widget)
            var itemScreenX by remember { mutableStateOf(0f) }
            var itemScreenY by remember { mutableStateOf(0f) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .onGloballyPositioned { bounds ->
                        val coords = bounds.positionInWindow()
                        itemScreenX = coords.x / density
                        itemScreenY = coords.y / density
                    }
                    .pointerInput(widget) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
                                viewModel.draggedApp = AppModel(widget, "WIDGET:$widget", "")
                                viewModel.isDraggingActive = true
                                viewModel.isDraggingFromDock = false
                                viewModel.isDraggingFromDrawer = true
                                viewModel.dragSourceIndex = -1
                                viewModel.dragOffset = androidx.compose.ui.geometry.Offset(
                                    x = itemScreenX + 100f,
                                    y = itemScreenY + 40f
                                )
                            },
                            onDragEnd = {
                                onDrop()
                            },
                            onDragCancel = {
                                viewModel.draggedApp = null
                                viewModel.isDraggingActive = false
                                viewModel.isDraggingFromDrawer = false
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
                // Interactive micro component preview
                LauncherCustomWidgets(
                    widgetType = widget,
                    themeColor = themeColor,
                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPinned) "已被添加至桌面主屏" else "将其放置到桌面主屏面板",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onPinWidgetToggle(widget) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isPinned) Color(0xFFEF4444) else themeColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (isPinned) "移除小组件" else "添加组件",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// 3. 我的手机 (My Phone) retro utilities dashboard setup inside Drawer Tab
@Composable
fun MyPhoneDrawerDashboard(
    viewModel: LauncherViewModel,
    themeColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Card: Overall RAM level and Clean trigger action
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x3BFFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Cyclone, contentDescription = null, tint = themeColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("当前系统性能级别: 完美", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                
                Spacer(modifier = Modifier.height(14.dp))

                // Inline booster control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { viewModel.ramUsagePercent / 100f },
                        modifier = Modifier.size(45.dp),
                        color = themeColor,
                        strokeWidth = 4.dp,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("物理内存 RAM 已占用: ${viewModel.ramUsagePercent}%", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        Text("点击右侧按钮，释放冗余的应用程序缓存垃圾", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                    
                    IconButton(
                        onClick = { viewModel.boostRam() },
                        modifier = Modifier
                            .size(42.dp)
                            .background(themeColor, shape = CircleShape)
                    ) {
                        if (viewModel.isRamBoosting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.RocketLaunch, contentDescription = "Clean", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Sub Grid: 4x2 interactive entrances
        Text("久安系统工具", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardItem(
                title = "${String.format("%.2f", viewModel.realCacheSizeMb)} MB",
                subtitle = "系统可清理垃圾",
                icon = Icons.Default.DeleteSweep,
                textColor = Color(0xFFF43F5E),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.boostRam() }
            )
            DashboardItem(
                title = "${String.format("%.1f", viewModel.batteryTemperature)}°C",
                subtitle = "电池温度 (${viewModel.batteryLevel}%)",
                icon = Icons.Default.Thermostat,
                textColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardItem(
                title = "${viewModel.networkPingMs} ms",
                subtitle = "实时网络延迟",
                icon = Icons.Default.NetworkCheck,
                textColor = Color(0xFF06B6D4),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            DashboardItem(
                title = "${String.format("%.0f", viewModel.realTotalStorageGb)} GB",
                subtitle = "存储 (余${String.format("%.1f", viewModel.realFreeStorageGb)}G)",
                icon = Icons.Default.SdStorage,
                textColor = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }

        // Direct brand identity notice representing locked native color style
        Text("久以桌面 • 专属品牌规范", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x0CFFFFFF), shape = RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(12.dp).background(themeColor, shape = CircleShape))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = "系统设计：极光霓虹暗雅玻璃", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = "完美呈现 #131313 深邃岩板与 #00D1FF 霓虹高能青", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun DashboardItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(textColor.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
            }
        }
    }
}