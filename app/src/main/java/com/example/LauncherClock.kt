package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.content.ContentUris
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LauncherClock(
    clockStyle: String,
    themeColor: Color,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(Date()) }

    // Tick-tack dynamic refresh
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            kotlinx.coroutines.delay(1000)
        }
    }

    val weatherState by viewModel.weatherState.collectAsState()
    val city = weatherState.city
    val weather = weatherState.weather
    val temp = weatherState.temperature

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (clockStyle) {
            "Minimalist" -> BoldMinimalistClock(currentTime, themeColor, city, weather, temp, viewModel)
            "Analog Classic" -> AnalogClassicClock(currentTime, themeColor)
            else -> RetroFlipClock(currentTime, themeColor, city, weather, temp, viewModel)
        }
    }
}

fun launchAlarmClock(context: Context) {
    var launched = false
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        launched = true
    } catch (e: Exception) {}

    if (!launched) {
        val pm = context.packageManager
        try {
            val pkgs = pm.getInstalledPackages(0)
            for (pkg in pkgs) {
                val name = pkg.packageName.lowercase()
                if (name.contains("clock") && !name.contains("widget") && !name.contains("service")) {
                    val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        launched = true
                        break
                    }
                }
            }
        } catch (ex: Exception) {}
    }

    if (!launched) {
        val commonClocks = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.miui.clock",
            "com.oppo.alarmclock",
            "com.oneplus.deskclock"
        )
        for (pkg in commonClocks) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    launched = true
                    break
                }
            } catch (ex: Exception) {}
        }
    }

    if (!launched) {
        Toast.makeText(context, "未找到系统闹钟应用", Toast.LENGTH_SHORT).show()
    }
}

fun launchCalendar(context: Context) {
    var launched = false
    val pm = context.packageManager
    try {
        val pkgs = pm.getInstalledPackages(0)
        for (pkg in pkgs) {
            val name = pkg.packageName.lowercase()
            if (name.contains("calendar") && !name.contains("widget") && !name.contains("provider")) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    launched = true
                    break
                }
            }
        }
    } catch (ex: Exception) {}

    if (!launched) {
        try {
            val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            ContentUris.appendId(builder, System.currentTimeMillis())
            val intent = Intent(Intent.ACTION_VIEW).setData(builder.build()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            launched = true
        } catch (e: Exception) {}
    }

    if (!launched) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            launched = true
        } catch (ex: Exception) {}
    }

    if (!launched) {
        Toast.makeText(context, "未找到系统日历应用", Toast.LENGTH_SHORT).show()
    }
}

