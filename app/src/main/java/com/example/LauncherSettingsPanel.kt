package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LauncherSettingsPanel(
    viewModel: LauncherViewModel,
    themeColor: Color,
    onClose: () -> Unit,
    showToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect settings flows from VM
    val currentThemeIndex by viewModel.currentThemeIndex.collectAsState()
    val clockStyle by viewModel.clockStyle.collectAsState()
    val wallpaperName by viewModel.wallpaperName.collectAsState()
    val showLabels by viewModel.showLabels.collectAsState()
    val drawerGrid by viewModel.drawerGrid.collectAsState()
    val iconPackFilter by viewModel.iconPackFilter.collectAsState()
    val dockPackages by viewModel.dockPackages.collectAsState()
    val appList by viewModel.appList.collectAsState()
    
    val isWeatherOnlineAllowed by viewModel.isWeatherOnlineAllowed.collectAsState()
    val weatherState by viewModel.weatherState.collectAsState()
    val customCityVal = weatherState.city
    val customWeatherVal = weatherState.weather
    val customTempVal = weatherState.temperature

    val musicWidgetMode by viewModel.musicWidgetMode.collectAsState()
    val preferredMusicPackage by viewModel.preferredMusicPackage.collectAsState()

    // Dock slot editor active state helper
    var activeDockSlotSelector by remember { mutableStateOf<Int?>(null) }
    var showCustomPlayerSelector by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF131313)) // Jiuyi Surface #131313
            .navigationBarsPadding()
            .statusBarsPadding()
    ) {
        // Core header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x0CFFFFFF), shape = CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "久以桌面设置中心",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Appearance Customization ("外观定制")
            item {
                SettingsCategoryCard(title = "外观定制", icon = Icons.Outlined.Palette, themeColor = themeColor) {
                    Text(
                        text = "在这里您可以完全个性化定制您的久以智能桌面外观，包括应用图标包风格、全局系统品牌色彩以及酷炫的动态物理壁纸特效。",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 1. Theme Color (主体色) Subsection
                    Text(
                        text = "主体色配置",
                        color = themeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // List of 6 thematic color choices
                    val colorOptions = listOf(
                        Triple("暖阳橙杏", Color(0xFFFA5F3D), "明亮柔和橙杏色"),
                        Triple("极光霓虹", Color(0xFF00D1FF), "幻白绚丽极光青"),
                        Triple("极客黛蓝", Color(0xFF6366F1), "深度数码极客蓝"),
                        Triple("翡翠初碧", Color(0xFF10B981), "生机盎然原生态翠"),
                        Triple("蔷薇电粉", Color(0xFFEC4899), "超次元蔷薇幻影红"),
                        Triple("琥珀金芒", Color(0xFFF59E0B), "复古辉耀金色光芒")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x08FFFFFF), shape = RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        colorOptions.forEachIndexed { index, (name, colorVal, desc) ->
                            val isSelected = currentThemeIndex == index
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.updateTheme(index) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(colorVal, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = desc,
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = themeColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. Icon Pack Management (图标包管理) Subsection
                    Text(
                        text = "图标包管理",
                        color = themeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val packages = listOf(
                        Triple("Minimalist", "极简极光 (Minimalist)", "纯色气泡与纤柔极简高光混合"),
                        Triple("Vintage Pixel", "极经典像素 (Vintage Pixel)", "硬核像素色块拼贴搭配亚克力质感"),
                        Triple("Sketch Outline", "手绘钢笔轮廓 (Sketch Outline)", "暗炭黑原画素描透视拟真轮廓"),
                        Triple("Raw Native", "原生全景图标 (Raw Native)", "无任何蒙版覆盖的纯生态原厂图标")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x08FFFFFF), shape = RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        packages.forEach { (filterKey, titleTxt, descTxt) ->
                            val isSelected = iconPackFilter == filterKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.updateIconPackFilter(filterKey) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (filterKey) {
                                        "Minimalist" -> Icons.Default.BrightnessLow
                                        "Vintage Pixel" -> Icons.Default.GridOn
                                        "Sketch Outline" -> Icons.Default.Gesture
                                        else -> Icons.Default.Android
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) themeColor else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = titleTxt,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = descTxt,
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.RadioButtonChecked,
                                        contentDescription = null,
                                        tint = themeColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. Wallpaper Management (壁纸管理) Subsection
                    Text(
                        text = "壁纸展示管理",
                        color = themeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val wallPapers = listOf("Warm Sunlight", "Cosmic Wave", "Interactive Matrix", "Starfield Warp", "Minimal Slate")

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x08FFFFFF), shape = RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        wallPapers.forEach { wp ->
                            val isSelected = wallpaperName == wp
                            val title = when (wp) {
                                "Warm Sunlight" -> "温暖柔和初照阳晨 (明亮暖色)"
                                "Cosmic Wave" -> "慢速流体宇宙波线粒子"
                                "Interactive Matrix" -> "黑客帝国祖母绿字符代码雨"
                                "Starfield Warp" -> "3D太空星域物理偏航星轨"
                                else -> "微粒子毛玻璃复古岩炭暗灰 (省电)"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.updateWallpaper(wp) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Landscape,
                                    contentDescription = null,
                                    tint = if (isSelected) themeColor else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = themeColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Clock & Live Wallpaper options
            item {
                SettingsCategoryCard(title = "悬浮时钟形式", icon = Icons.Outlined.Schedule, themeColor = themeColor) {
                    val clocks = listOf("Retro Flip", "Minimalist", "Analog Classic")
                    clocks.forEach { style ->
                        val isSelected = clockStyle == style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateClockStyle(style) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (style) {
                                    "Retro Flip" -> "91复古分局翻页钟  (建议用于全景背景)"
                                    "Minimalist" -> "太空黑极简超大双行表"
                                    else -> "古典重金属圆盘时钟 (画布平移秒针)"
                                },
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = themeColor)
                            }
                        }
                    }
                }
            }

            // Section 4: Home screen widgets layout and custom Dock configurations
            item {
                SettingsCategoryCard(title = "极简 DOCK 底部快捷管理", icon = Icons.Outlined.SettingsInputComposite, themeColor = themeColor) {
                    Text(
                        text = "底部Dock固定容纳5个卡位（中央内置菜单键）。你可以随时更换替换其中4个应用位置捷径：",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Render Dock 5 slots preview
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        dockPackages.forEachIndexed { index, pkg ->
                            val isMenu = pkg == "MENU_BUTTON"
                            val labelText = if (isMenu) "主抽屉" else {
                                appList.firstOrNull { it.packageName == pkg }?.label?.take(3) ?: pkg.takeLast(3)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isMenu) themeColor.copy(alpha = 0.2f) else Color(0x0CFFFFFF))
                                    .clickable { if (!isMenu) activeDockSlotSelector = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                    Icon(
                                        imageVector = if (isMenu) Icons.Default.Apps else if (pkg == "EMPTY") Icons.Default.AddCircleOutline else Icons.Default.Android,
                                        contentDescription = null,
                                        tint = if (isMenu) themeColor else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (pkg == "EMPTY") "未绑定" else labelText,
                                        fontSize = 8.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.width(44.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 4b: Weather sync and privacy control settings
            item {
                SettingsCategoryCard(
                    title = "位置隐私与桌面天气同步控制",
                    icon = Icons.Outlined.Cloud,
                    themeColor = themeColor
                ) {
                    Text(
                        text = "主屏幕时钟模块同步展示气象。为保障您的隐私，默认使用本地安全设置。当允许在线网络同步时，程序将基于当前网络IP定位获取该国省市的气温状况，绝不索要任何多余的GPS物理位置授权。",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "网络IP定位同步天气", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = if (isWeatherOnlineAllowed) "已开启位置天气同步" else "已禁用（推荐：安全并节省电量）", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        Switch(
                            checked = isWeatherOnlineAllowed,
                            onCheckedChange = { viewModel.updateWeatherConsent(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = themeColor,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    if (!isWeatherOnlineAllowed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "自定义常驻显示数值:",
                            color = themeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customCityVal,
                                onValueChange = { viewModel.updateCustomWeather(it, customWeatherVal, customTempVal) },
                                label = { Text("城市", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp) },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = themeColor,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedContainerColor = Color(0x05FFFFFF),
                                    unfocusedContainerColor = Color(0x05FFFFFF)
                                )
                            )

                            OutlinedTextField(
                                value = customWeatherVal,
                                onValueChange = { viewModel.updateCustomWeather(customCityVal, it, customTempVal) },
                                label = { Text("天气", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp) },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.weight(1.5f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = themeColor,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedContainerColor = Color(0x05FFFFFF),
                                    unfocusedContainerColor = Color(0x05FFFFFF)
                                )
                            )

                            OutlinedTextField(
                                value = customTempVal,
                                onValueChange = { viewModel.updateCustomWeather(customCityVal, customWeatherVal, it) },
                                label = { Text("温标", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp) },
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = themeColor,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedContainerColor = Color(0x05FFFFFF),
                                    unfocusedContainerColor = Color(0x05FFFFFF)
                                )
                            )
                        }
                    }
                }
            }

            // Section 4c: Music Integration Options
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                SettingsCategoryCard(
                    title = "音频播放与系统流媒体整合方式",
                    icon = Icons.Outlined.MusicNote,
                    themeColor = themeColor
                ) {
                    Text(
                        text = "Music Cassette 磁带音乐组件可以播放线上精选经典电台，也可以作为您手机中已安装音乐软件的系统级流媒体控制中枢，读取并控制播放。极简轻量，对电池无负担。",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Selection Row: Mode
                    Text(
                        text = "播放中枢工作模式:",
                        color = themeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val modes = listOf(
                        Pair("系统级媒体控制中枢", 0),
                        Pair("内置经典经典粤语电台", 1)
                    )
                    modes.forEach { (name, modeIdx) ->
                        val isSelected = musicWidgetMode == modeIdx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateMusicWidgetMode(modeIdx) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateMusicWidgetMode(modeIdx) },
                                colors = RadioButtonDefaults.colors(selectedColor = themeColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = name, color = Color.White, fontSize = 13.sp)
                        }
                    }

                    if (musicWidgetMode == 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "绑定首选播放应用 (当未激活时一键拉起):",
                            color = themeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val players = listOf(
                            Pair("网易云音乐 (已支持原生控制)", "com.netease.cloudmusic"),
                            Pair("QQ音乐 (已支持原生控制)", "com.tencent.qqmusic"),
                            Pair("酷狗音乐 (已支持原生控制)", "com.kugou.android")
                        )

                        val isCustomSelected = preferredMusicPackage.isNotEmpty() && players.none { it.second == preferredMusicPackage }
                        val customAppLabel = if (isCustomSelected) {
                            val matchingApp = appList.firstOrNull { it.packageName == preferredMusicPackage }
                            if (matchingApp != null) "${matchingApp.label} (已自定义绑定)" else "未命名播放器 ($preferredMusicPackage)"
                        } else null

                        players.forEach { (name, pkg) ->
                            val isSelected = preferredMusicPackage == pkg
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updatePreferredMusicPackage(pkg) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(if (isSelected) themeColor else Color.White.copy(alpha = 0.1f), shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = name,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        if (isCustomSelected && customAppLabel != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updatePreferredMusicPackage(preferredMusicPackage) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(themeColor, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = customAppLabel,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showCustomPlayerSelector = true }
                                .background(Color(0x0CFFFFFF))
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = themeColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "添加并绑定其他播放应用", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    showToast("未检测到系统设置路径，请在系统设置搜索「通知访问权限」进行授权")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("授予系统级通知访问与媒体控制权", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "注: 开启「通知访问权限」后，JiuYi Launcher 即可接收系统广播更新，无需消耗任何网络数据资费。默认状态下，点击控制将直接唤醒已选音乐软件播放您歌单里的本地/缓存曲目。",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "粤语经典红日、月半小夜曲、漫步人生路。流媒体技术采用安全 HTTPS 直播链接，由于非您个人缓存，在流量状态下可能会产生移动流量消耗。无网络时点击将无法加载。",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Section 5: Version logs and About
            item {
                SettingsCategoryCard(title = "关于软件 / 开发者信息", icon = Icons.Outlined.Info, themeColor = themeColor) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "软件名称: 久以桌面 (JiuYi Launcher)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "版本代号: v1.0.3 (Retro Stable Premium)", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(text = "核心概念: 极致留白背景，应用抽屉分页集中管理，微芯片电池一键物理加速，还原经典2012安卓91桌面交互乐趣。", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(text = "开源声明:", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Licensed under Apache License 2.0. Copyright (c) 1998-2026 JiuYi Lab.", color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                    }
                }
            }
        }
    }

    // Action dialog to select which app to bind on Dock elements
    activeDockSlotSelector?.let { slotIdx ->
        AlertDialog(
            onDismissRequest = { activeDockSlotSelector = null },
            containerColor = Color(0xFF1C1B1B),
            title = { Text("将应用绑定至 Dock 第 ${slotIdx + 1} 卡位", color = Color.White, fontSize = 15.sp) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Include option to clear slot
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.removeDockItem(slotIdx)
                                    showToast("第 ${slotIdx + 1} 位绑定的应用捷径已清空")
                                    activeDockSlotSelector = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("置空此卡位", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }

                    // Display all launchable apps
                    items(appList.size) { appIdx ->
                        val app = appList[appIdx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.swapOrUpdateDockItem(slotIdx, app.packageName)
                                    showToast("已将 ${app.label} 固定至快捷菜单栏")
                                    activeDockSlotSelector = null
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Launch, contentDescription = null, tint = themeColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = app.label, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDockSlotSelector = null }) {
                    Text("取消", color = themeColor)
                }
            }
        )
    }

    if (showCustomPlayerSelector) {
        AlertDialog(
            onDismissRequest = { showCustomPlayerSelector = false },
            containerColor = Color(0xFF1C1B1B),
            title = { Text("选择要绑定的音乐播放应用", color = Color.White, fontSize = 15.sp) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(appList.size) { appIdx ->
                        val app = appList[appIdx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updatePreferredMusicPackage(app.packageName)
                                    showToast("已成功绑定首选播放应用为: ${app.label}")
                                    showCustomPlayerSelector = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = themeColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = app.label, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomPlayerSelector = false }) {
                    Text("取消", color = themeColor)
                }
            }
        )
    }
}

@Composable
fun SettingsCategoryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    themeColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = themeColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            content()
        }
    }
}
