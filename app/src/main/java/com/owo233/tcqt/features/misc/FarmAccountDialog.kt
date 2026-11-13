package com.owo233.tcqt.features.misc

import android.content.Context
import android.os.ResultReceiver
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.host.service.FarmAccount
import com.owo233.tcqt.host.service.FarmAccountHost
import com.owo233.tcqt.core.log.ActionErrorStore
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.CompatibleComposeDialog
import com.owo233.tcqt.ui.component.DialogForm
import com.owo233.tcqt.ui.component.HostDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

internal class FarmAccountDialog(
    context: Context,
    private val host: FarmAccountHost,
    private val onReady: suspend (FarmAccount, ResultReceiver) -> Unit,
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
        var error by remember { mutableStateOf<String?>(null) }
        var reload by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()

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
                    ActionButton(onClick = ::dismissWithAnimation, modifier = Modifier.weight(1f)) { Text("取消") }
                    ActionButton(
                        primary = true,
                        modifier = Modifier.weight(1f),
                        enabled = !busy && (selected != null || error != null),
                        onClick = {
                            val account = selected
                            if (account == null) { reload++; return@ActionButton }
                            busy = true
                            error = null
                            preparation = scope.launch {
                                var closingFarm = false
                                try {
                                    val service = host.prepare(account)
                                    closingFarm = true
                                    onReady(account, service)
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
                        },
                    ) { Text(if (busy) "正在准备…" else if (selected == null && error != null) "重试" else "进入农场") }
                },
            ) {
                Text(
                    "选择已登录的 QQ 账号进入农场。当前打开的农场会关闭，请先完成其中的操作。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                if (accounts == null && error == null) Text("正在读取账号…", style = MiuixTheme.textStyles.body2)
                if (accounts?.isEmpty() == true) Text("没有可用账号，请先在 QQ 中登录。", style = MiuixTheme.textStyles.body2)
                val pressColor = MiuixTheme.colorScheme.primary
                CompositionLocalProvider(
                    LocalIndication provides remember(pressColor) { MiuixIndication(pressColor) },
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
                        accounts.orEmpty().forEach { account ->
                            androidx.compose.runtime.key(account.uin) {
                                RadioButtonPreference(
                                    title = account.nickname.ifBlank { "QQ 账号" },
                                    summary = maskAccountNumber(account.uin) + if (account.isCurrent) " · 当前账号" else "",
                                    selected = selected?.uin == account.uin,
                                    enabled = !busy,
                                    radioButtonLocation = RadioButtonLocation.End,
                                    modifier = Modifier.heightIn(min = 64.dp).squircleClip(12.dp),
                                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    onClick = { selected = account; error = null },
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
