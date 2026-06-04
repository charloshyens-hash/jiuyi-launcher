package com.example

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import com.example.weather.CityItem


@Composable
fun CitySelectorDialog(
    viewModel: LauncherViewModel,
    themeColor: Color,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var recentCitiesList by remember { mutableStateOf(viewModel.prefs.getRecentCityObjects()) }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (coarseGranted || fineGranted) {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            val hasGps = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val hasNetwork = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            if (!hasGps && !hasNetwork) {
                Toast.makeText(context, "请开启手机系统定位服务设置以进行定位", Toast.LENGTH_SHORT).show()
                try {
                    val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    context.startActivity(settingsIntent)
                } catch (e: Exception) {}
            } else {
                getCurrentLocationAndFill(context, viewModel)
            }
        } else {
            Toast.makeText(context, "需要定位权限以自动填充城市", Toast.LENGTH_SHORT).show()
        }
    }
    
    val searchResults by viewModel.citySearchResults.collectAsState()
    
    LaunchedEffect(searchText) {
        viewModel.searchCityGeo(searchText)
    }
    
    val popularCities = listOf("北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京", "重庆", "西安", "苏州", "天津")
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF21E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "久以天气 • 城市切换",
                        color = themeColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { onClose() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.weight(1f).height(50.dp),
                        placeholder = {
                            Text(
                                "搜索全世界的城市景点/拼音/英文",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = themeColor
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = themeColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color(0x13FFFFFF),
                            unfocusedContainerColor = Color(0x06FFFFFF)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!hasCoarse && !hasFine) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                        android.Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            } else {
                                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                                val hasGps = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                                val hasNetwork = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                                if (!hasGps && !hasNetwork) {
                                    Toast.makeText(context, "正在打开手机系统定位服务设置，请开启定位...", Toast.LENGTH_LONG).show()
                                    try {
                                        val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        context.startActivity(settingsIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "跳转设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    getCurrentLocationAndFill(context, viewModel)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "地图",
                                tint = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(22.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "定位销柱",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(15.dp).align(Alignment.Center).offset(y = (-4).dp)
                            )
                        }
                    }
                }
                
                if (searchText.trim().isNotEmpty()) {
                    Text(
                        text = "全局定位检索匹配站点（含中文自建与全球索引）：",
                        color = themeColor.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        item {
                            val customStr = searchText.trim()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.searchAndSelectCity(customStr)
                                        viewModel.showCitySelectorDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLocation,
                                    contentDescription = "使用自定义输入",
                                    tint = themeColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "直接使用自定义地名: \"$customStr\"",
                                    color = themeColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        }
                        
                        items(searchResults.size) { i ->
                            val item = searchResults[i]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectCityAndSimulateWeather(item.name, item.lat, item.lng, item.country, item.admin, query = searchText)
                                        viewModel.showCitySelectorDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.name == item.city) Icons.Default.LocationCity else Icons.Default.Landscape,
                                    contentDescription = "类型",
                                    tint = themeColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (item.name == item.city) {
                                        item.city
                                    } else if (item.name.contains(item.city, ignoreCase = true)) {
                                        item.name
                                    } else {
                                        "${item.name} (${item.city})"
                                    },
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (recentCitiesList.isNotEmpty()) {
                            Text(
                                text = "常用城市",
                                color = themeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp, top = 2.dp)
                            )
                            val chunkedRecent = recentCitiesList.chunked(4)
                            for (row in chunkedRecent) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (j in 0 until 4) {
                                        if (j < row.size) {
                                            val c = row[j]
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(themeColor.copy(alpha = 0.15f))
                                                    .border(BorderStroke(1.dp, themeColor.copy(alpha = 0.25f)), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.searchAndSelectCity(c.query)
                                                        viewModel.showCitySelectorDialog = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = c.name,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(start = 4.dp, end = 16.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .align(Alignment.CenterEnd)
                                                        .clickable {
                                                            viewModel.prefs.removeRecentCity(c.name)
                                                            recentCitiesList = viewModel.prefs.getRecentCityObjects()
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "删除",
                                                        tint = Color.White.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        
                        Text(
                            text = "热门城市",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val chunkedPop = popularCities.chunked(4)
                        for (row in chunkedPop) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (c in row) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x0CFFFFFF))
                                            .clickable {
                                                viewModel.searchAndSelectCity(c)
                                                viewModel.showCitySelectorDialog = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = c,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
