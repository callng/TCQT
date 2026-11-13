package com.owo233.tcqt.features.misc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.ResultReceiver
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.host.service.FarmAccount
import com.owo233.tcqt.host.service.FarmAccountHost
import com.owo233.tcqt.host.service.FarmAccountProtocol
import com.owo233.tcqt.core.log.ActionErrorStore
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.CompatibleComposeDialog
import com.owo233.tcqt.ui.component.DialogForm
import com.owo233.tcqt.ui.component.HostDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

internal class FarmAccountDialog(
    context: Context,
    private val host: FarmAccountHost,
    private val onReady: suspend (FarmAccount?, ResultReceiver) -> Unit,
) : CompatibleComposeDialog(context) {

    var launchAfterDismiss: (() -> Unit)? = null
    private var preparation: Job? = null

    override fun dismiss() {
        if (launchAfterDismiss == null) preparation?.cancel()
        super.dismiss()
    }

    @Composable
    override fun DialogContent() {
        var accounts by remember { mutableStateOf<List<FarmAccount>?>(null) }
        var selected by remember { mutableStateOf<FarmAccount?>(null) }
        var busy by remember { mutableStateOf(false) }
        var launchingCustom by remember { mutableStateOf(false) }
        var copyingUin by remember { mutableStateOf<String?>(null) }
        var customCode by remember { mutableStateOf("") }
        var customError by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var reload by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val hapticFeedback = LocalHapticFeedback.current
        val working = busy || copyingUin != null

        fun enter(account: FarmAccount?, code: String? = null) {
            if (busy || copyingUin != null) return
            val input = if (account == null) {
                try { FarmAccountProtocol.normalizeCustomCode(code.orEmpty()) }
                catch (e: IllegalArgumentException) { customError = e.message; return }
            } else null
            focusManager.clearFocus()
            busy = true
            launchingCustom = account == null
            error = null
            customError = null
            preparation = scope.launch {
                var closingFarm = false
                try {
                    val service = if (account != null) host.prepare(account) else host.prepareCustom(input!!)
                    closingFarm = true
                    onReady(account, service)
                    customCode = ""
                    dismissWithAnimation()
                } catch (_: TimeoutCancellationException) {
                    busy = false
                    error = if (closingFarm) "旧农场未能及时关闭，请退出旧农场后重试。"
                        else "获取登录信息超时，请检查网络后重试。"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ActionErrorStore.report("farm_account_selector", "农场登录准备", e)
                    busy = false
                    error = if (closingFarm) "关闭旧农场失败，请退出农场及其他小游戏后重试。"
                        else "获取登录信息失败，请重试。若仍失败，请先在 QQ 中重新登录这个账号。"
                }
            }
        }

        fun copyCode(account: FarmAccount) {
            if (busy || copyingUin != null) return
            copyingUin = account.uin
            error = null
            preparation = scope.launch {
                try {
                    val code = host.exportCode(account)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    @Suppress("UsePropertyAccessSyntax")
                    clipboard.setPrimaryClip(ClipData.newPlainText("农场 js_code", code).apply {
                        description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                    })
                    Toasts.success("js_code 已复制，请尽快使用")
                } catch (_: TimeoutCancellationException) {
                    error = "获取 js_code 超时，请检查网络后重试。"
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = "复制 js_code 失败，请重试。若仍失败，请先在 QQ 中重新登录这个账号。"
                } finally {
                    copyingUin = null
                }
            }
        }

        LaunchedEffect(reload) {
            error = null
            try {
                accounts = host.accounts()
                selected = accounts?.firstOrNull { it.isCurrent } ?: accounts?.firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "读取账号失败，请重试"
                accounts = null
            }
        }
        HostDialog(isVisible, ::dismissWithAnimation, ::dismissImmediately) {
            DialogForm(
                title = "选择农场账号",
                actions = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextField(
                                value = customCode,
                                onValueChange = { customCode = it; customError = null },
                                label = "自定义 js_code",
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                enabled = !working,
                                visualTransformation = remember { PasswordVisualTransformation() },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { enter(null, customCode) }),
                                modifier = Modifier.weight(1f),
                            )
                            ActionButton(
                                onClick = { enter(null, customCode) },
                                enabled = !working && customCode.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                                modifier = Modifier.width(80.dp).heightIn(min = 56.dp).semantics {
                                    contentDescription = "使用自定义 js_code 进入农场"
                                },
                            ) {
                                if (busy && launchingCustom) CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                                else Text("进入")
                            }
                        }
                        Text(
                            customError ?: "用于其他账号，复制后请尽快使用。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (customError != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionButton(onClick = ::dismissWithAnimation, modifier = Modifier.weight(1f)) { Text("取消") }
                            ActionButton(
                                primary = true,
                                modifier = Modifier.weight(1f),
                                enabled = !working && (selected != null || error != null),
                                onClick = {
                                    val account = selected
                                    if (account == null) { reload++; return@ActionButton }
                                    enter(account)
                                },
                            ) {
                                Text(if (busy && !launchingCustom) "正在准备…"
                                    else if (selected == null && error != null) "重试"
                                    else "进入所选账号")
                            }
                        }
                    }
                },
            ) {
                Text(
                    "选择账号进入农场，右侧可复制 js_code。进入前会关闭当前农场。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                if (accounts == null && error == null) Text("正在读取账号…", style = MiuixTheme.textStyles.body2)
                if (accounts?.isEmpty() == true) Text("没有可用账号，请先在 QQ 中登录。", style = MiuixTheme.textStyles.body2)
                val pressColor = MiuixTheme.colorScheme.primary
                CompositionLocalProvider(
                    LocalIndication provides remember(pressColor) { MiuixIndication(pressColor) },
                ) {
                    Column(Modifier.selectableGroup()) {
                        accounts.orEmpty().forEach { account ->
                            androidx.compose.runtime.key(account.uin) {
                                val isSelected = selected?.uin == account.uin
                                BasicComponent(
                                    title = account.nickname.ifBlank { "QQ 账号" },
                                    summary = maskAccountNumber(account.uin) + if (account.isCurrent) " · 当前账号" else "",
                                    titleColor = BasicComponentDefaults.titleColor(
                                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                                    ),
                                    summaryColor = BasicComponentDefaults.summaryColor(
                                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    ),
                                    enabled = !busy,
                                    role = Role.RadioButton,
                                    modifier = Modifier.heightIn(min = 64.dp).squircleClip(12.dp).semantics {
                                        this.selected = isSelected
                                    },
                                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    onClick = {
                                        selected = account
                                        error = null
                                        hapticFeedback.performHapticFeedback(
                                            if (isSelected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                        )
                                    },
                                    endActions = {
                                        IconButton(
                                            onClick = { copyCode(account) },
                                            enabled = !working,
                                            minWidth = 48.dp,
                                            minHeight = 48.dp,
                                            cornerRadius = 12.dp,
                                        ) {
                                            if (copyingUin == account.uin) CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                                            else Icon(
                                                MiuixIcons.Copy,
                                                contentDescription = "复制${account.nickname.ifBlank { "QQ 账号" }}的 js_code",
                                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                error?.let {
                    Text(
                        it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }

    private fun maskAccountNumber(uin: String): String {
        if (uin.length <= 3) return "*".repeat(uin.length)
        val suffixLength = when {
            uin.length >= 10 -> 3
            uin.length >= 7 -> 2
            else -> 1
        }
        return uin.take(2) + "*".repeat(uin.length - 2 - suffixLength) + uin.takeLast(suffixLength)
    }
}
