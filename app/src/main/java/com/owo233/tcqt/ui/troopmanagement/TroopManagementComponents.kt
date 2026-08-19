package com.owo233.tcqt.ui.troopmanagement

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.ext.copyToClipboard
import com.owo233.tcqt.hooks.base.Toasts
import com.owo233.tcqt.ui.miuix.MaterialTheme
import com.owo233.tcqt.ui.miuix.OutlinedButton
import com.owo233.tcqt.ui.miuix.TextButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField as OutlinedTextField

private data class ManagementButtonData(
    val text: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
internal fun MainMenuView(
    memberUin: String,
    memberNick: String,
    memberUid: String,
    roles: TroopMemberRoles?,
    isLoading: Boolean,
    onEnterProfile: () -> Unit,
    onRecall: () -> Unit,
    onSetAdmin: () -> Unit,
    onCancelAdmin: () -> Unit,
    onSetMute: () -> Unit,
    onCancelMute: () -> Unit,
    onSetTitle: () -> Unit,
    onSetCard: () -> Unit,
    onKick: () -> Unit,
    onKickBlock: () -> Unit,
    onMuteAll: () -> Unit,
    onUnmuteAll: () -> Unit
) {
    val context = LocalContext.current
    val isDark = HookEnv.isNightMode()

    val customPrimary = MaterialTheme.colorScheme.primary
    val customError = MaterialTheme.colorScheme.error
    val customGreen = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val permissions = roles?.permissions(HookEnv.isQQ())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "群管菜单",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        InfoRow(label = "Uin", value = memberUin) {
            context.copyToClipboard(memberUin, false)
            Toasts.success("已复制Uin")
        }
        Spacer(modifier = Modifier.height(8.dp))
        InfoRow(label = "Uid", value = memberUid) {
            context.copyToClipboard(memberUid, false)
            Toasts.success("已复制Uid")
        }
        Spacer(modifier = Modifier.height(8.dp))
        InfoRow(label = "Name", value = memberNick) {
            context.copyToClipboard(memberNick, false)
            Toasts.success("已复制Name")
        }
        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "buttons_loading_transition"
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            size = 36.dp,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在获取群管列表...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (permissions != null) {
                val perms = permissions
                val managementButtons = remember(
                    perms,
                    customPrimary,
                    customError,
                    customGreen
                ) {
                    buildList {
                        add(ManagementButtonData("打开资料页", customPrimary, onEnterProfile))

                        if (perms.canRecall) {
                            add(ManagementButtonData("撤回群消息", customPrimary, onRecall))
                        }

                        if (perms.canSetAdmin) {
                            add(ManagementButtonData("设置管理员", customGreen, onSetAdmin))
                        }
                        if (perms.canCancelAdmin) {
                            add(ManagementButtonData("取消管理员", customGreen, onCancelAdmin))
                        }

                        if (perms.canMute) {
                            add(ManagementButtonData("设置禁言", customPrimary, onSetMute))
                            add(ManagementButtonData("解除禁言", customPrimary, onCancelMute))
                        }

                        if (perms.canSetTitle) {
                            add(ManagementButtonData("设置头衔", customPrimary, onSetTitle))
                        }

                        if (perms.canEditCard) {
                            add(ManagementButtonData("修改名片", customPrimary, onSetCard))
                        }

                        if (perms.canKick) {
                            add(ManagementButtonData("踢出本群", customError, onKick))
                            add(ManagementButtonData("踢出并拉黑", customError, onKickBlock))
                        }

                        if (perms.canMuteAll) {
                            add(ManagementButtonData("全员禁言", customError, onMuteAll))
                            add(ManagementButtonData("全员解禁", customGreen, onUnmuteAll))
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val chunks = managementButtons.chunked(2)
                    chunks.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { button ->
                                ManagementButton(
                                    text = button.text,
                                    color = button.color,
                                    onClick = button.onClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    ManagementButton(
                        text = "打开资料页",
                        color = customPrimary,
                        onClick = onEnterProfile,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "获取群管列表失败~",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

private enum class MuteInputMode { Wheels, Seconds }

@Composable
internal fun MuteDurationMenuView(
    initialValue: Int,
    onBack: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var mode by remember { mutableStateOf(MuteInputMode.Wheels) }
    val initialParts = remember(initialValue) {
        initialValue.coerceIn(MUTE_MIN_SECONDS, MUTE_MAX_SECONDS).toMuteDurationParts()
    }
    var days by remember { mutableIntStateOf(initialParts.days) }
    var hours by remember { mutableIntStateOf(initialParts.hours) }
    var minutes by remember { mutableIntStateOf(initialParts.minutes) }
    var seconds by remember { mutableIntStateOf(initialParts.seconds) }
    var secondsText by remember {
        mutableStateOf(initialValue.coerceIn(MUTE_MIN_SECONDS, MUTE_MAX_SECONDS).toString())
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    val wheelSeconds = if (days == 30) {
        MUTE_MAX_SECONDS
    } else {
        MuteDurationParts(days, hours, minutes, seconds).totalSeconds
    }
    val parsedSeconds = secondsText.toIntOrNull()
    val secondsValid = parsedSeconds != null && parsedSeconds in MUTE_MIN_SECONDS..MUTE_MAX_SECONDS
    val secondsError = when {
        secondsText.isBlank() -> "请输入禁言秒数"
        parsedSeconds == null -> "请输入有效的整数秒"
        !secondsValid -> "禁言时长需在 $MUTE_MIN_SECONDS ~ $MUTE_MAX_SECONDS 秒之间"
        else -> null
    }

    LaunchedEffect(mode) {
        if (mode == MuteInputMode.Seconds) {
            secondsText = wheelSeconds.toString()
        } else {
            val parsed = secondsText.toIntOrNull()
            if (parsed != null) {
                val target = parsed.coerceIn(MUTE_MIN_SECONDS, MUTE_MAX_SECONDS)
                if (target == MUTE_MAX_SECONDS && days == 30) {
                    days = 30
                } else {
                    val parts = target.toMuteDurationParts()
                    days = parts.days
                    hours = parts.hours
                    minutes = parts.minutes
                    seconds = parts.seconds
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onBack()
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("返回", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "设置禁言时长",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        TabRowWithContour(
            tabs = listOf("快捷选择", "秒数输入"),
            selectedTabIndex = if (mode == MuteInputMode.Wheels) 0 else 1,
            onTabSelected = {
                keyboardController?.hide()
                mode = if (it == 0) MuteInputMode.Wheels else MuteInputMode.Seconds
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        when (mode) {
            MuteInputMode.Wheels -> {
                val dayLocked = days == 30
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DurationWheelColumn(
                        label = "天",
                        value = days,
                        range = 0..30,
                        enabled = true,
                        onValueChange = { newDays ->
                            days = newDays
                        },
                        modifier = Modifier.weight(1f)
                    )
                    DurationWheelColumn(
                        label = "时",
                        value = hours,
                        range = 0..23,
                        enabled = !dayLocked,
                        onValueChange = { hours = it },
                        modifier = Modifier.weight(1f)
                    )
                    DurationWheelColumn(
                        label = "分",
                        value = minutes,
                        range = 0..59,
                        enabled = !dayLocked,
                        onValueChange = { minutes = it },
                        modifier = Modifier.weight(1f)
                    )
                    DurationWheelColumn(
                        label = "秒",
                        value = seconds,
                        range = 1..59,
                        enabled = !dayLocked,
                        onValueChange = { seconds = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (dayLocked) {
                        "将被禁言：30天（共 $MUTE_MAX_SECONDS 秒）"
                    } else {
                        "将被禁言：${MuteDurationParts(days, hours, minutes, seconds).toDisplayString()}（共 $wheelSeconds 秒）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "禁言规则：最低 $MUTE_MIN_SECONDS 秒，最高 30 天（$MUTE_MAX_SECONDS 秒）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            MuteInputMode.Seconds -> {
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = {
                        secondsText = it.filter { c -> c.isDigit() }
                    },
                    label = "禁言时长（秒）",
                    useLabelAsPlaceholder = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    cornerRadius = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                if (secondsError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = secondsError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                keyboardController?.hide()
                when (mode) {
                    MuteInputMode.Wheels -> {
                        if (wheelSeconds in MUTE_MIN_SECONDS..MUTE_MAX_SECONDS) {
                            onConfirm(wheelSeconds)
                        }
                    }
                    MuteInputMode.Seconds -> {
                        val parsed = secondsText.toIntOrNull()
                        if (parsed != null && parsed in MUTE_MIN_SECONDS..MUTE_MAX_SECONDS) {
                            onConfirm(parsed)
                        }
                    }
                }
            },
            enabled = when (mode) {
                MuteInputMode.Wheels -> wheelSeconds in MUTE_MIN_SECONDS..MUTE_MAX_SECONDS
                MuteInputMode.Seconds -> secondsValid
            },
            cornerRadius = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确定", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DurationWheelColumn(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
            NumberPicker(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                range = range,
                visibleItemCount = 5,
                wrapAround = true,
                itemHeight = 40.dp,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    }
}

@Composable
internal fun InputMenuView(
    title: String,
    label: String,
    hint: String,
    initialValue: String,
    keyboardType: KeyboardType,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onBack()
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("返回", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = if (keyboardType == KeyboardType.Number) {
                    it.filter { c -> c.isDigit() }
                } else {
                    it
                }
            },
            label = hint.ifBlank { label },
            useLabelAsPlaceholder = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            cornerRadius = 12.dp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                keyboardController?.hide()
                onConfirm(text)
            },
            cornerRadius = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("确定", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ConfirmMenuView(
    title: String,
    message: String,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("取消", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onConfirm,
                cornerRadius = 14.dp,
                colors = ButtonDefaults.buttonColors(color = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Text("确定", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "📋",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManagementButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
