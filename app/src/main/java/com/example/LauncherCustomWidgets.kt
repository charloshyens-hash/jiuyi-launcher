package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LauncherCustomWidgets(
    widgetType: String,
    themeColor: Color,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xD90F172A) // Dark Soft Glass
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Widget Header bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                val headerIcon = when (widgetType) {
                    "RAM Booster" -> Icons.Default.Speed
                    "Music Cassette" -> Icons.Default.MusicNote
                    "Quick Tasks" -> Icons.Default.Checklist
                    else -> Icons.Default.BatteryChargingFull
                }
                Icon(
                    imageVector = headerIcon,
                    contentDescription = null,
                    tint = themeColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (widgetType) {
                        "RAM Booster" -> "久以便捷 • 系统内存优化"
                        "Music Cassette" -> "久以金曲 • 极简流媒体播放器"
                        "Quick Tasks" -> "久以便捷 • 桌面备忘提醒清单"
                        else -> "久以能量 • 动力瞬态仪表盘"
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Widget Content layouts
            when (widgetType) {
                "RAM Booster" -> RamBoosterContent(themeColor, viewModel)
                "Music Cassette" -> MusicCassetteWidget(
                    themeColor = themeColor,
                    viewModel = viewModel
                )
                "Quick Tasks" -> QuickTasksContent(themeColor)
                else -> BatteryDashboardContent(themeColor, viewModel)
            }
        }
    }
}

@Composable
fun RamBoosterContent(themeColor: Color, viewModel: LauncherViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var displayPercent by remember { mutableStateOf(viewModel.ramUsagePercent) }

    LaunchedEffect(viewModel.ramUsagePercent) {
        displayPercent = viewModel.ramUsagePercent
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Fluid Percent Progress Circle
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outerRimWidth = 4.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = size.width / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = outerRimWidth)
                )
                drawArc(
                    color = themeColor,
                    startAngle = -90f,
                    sweepAngle = (displayPercent / 100f) * 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = outerRimWidth + 2f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$displayPercent%",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "RAM 占用",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 8.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right Column: Info and Clean Launch Action
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (viewModel.isRamBoosting) "正在急速清理废弃缓存..." else "系统运行良好，可清理垃圾 ${String.format("%.2f", viewModel.realCacheSizeMb)} MB",
                color = if (viewModel.isRamBoosting) themeColor else Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "上次加速时间: ${viewModel.lastBoostTime}",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { viewModel.boostRam() },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !viewModel.isRamBoosting,
                modifier = Modifier.height(30.dp)
            ) {
                if (viewModel.isRamBoosting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "一键提速",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}



@Composable
fun QuickTasksContent(themeColor: Color) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var newTaskText by remember { mutableStateOf("") }
    val tasks = remember {
        mutableStateListOf(
            Pair("更换酷炫动态壁纸", false),
            Pair("在设置里定制专属悬浮Dock", false),
            Pair("点击一键提速释放内存", false)
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "这是一个桌面便签本。输入日常待办，点击 ＋ 即可记录。勾选即可划掉任务。",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTaskText,
                onValueChange = { newTaskText = it },
                placeholder = { Text("新增日常清单...", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = themeColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0x21000000),
                    unfocusedContainerColor = Color(0x14000000),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (newTaskText.trim().isNotEmpty()) {
                        tasks.add(Pair(newTaskText, false))
                        newTaskText = ""
                        keyboardController?.hide()
                    }
                }),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = {
                    if (newTaskText.trim().isNotEmpty()) {
                        tasks.add(Pair(newTaskText, false))
                        newTaskText = ""
                        keyboardController?.hide()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Task collection lists
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.heightIn(max = 120.dp)
        ) {
            tasks.take(4).forEachIndexed { index, task ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            tasks[index] = Pair(task.first, !task.second)
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (task.second) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (task.second) themeColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = task.first,
                        color = if (task.second) Color.White.copy(alpha = 0.4f) else Color.White,
                        fontSize = 12.sp,
                        textDecoration = if (task.second) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryDashboardContent(themeColor: Color, viewModel: LauncherViewModel) {
    val level = viewModel.batteryLevel
    val isCharging = viewModel.isBatteryCharging
    val temp = viewModel.batteryTemperature
    val volt = viewModel.batteryVoltage

    val infiniteTransition = rememberInfiniteTransition(label = "powerPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glowing Battery outline
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .drawBehind {
                    // Draw filled power indicator matching real battery
                    val pct = level / 100f
                    val powerFillWidth = size.width * pct
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(themeColor, themeColor.copy(alpha = 0.7f))
                        ),
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size((powerFillWidth - 4.dp.toPx()).coerceAtLeast(0f), size.height - 4.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$level% ${if (isCharging) "充电中" else "放电中"}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Telemetry details list
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = themeColor.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "系统功耗: ${String.format("%.2f", volt)}V 正常", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
            Text(text = "电池健康度: 100% (良好)", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            Text(text = "温度: ${String.format("%.1f", temp)}°C / 正常状态", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}
