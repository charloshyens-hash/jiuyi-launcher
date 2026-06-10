package com.example

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
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
    var isManageHiddenDialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF90B0F19)) // 91-Classic translucent dark slate
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
                            modifier = Modifier.background(Color(0xFF1E293B))
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
                                text = { 
                                    Text(
                                        if (showLabels) "隐藏图标标签" else "显示图标标签", 
                                        color = Color.White, 
                                        fontSize = 13.sp
                                    ) 
                                },
                                onClick = {
                                    viewModel.toggleShowLabels()
                                    showMoreMenu = false
                                },
                                leadingIcon = { Icon(if (showLabels) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = themeColor) }
                            )
                            DropdownMenuItem(
                                text = { Text("抽屉网格: ${drawerGrid}", color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    val next = if (drawerGrid == "4x6") "5x5" else "4x6"
                                    viewModel.updateDrawerGrid(next)
                                    showMoreMenu = false
                                    showToast("已切换网格布局为 $next")
                                },
                                leadingIcon = { Icon(Icons.Default.GridView, null, tint = themeColor) }
                            )
                            DropdownMenuItem(
                                text = { Text("切换图标滤镜 (${iconPackFilter})", color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    val list = listOf("Minimalist", "Vintage Pixel", "Sketch Outline", "Raw Native")
                                    val nextIdx = (list.indexOf(iconPackFilter) + 1) % list.size
                                    viewModel.updateIconPackFilter(list[nextIdx])
                                    showMoreMenu = false
                                    showToast("图标外观滤镜已更改为: ${list[nextIdx]}")
                                },
                                leadingIcon = { Icon(Icons.Default.Palette, null, tint = themeColor) }
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
                                text = { Text("退出应用抽屉", color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    showMoreMenu = false
                                    onClose()
                                },
                                leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, tint = themeColor) }
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
                        onDrop = onDrop
                    )
                }
            } else if (pageIndex == appPagesCount) {
                // Page after apps list: 小组件/小部件 (Widgets)
                WidgetsDrawerGrid(
                    pinnedWidgets = pinnedWidgets,
                    onPinWidgetToggle = onPinWidgetToggle,
                    themeColor = themeColor
                )
            } else {
                // Last Page: 我的手机 (My Phone)
                MyPhoneDrawerDashboard(
                    viewModel = viewModel,
                    themeColor = themeColor
                )
            }
        }

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

        // Expanded Search Dialog Picker
        if (isSearchDialogOpen) {
            AlertDialog(
                onDismissRequest = { isSearchDialogOpen = false },
                containerColor = Color(0xFF1E293B),
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

    // Dialog to manage hidden apps dynamically
    if (isManageHiddenDialogOpen) {
        val allApps = viewModel.appList.collectAsState().value.sortedBy { it.label.lowercase() }
        val hiddenPackages by viewModel.hiddenPackagesFlow.collectAsState()

        AlertDialog(
            onDismissRequest = { isManageHiddenDialogOpen = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "管理应用隐藏状态",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "勾选的应用将在应用抽屉中隐藏。取消勾选即可恢复显示。",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(allApps.size) { index ->
                                val app = allApps[index]
                                val isHidden = hiddenPackages.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.toggleHiddenPackage(app.packageName)
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconStylingCard(
                                        app = app,
                                        filter = iconPackFilter,
                                        themeColor = themeColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = app.label,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Checkbox(
                                        checked = isHidden,
                                        onCheckedChange = {
                                            viewModel.toggleHiddenPackage(app.packageName)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = themeColor,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isManageHiddenDialogOpen = false }) {
                    Text("完成", color = themeColor, fontWeight = FontWeight.Bold)
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
    displayApps: List<AppModel>,
    pageIdx: Int,
    itemsPerPage: Int,
    iconFilter: String,
    onAppLongClicked: (AppModel) -> Unit,
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
    
    val pageApps = displayApps.subList(startIdx, endIdx)
    val cols = if (gridMode == "5x5") 5 else 4
    val rows = if (gridMode == "5x5") 5 else 6

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Compute dynamically scaled cell heights to fit perfectly without overflow or wasted bottom space
        val usableHeight = maxHeight - 24.dp
        val cellHeight = if (usableHeight > 100.dp) usableHeight / rows else 88.dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            userScrollEnabled = false, // 👈 Disable vertical bounce/scrolling entirely
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
        ) {
            itemsIndexed(pageApps) { _, app ->
                var itemScreenX by remember { mutableStateOf(0f) }
                var itemScreenY by remember { mutableStateOf(0f) }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center, // perfectly center vertical contents
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cellHeight) // 📊 Auto-matching responsive cellular heights
                        .onGloballyPositioned { bounds ->
                            val coords = bounds.positionInWindow()
                            itemScreenX = coords.x / density
                            itemScreenY = coords.y / density
                        }
                        .pointerInput(app) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { localOffset ->
                                    viewModel.draggedApp = app
                                    viewModel.isDraggingActive = true
                                    viewModel.isDraggingFromDock = false
                                    viewModel.dragSourceIndex = -1
                                    viewModel.dragOffset = androidx.compose.ui.geometry.Offset(
                                        x = itemScreenX + 26f,
                                        y = itemScreenY + 26f
                                    )
                                },
                                onDragEnd = {
                                    onDrop()
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
                        .clickable { app.launch(context) }
                ) {
                    IconStylingCard(
                        app = app,
                        filter = iconFilter,
                        themeColor = themeColor,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    if (showLabels) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = app.label,
                            color = Color.White.copy(alpha = 0.9f),
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
}

// Applying custom 91 retro/minimal overlay layers to basic app icons
@Composable
fun IconStylingCard(
    app: AppModel,
    filter: String,
    themeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
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
    themeColor: Color
) {
    val widgetPresets = listOf("RAM Booster", "Music Cassette", "Quick Tasks", "Power Battery")

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)
    ) {
        itemsIndexed(widgetPresets) { _, widget ->
            val isPinned = pinnedWidgets.contains(widget)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
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

        // Direct Quick themes manager list inside My Phone
        Text("极速更换系统色彩主题", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val themesColors = listOf(
                Pair("暖杏", Color(0xFFFA5F3D)),
                Pair("靛青", Color(0xFF6366F1)),
                Pair("霓虹", Color(0xFF06B6D4)),
                Pair("祖母", Color(0xFF10B981)),
                Pair("嫣粉", Color(0xFFEC4899)),
                Pair("琥珀", Color(0xFFF59E0B))
            )
            themesColors.forEachIndexed { idx, (name, color) ->
                val isActive = viewModel.currentThemeIndex.collectAsState().value == idx
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { viewModel.updateTheme(idx) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isActive) {
                            Box(modifier = Modifier.size(16.dp).background(Color.White, shape = CircleShape))
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(text = name, color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                }
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
