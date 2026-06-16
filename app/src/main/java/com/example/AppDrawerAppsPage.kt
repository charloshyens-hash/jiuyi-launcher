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
//  应用抽屉单页网格（从 LauncherAppDrawer 拆分）
// ═══════════════════════════════════════════════════════════════════════════

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