fun launchWeather(context: Context) {
    var launched = false
    val pm = context.packageManager
    try {
        val pkgs = pm.getInstalledPackages(0)
        for (pkg in pkgs) {
            val name = pkg.packageName.lowercase()
            if (name.contains("weather") && !name.contains("widget")) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    launched = true
                    break
                }
            }
        }
    } catch (e: Exception) {}

    if (!launched) {
        val commonWeather = listOf(
            "com.miui.weather2", // Xiaomi
            "com.sec.android.easyMover.Weather", 
            "com.coloros.weather2", // Oppo
            "com.tencent.weather",
            "com.moji.mjweather",
            "com.weather.Weather"
        )
        for (pkg in commonWeather) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    launched = true
                    break
                }
            } catch (e: Exception) {}
        }
    }

    if (!launched) {
        try {
            // Highly compatible, extremely clean web page as a fallback!
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cn.bing.com/weather")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            launched = true
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开天气应用或浏览器", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun RetroFlipClock(
    time: Date,
    themeColor: Color,
    city: String,
    weather: String,
    temp: String,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    val hrs = SimpleDateFormat("HH", Locale.getDefault()).format(time)
    val mins = SimpleDateFormat("mm", Locale.getDefault()).format(time)
    val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(time)
    val weekdayStr = SimpleDateFormat("EEEE", Locale.CHINESE).format(time)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.clickable { launchAlarmClock(context) }
    ) {
        // Hour Flip Box
        FlipNumberBox(hrs, themeColor)
        Spacer(modifier = Modifier.width(8.dp))
        // Colon flashing separator
        Text(
            text = ":",
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (time.time % 2000 > 1000) 0.3f else 0.9f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // Minute Flip Box
        FlipNumberBox(mins, themeColor)
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Info Deck: Calendar date, weekday and weather status
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x3B0F172A))
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { launchCalendar(context) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = "Date",
                tint = themeColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$dateStr $weekdayStr",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Divider
        Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.3f)))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            // Part A: City Portion
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { viewModel.showCitySelectorDialog = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "City",
                    tint = themeColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = city,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (weather.isNotEmpty() || temp.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(8.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                )

                // Part B: Weather/Temperature Portion
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { launchWeather(context) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    val weatherIcon = when (weather) {
                        "多云" -> Icons.Outlined.Cloud
                        "小雨", "雨", "阵雨" -> Icons.Outlined.WaterDrop
                        "雷阵雨" -> Icons.Outlined.Thunderstorm
                        else -> Icons.Outlined.WbSunny
                    }
                    Icon(
                        imageVector = weatherIcon,
                        contentDescription = "Weather",
                        tint = if (weather == "晴") Color(0xFFFBBF24) else themeColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$weather $temp",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FlipNumberBox(number: String, themeColor: Color) {
    Box(
        modifier = Modifier
            .size(width = 86.dp, height = 75.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE10B0F19))
            .drawBehind {
                // Split line representing retro flipping plates horizontal slit
                drawLine(
                    color = Color.Black.copy(alpha = 0.65f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx()
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Giant Flip Card numbers
        Text(
            text = number,
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = themeColor,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Serif
        )
    }
}

@Composable
fun BoldMinimalistClock(
    time: Date,
    themeColor: Color,
    city: String,
    weather: String,
    temp: String,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    val hh = SimpleDateFormat("HH", Locale.getDefault()).format(time)
    val mm = SimpleDateFormat("mm", Locale.getDefault()).format(time)
    val fullDate = SimpleDateFormat("M/dd EEEE", Locale.CHINESE).format(time)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$hh\n$mm",
            fontSize = 85.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            lineHeight = 75.sp,
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier
                .clickable { launchAlarmClock(context) }
                .padding(bottom = 4.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // City Portion
            Text(
                text = city,
                color = themeColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clickable { viewModel.showCitySelectorDialog = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )

            if (weather.isNotEmpty() || temp.isNotEmpty()) {
                Text(
                    text = " • ",
                    color = themeColor.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
                // Weather/Temperature Portion
                Text(
                    text = "$weather $temp",
                    color = themeColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clickable { launchWeather(context) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Text(
                text = " • ",
                color = themeColor.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            Text(
                text = fullDate,
                color = themeColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .clickable { launchCalendar(context) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun AnalogClassicClock(time: Date, themeColor: Color) {
    val context = LocalContext.current
    val cal = Calendar.getInstance().apply { timeInMillis = time.time }
    val hour = cal.get(Calendar.HOUR)
    val min = cal.get(Calendar.MINUTE)
    val sec = cal.get(Calendar.SECOND)

    Box(
        modifier = Modifier
            .size(190.dp)
            .shadow(8.dp, RoundedCornerShape(95.dp))
            .clip(RoundedCornerShape(95.dp))
            .background(Color(0xD20B0F19))
            .clickable { launchAlarmClock(context) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2

            // Draw outer metallic tick ring
            drawCircle(color = themeColor, radius = radius, style = Stroke(width = 3.dp.toPx()))
            
            // Draw internal hours dots ticks
            for (i in 0 until 12) {
                val tickAngle = i * 30 * Math.PI / 180f
                val startX = center.x + Math.cos(tickAngle).toFloat() * (radius - 12.dp.toPx())
                val startY = center.y + Math.sin(tickAngle).toFloat() * (radius - 12.dp.toPx())
                drawCircle(
                    color = if (i % 3 == 0) Color.White else Color.White.copy(alpha = 0.4f),
                    radius = if (i % 3 == 0) 3.5.dp.toPx() else 1.8.dp.toPx(),
                    center = Offset(startX, startY)
                )
            }

            // Calculations
            val hourAngle = ((hour + min / 60f) * 30 - 90) * Math.PI / 180f
            val minAngle = (min * 6 - 90) * Math.PI / 180f
            val secAngle = (sec * 6 - 90) * Math.PI / 180f

            // Hour Hand
            drawLine(
                color = Color.White,
                start = center,
                end = Offset(
                    center.x + Math.cos(hourAngle).toFloat() * (radius * 0.5f),
                    center.y + Math.sin(hourAngle).toFloat() * (radius * 0.5f)
                ),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Minute Hand
            drawLine(
                color = themeColor,
                start = center,
                end = Offset(
                    center.x + Math.cos(minAngle).toFloat() * (radius * 0.7f),
                    center.y + Math.sin(minAngle).toFloat() * (radius * 0.7f)
                ),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Dynamic ticking Orange Second Hand
            drawLine(
                color = Color(0xFFF97316),
                start = center,
                end = Offset(
                    center.x + Math.cos(secAngle).toFloat() * (radius * 0.82f),
                    center.y + Math.sin(secAngle).toFloat() * (radius * 0.82f)
                ),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Draw centerpiece cap pin
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = center)
            drawCircle(color = Color(0xFFF97316), radius = 2.dp.toPx(), center = center)
        }
    }
}
