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
//  我的手机面板 + 单项组件（从 LauncherAppDrawer 拆分）
// ═══════════════════════════════════════════════════════════════════════════

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
