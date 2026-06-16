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
//  小组件抽屉网格（从 LauncherAppDrawer 拆分）
// ═══════════════════════════════════════════════════════════════════════════

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
