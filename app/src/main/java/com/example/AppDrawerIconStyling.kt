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
//  图标样式卡片（从 LauncherAppDrawer 拆分）
// ═══════════════════════════════════════════════════════════════════════════

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
