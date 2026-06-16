package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ─────────────────────────────────────────────────────────────────────────────
// 内部页面枚举
// ─────────────────────────────────────────────────────────────────────────────
private enum class VaultScreen {
    ENTRY,
    MANAGE_HIDE,
    VAULT_HOME,
    SETTINGS,
    SECURITY_QUESTION
}

// ─────────────────────────────────────────────────────────────────────────────
// 顶层入口：由 LauncherAppDrawer 调用
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppVaultEntryPoint(
    viewModel: LauncherViewModel,
    themeColor: Color,
    iconPackFilter: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vault = remember { AppVaultManager(context) }

    var screen by remember { mutableStateOf(VaultScreen.ENTRY) }

    LaunchedEffect(Unit) {
        screen = if (vault.hasPassword) VaultScreen.ENTRY else VaultScreen.MANAGE_HIDE
    }

    when (screen) {
        VaultScreen.ENTRY -> {
            VaultPasswordVerifyDialog(
                vault = vault,
                themeColor = themeColor,
                onDismiss = onDismiss,
                onVerified = {
                    val hasHidden = viewModel.hiddenPackagesFlow.value.isNotEmpty()
                    screen = if (hasHidden) VaultScreen.VAULT_HOME else VaultScreen.MANAGE_HIDE
                }
            )
        }
        VaultScreen.MANAGE_HIDE -> {
            ManageHideScreen(
                viewModel = viewModel,
                themeColor = themeColor,
                iconPackFilter = iconPackFilter,
                onCancel = onDismiss,
                onConfirm = { screen = VaultScreen.VAULT_HOME }
            )
        }
        VaultScreen.VAULT_HOME -> {
            VaultHomeScreen(
                viewModel = viewModel,
                themeColor = themeColor,
                iconPackFilter = iconPackFilter,
                vault = vault,
                onAddClick = { screen = VaultScreen.MANAGE_HIDE },
                onSettingsClick = { screen = VaultScreen.SETTINGS },
                onDismiss = onDismiss
            )
        }
        VaultScreen.SETTINGS -> {
            VaultSettingsScreen(
                vault = vault,
                themeColor = themeColor,
                viewModel = viewModel,
                onBack = { screen = VaultScreen.VAULT_HOME },
                onResetDone = onDismiss,
                onGoSecurityQuestion = { screen = VaultScreen.SECURITY_QUESTION }
            )
        }
        VaultScreen.SECURITY_QUESTION -> {
            SecurityQuestionScreen(
                vault = vault,
                themeColor = themeColor,
                onDone = { screen = VaultScreen.VAULT_HOME }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 密码验证弹窗
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VaultPasswordVerifyDialog(
    vault: AppVaultManager,
    themeColor: Color,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    VaultDialog(onDismiss = onDismiss) {
        Text("隐藏应用密码", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        VaultTextField(
            value = input,
            onValueChange = { input = it; error = "" },
            placeholder = "请输入密码",
            isPassword = true
        )
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF6B6B), fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) { Text("取消 ❎", color = Color.White) }
            Button(
                onClick = {
                    if (vault.verifyPassword(input)) onVerified()
                    else error = "密码错误，请重试"
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) { Text("确定 ✅", color = Color.Black) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 页面1：管理应用隐藏状态（无密码弹窗，直接确认后进入隐藏应用页）
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ManageHideScreen(
    viewModel: LauncherViewModel,
    themeColor: Color,
    iconPackFilter: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val allApps by viewModel.appList.collectAsState()
    val hiddenPkgs by viewModel.hiddenPackagesFlow.collectAsState()
    var tempSelected by remember { mutableStateOf(hiddenPkgs.toSet()) }

    val sorted = remember(allApps) {
        allApps.sortedWith(AppSortUtils.getAlphaComparator())
    }
    val itemsPerPage = 16
    val pages = maxOf(1, (sorted.size + itemsPerPage - 1) / itemsPerPage)
    val pagerState = rememberPagerState(pageCount = { pages })

    VaultFullScreenDialog(onDismiss = onCancel) {
        VaultTopBar(title = "管理应用隐藏状态", onBack = onCancel, themeColor = themeColor)
        Text(
            "勾选的应用将在应用列表中隐藏，取消勾选即可恢复显示。",
            color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val slice = sorted.drop(page * itemsPerPage).take(itemsPerPage)
            AppGridSelectable(
                apps = slice,
                selected = tempSelected,
                iconPackFilter = iconPackFilter,
                themeColor = themeColor,
                onToggle = { pkg ->
                    tempSelected = if (tempSelected.contains(pkg))
                        tempSelected - pkg else tempSelected + pkg
                }
            )
        }

        PageDots(pagerState.currentPage, pages, themeColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) { Text("取消", color = Color.White) }
            Button(
                onClick = {
                    viewModel.setHiddenPackages(tempSelected)
                    onConfirm()
                },
                enabled = tempSelected.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) { Text("确定", color = Color.Black) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 页面2：隐藏应用主界面
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VaultHomeScreen(
    viewModel: LauncherViewModel,
    themeColor: Color,
    iconPackFilter: String,
    vault: AppVaultManager,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hiddenPkgs by viewModel.hiddenPackagesFlow.collectAsState()
    val allApps by viewModel.appList.collectAsState()
    val hiddenApps = remember(allApps, hiddenPkgs) {
        allApps.filter { hiddenPkgs.contains(it.packageName) }
    }

    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showPasswordSuccessDialog by remember { mutableStateOf(false) }

    val itemsPerPage = 16
    val pages = maxOf(1, (hiddenApps.size + itemsPerPage - 1) / itemsPerPage)
    val pagerState = rememberPagerState(pageCount = { pages })

    VaultFullScreenDialog(onDismiss = onDismiss) {
        VaultTopBar(title = "隐藏应用", onBack = onDismiss, themeColor = themeColor)

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val slice = hiddenApps.drop(page * itemsPerPage).take(itemsPerPage)
            AppGridLaunchable(
                apps = slice,
                iconPackFilter = iconPackFilter,
                themeColor = themeColor,
                onLaunch = { pkg ->
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent?.let { context.startActivity(it) }
                }
            )
        }

        PageDots(pagerState.currentPage, pages, themeColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VaultBottomIconBtn(
                icon = Icons.Default.Add,
                label = "添加",
                tint = themeColor,
                onClick = onAddClick
            )
            // 锁图标：点击才弹设置密码弹窗
            val lockIcon = if (vault.hasPassword) Icons.Default.Lock else Icons.Default.LockOpen
            VaultBottomIconBtn(
                icon = lockIcon,
                label = if (vault.hasPassword) "已加密" else "未加密",
                tint = if (vault.hasPassword) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.6f),
                onClick = {
                    if (!vault.hasPassword) showSetPasswordDialog = true
                }
            )
            VaultBottomIconBtn(
                icon = Icons.Default.Settings,
                label = "设置",
                tint = Color.White.copy(alpha = 0.8f),
                onClick = onSettingsClick
            )
        }
    }

    if (showSetPasswordDialog) {
        SetPasswordDialog(
            vault = vault,
            themeColor = themeColor,
            onDismiss = { showSetPasswordDialog = false },
            onSuccess = { showSetPasswordDialog = false; showPasswordSuccessDialog = true }
        )
    }

    if (showPasswordSuccessDialog) {
        VaultDialog(onDismiss = { showPasswordSuccessDialog = false }) {
            Text("密码锁定", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "密码设置成功！可设置密保方便您找回密码",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showPasswordSuccessDialog = false },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) { Text("不了", color = Color.White) }
                Button(
                    onClick = { showPasswordSuccessDialog = false; onSettingsClick() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) { Text("去设置", color = Color.Black) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 页面3：隐藏应用设置
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VaultSettingsScreen(
    vault: AppVaultManager,
    themeColor: Color,
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onResetDone: () -> Unit,
    onGoSecurityQuestion: () -> Unit
) {
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showNoPasswordToast by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotVerified by remember { mutableStateOf(false) }

    VaultFullScreenDialog(onDismiss = onBack) {
        VaultTopBar(title = "隐藏应用设置", onBack = onBack, themeColor = themeColor)
        Spacer(Modifier.height(12.dp))

        VaultSettingsItem(
            icon = Icons.Default.Key,
            label = "修改密码",
            themeColor = themeColor,
            onClick = {
                if (!vault.hasPassword) showNoPasswordToast = true
                else showChangePasswordDialog = true
            }
        )
        VaultSettingsItem(
            icon = Icons.Default.Security,
            label = "密保问题设置",
            themeColor = themeColor,
            onClick = onGoSecurityQuestion
        )
        VaultSettingsItem(
            icon = Icons.Default.RestartAlt,
            label = "重置隐藏应用",
            themeColor = themeColor,
            tintOverride = Color(0xFFFF6B6B),
            onClick = { showResetDialog = true }
        )

        if (showNoPasswordToast) {
            VaultDialog(onDismiss = { showNoPasswordToast = false }) {
                Text("尚未设置隐藏应用密码", color = Color.White, fontSize = 15.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showNoPasswordToast = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) { Text("知道了", color = Color.Black) }
            }
        }
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            vault = vault,
            themeColor = themeColor,
            skipOldVerify = forgotVerified,
            onDismiss = { showChangePasswordDialog = false; forgotVerified = false },
            onForgot = { showChangePasswordDialog = false; showForgotDialog = true }
        )
    }

    if (showForgotDialog) {
        SecurityVerifyDialog(
            vault = vault,
            themeColor = themeColor,
            onDismiss = { showForgotDialog = false },
            onVerified = {
                showForgotDialog = false
                forgotVerified = true
                showChangePasswordDialog = true
            }
        )
    }

    if (showResetDialog) {
        ResetVaultDialog(
            vault = vault,
            themeColor = themeColor,
            viewModel = viewModel,
            onDismiss = { showResetDialog = false },
            onDone = onResetDone
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 页面4：密保问题设置
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SecurityQuestionScreen(
    vault: AppVaultManager,
    themeColor: Color,
    onDone: () -> Unit
) {
    val questionPool = listOf(
        "你的出生城市是？", "你的第一所学校名称？", "你的母亲姓名？",
        "你的父亲姓名？", "你最喜欢的城市？", "你第一只宠物名字？",
        "你的生日日期？", "你最喜欢的食物？", "你最好的朋友名字？"
    )
    val existing = remember { vault.getSecurityQuestions() }
    var q1 by remember { mutableStateOf(existing.getOrNull(0)?.first ?: "") }
    var a1 by remember { mutableStateOf(existing.getOrNull(0)?.second ?: "") }
    var q2 by remember { mutableStateOf(existing.getOrNull(1)?.first ?: "") }
    var a2 by remember { mutableStateOf(existing.getOrNull(1)?.second ?: "") }
    var q3 by remember { mutableStateOf(existing.getOrNull(2)?.first ?: "") }
    var a3 by remember { mutableStateOf(existing.getOrNull(2)?.second ?: "") }
    var error by remember { mutableStateOf("") }

    VaultFullScreenDialog(onDismiss = onDone) {
        VaultTopBar(title = "设置密保问题", onBack = onDone, themeColor = themeColor)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SecurityQuestionGroup(index = 1, question = q1, answer = a1,
                pool = questionPool, excludeSelected = listOf(q2, q3),
                onQuestionChange = { q1 = it }, onAnswerChange = { a1 = it },
                themeColor = themeColor)
            SecurityQuestionGroup(index = 2, question = q2, answer = a2,
                pool = questionPool, excludeSelected = listOf(q1, q3),
                onQuestionChange = { q2 = it }, onAnswerChange = { a2 = it },
                themeColor = themeColor)
            SecurityQuestionGroup(index = 3, question = q3, answer = a3,
                pool = questionPool, excludeSelected = listOf(q1, q2),
                onQuestionChange = { q3 = it }, onAnswerChange = { a3 = it },
                themeColor = themeColor)

            Text(
                "注：该方法为唯一找回密码方式，请务必填写真实信息。",
                color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFFF6B6B), fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        Button(
            onClick = {
                val pairs = listOf(q1 to a1, q2 to a2, q3 to a3)
                    .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                if (pairs.isEmpty()) { error = "至少填写 1 组密保问答"; return@Button }
                vault.saveSecurityQuestions(pairs)
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
            shape = RoundedCornerShape(20.dp)
        ) { Text("完成", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 子弹窗组件
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetPasswordDialog(
    vault: AppVaultManager,
    themeColor: Color,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var pw1 by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    VaultDialog(onDismiss = onDismiss) {
        Text("设置隐藏应用密码", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        VaultTextField(value = pw1, onValueChange = { pw1 = it; error = "" },
            placeholder = "请输入密码，长度至少4位", isPassword = true)
        Spacer(Modifier.height(10.dp))
        VaultTextField(value = pw2, onValueChange = { pw2 = it; error = "" },
            placeholder = "请再次输入密码", isPassword = true)
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF6B6B), fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) { Text("取消 ❎", color = Color.White) }
            Button(
                onClick = {
                    when {
                        pw1.length < 4 -> error = "密码长度至少 4 位"
                        pw1 != pw2 -> error = "两次密码不一致"
                        else -> { vault.setPassword(pw1); onSuccess() }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) { Text("确定 ✅", color = Color.Black) }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    vault: AppVaultManager,
    themeColor: Color,
    skipOldVerify: Boolean = false,
    onDismiss: () -> Unit,
    onForgot: () -> Unit
) {
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var newPw2 by remember { mutableStateOf("") }
    // 密保验证通过后跳过旧密码验证步骤
    var step by remember { mutableIntStateOf(if (skipOldVerify) 1 else 0) }
    var error by remember { mutableStateOf("") }

    VaultDialog(onDismiss = onDismiss) {
        Text("修改隐藏应用密码", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (step == 0) {
            VaultTextField(value = oldPw, onValueChange = { oldPw = it; error = "" },
                placeholder = "请输入隐藏应用密码", isPassword = true)
        } else {
            VaultTextField(value = newPw, onValueChange = { newPw = it; error = "" },
                placeholder = "请输入新密码，长度至少4位", isPassword = true)
            Spacer(Modifier.height(10.dp))
            VaultTextField(value = newPw2, onValueChange = { newPw2 = it; error = "" },
                placeholder = "请再次输入新密码", isPassword = true)
        }
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF6B6B), fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(20.dp))
        if (step == 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onForgot) {
                    Text("忘记密码", color = themeColor, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = Color.White.copy(alpha = 0.6f))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (vault.verifyPassword(oldPw)) step = 1
                        else error = "密码错误"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) { Text("确定", color = Color.Black) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) { Text("取消 ❎", color = Color.White) }
                Button(
                    onClick = {
                        when {
                            newPw.length < 4 -> error = "新密码长度至少 4 位"
                            newPw != newPw2 -> error = "两次密码不一致"
                            else -> { vault.setPassword(newPw); onDismiss() }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) { Text("确定 ✅", color = Color.Black) }
            }
        }
    }
}

@Composable
private fun SecurityVerifyDialog(
    vault: AppVaultManager,
    themeColor: Color,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    val questions = remember { vault.getSecurityQuestions() }
    if (questions.isEmpty()) {
        VaultDialog(onDismiss = onDismiss) {
            Text(
                "未设置密保问题，无法通过密保找回密码。",
                color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) { Text("知道了", color = Color.Black) }
        }
        return
    }
    var answers by remember { mutableStateOf(List(questions.size) { "" }) }
    var error by remember { mutableStateOf("") }

    VaultDialog(onDismiss = onDismiss) {
        Text("密保验证", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        questions.forEachIndexed { i, (q, _) ->
            Text(q, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp))
            VaultTextField(
                value = answers[i],
                onValueChange = { v -> answers = answers.toMutableList().also { it[i] = v }; error = "" },
                placeholder = "答案",
                isPassword = false
            )
            Spacer(Modifier.height(10.dp))
        }
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF6B6B), fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) { Text("取消", color = Color.White) }
            Button(
                onClick = {
                    val provided = questions.mapIndexed { i, (q, _) -> q to answers[i] }
                    if (vault.verifySecurityAnswers(provided)) onVerified()
                    else error = "答案不正确，请重试"
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) { Text("验证", color = Color.Black) }
        }
    }
}

@Composable
private fun ResetVaultDialog(
    vault: AppVaultManager,
    themeColor: Color,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var pwInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    VaultDialog(onDismiss = onDismiss) {
        Text("重置隐藏应用", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "将应用还原到应用程序当中去，并删除密码与密保",
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
        )
        if (vault.hasPassword) {
            Spacer(Modifier.height(12.dp))
            VaultTextField(value = pwInput, onValueChange = { pwInput = it; error = "" },
                placeholder = "请输入隐藏应用密码", isPassword = true)
            if (error.isNotEmpty()) {
                Text(error, color = Color(0xFFFF6B6B), fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) { Text("取消", color = Color.White) }
            Button(
                onClick = {
                    if (vault.hasPassword && !vault.verifyPassword(pwInput)) {
                        error = "密码错误"; return@Button
                    }
                    viewModel.setHiddenPackages(emptySet())
                    vault.clearAll()
                    onDone()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
            ) { Text("确定", color = Color.White) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 通用小组件
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppGridSelectable(
    apps: List<AppModel>,
    selected: Set<String>,
    iconPackFilter: String,
    themeColor: Color,
    onToggle: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        apps.chunked(4).forEach { rowApps ->
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                rowApps.forEach { app ->
                    val isSelected = selected.contains(app.packageName)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(6.dp)
                            .clickable { onToggle(app.packageName) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                IconStylingCard(
                                    app = app, filter = iconPackFilter,
                                    themeColor = themeColor,
                                    modifier = Modifier.size(52.dp)
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(themeColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(app.label, color = Color.White, fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
                repeat(4 - rowApps.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppGridLaunchable(
    apps: List<AppModel>,
    iconPackFilter: String,
    themeColor: Color,
    onLaunch: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        apps.chunked(4).forEach { rowApps ->
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                rowApps.forEach { app ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(6.dp)
                            .clickable { onLaunch(app.packageName) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconStylingCard(
                                app = app, filter = iconPackFilter,
                                themeColor = themeColor,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(app.label, color = Color.White, fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
                repeat(4 - rowApps.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, total: Int, themeColor: Color) {
    if (total <= 1) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 16.dp else 6.dp, 6.dp)
                    .clip(CircleShape)
                    .background(if (i == current) themeColor else Color.White.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
private fun VaultTopBar(title: String, onBack: () -> Unit, themeColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBackIosNew, null,
                tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(
            title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(40.dp))
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
}

@Composable
private fun VaultBottomIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

@Composable
private fun VaultSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    themeColor: Color,
    tintOverride: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tintOverride ?: themeColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tintOverride ?: Color.White, fontSize = 15.sp,
            modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
    }
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.07f),
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun SecurityQuestionGroup(
    index: Int,
    question: String,
    answer: String,
    pool: List<String>,
    excludeSelected: List<String>,
    onQuestionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    themeColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val available = pool.filter { it !in excludeSelected || it == question }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("问题 $index", color = themeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    question.ifBlank { "选择密保问题" },
                    color = if (question.isBlank()) Color.White.copy(alpha = 0.4f) else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.White.copy(alpha = 0.5f))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF2A2A2A))
            ) {
                available.forEach { q ->
                    DropdownMenuItem(
                        text = { Text(q, color = Color.White, fontSize = 13.sp) },
                        onClick = { onQuestionChange(q); expanded = false }
                    )
                }
            }
        }
        VaultTextField(value = answer, onValueChange = onAnswerChange,
            placeholder = "输入答案", isPassword = false)
    }
}

@Composable
private fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
        },
        visualTransformation = if (isPassword)
            PasswordVisualTransformation()
        else
            androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White.copy(alpha = 0.4f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun VaultDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E1E1E))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun VaultFullScreenDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE131313))
                .statusBarsPadding()
                .navigationBarsPadding(),
            content = content
        )
    }
}