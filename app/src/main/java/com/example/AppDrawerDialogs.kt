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

// ═══════════════════════════════════════════════════════════════════════════
//  抽屉弹窗集合（从 LauncherAppDrawer 拆分，所有 AlertDialog/弹层在此集中管理）
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun AppDrawerDialogs(
    viewModel: LauncherViewModel,
    themeColor: Color,
    showToast: (String) -> Unit,
    isSearchDialogOpen: Boolean,
    onSearchDialogClose: () -> Unit,
    isManageHiddenDialogOpen: Boolean,
    onManageHiddenClose: () -> Unit,
    showSortDialog: Boolean,
    onSortDialogClose: () -> Unit,
    showSmartCategoryDialog: Boolean,
    onSmartCategoryClose: () -> Unit,
    showNewFolderDialog: Boolean,
    onNewFolderClose: () -> Unit,
    showListSettingsDialog: Boolean,
    onListSettingsClose: () -> Unit,
    activeOpenedFolder: DrawerFolder?,
    onFolderClose: () -> Unit,
    renameHideDialogProduct: AppModel?,
    onRenameHideClose: () -> Unit,
    showRenameFolderDialog: DrawerFolder?,
    onRenameFolderClose: () -> Unit,
    searchQuery: String
) {
        // Expanded Search Dialog Picker
        if (isSearchDialogOpen) {
            AlertDialog(
                onDismissRequest = { onSearchDialogClose() },
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
                        onClick = { onSearchDialogClose() }
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
            onDismissRequest = { onManageHiddenClose() },
            containerColor = Color(0xFF1C1B1B),
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
                TextButton(onClick = { onManageHiddenClose() }) {
                    Text("完成", color = themeColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- Organization Dialog overlays (V2) ---
    if (showSortDialog) {
        val currentSortType by viewModel.drawerSortType.collectAsState()
        AlertDialog(
            onDismissRequest = { onSortDialogClose() },
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
                                    onSortDialogClose()
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
                TextButton(onClick = { onSortDialogClose() }) {
                    Text("取消", color = themeColor)
                }
            }
        )
    }

    if (showSmartCategoryDialog) {
        AlertDialog(
            onDismissRequest = { onSmartCategoryClose() },
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
                            onSmartCategoryClose()
                            showToast("智能分类完成，原文件夹已安全备份！")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("开始自动分类", color = Color.Black)
                    }
                    TextButton(
                        onClick = {
                            val ok = viewModel.restoreLayoutSnapshot()
                            onSmartCategoryClose()
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
                TextButton(onClick = { onSmartCategoryClose() }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }

    if (showNewFolderDialog) {
        var folderNameInput by remember { mutableStateOf("新建文件夹") }
        AlertDialog(
            onDismissRequest = { onNewFolderClose() },
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
                        onNewFolderClose()
                        showToast("文件夹「$name」创建成功")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) {
                    Text("创建", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { onNewFolderClose() }) {
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
            onDismissRequest = { onListSettingsClose() },
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
                TextButton(onClick = { onListSettingsClose() }) {
                    Text("确定", color = themeColor)
                }
            }
        )
    }

    if (activeOpenedFolder != null) {
        val foldersList by viewModel.drawerFolders.collectAsState()
        val folder = foldersList.find { it.id == activeOpenedFolder!!.id }
        
        if (folder == null) {
            onFolderClose()
        } else {
            var showAppsSelector by remember { mutableStateOf(false) }
            var isRenamingFolder by remember { mutableStateOf(false) }
            var updatedNameInput by remember { mutableStateOf(folder.name) }
            
            val rawAppList by viewModel.appList.collectAsState()
            val folderApps = folder.packageNames.mapNotNull { pkg -> rawAppList.firstOrNull { it.packageName == pkg } }

            AlertDialog(
                onDismissRequest = { onFolderClose() },
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
                                onFolderClose()
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
                                                    onFolderClose()
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
                    TextButton(onClick = { onFolderClose() }) {
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
            onDismissRequest = { onRenameFolderClose() },
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
                        onRenameFolderClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) {
                    Text("保存", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { onRenameFolderClose() }) {
                    Text("取消", color = themeColor)
                }
            }
        )
    }
}
