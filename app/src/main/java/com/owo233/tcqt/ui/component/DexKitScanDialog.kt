package com.owo233.tcqt.ui.component

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.command.ModuleCommandBus
import com.owo233.tcqt.core.dexkit.DexKitScanState
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HostBridge
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/** Main-thread owner. The scan outlives any individual host Activity or dialog window. */
internal class DexKitScanDialogHost(
    private val application: Application,
    private val state: StateFlow<DexKitScanState>,
    private val onClosed: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private var activity: Activity? = null
    private var dialog: DexKitScanDialog? = null

    fun attach() {
        application.registerActivityLifecycleCallbacks(this)
        // MainFragment.onResume may run after the Activity's resumed callback but before
        // its first window focus. Post once so that startup still displays the dialog.
        HostBridge.topActivity()?.let { host ->
            host.window.decorView.post {
                if (host.window.decorView.isAttachedToWindow) show(host)
            }
        }
    }

    private fun show(host: Activity) {
        if (host.isFinishing || host.isDestroyed) return
        if (activity === host && dialog?.isShowing == true) return
        hide()
        runCatching {
            DexKitScanDialog(host, state, onClose = ::close).also {
                activity = host
                dialog = it
                it.show()
            }
        }.onFailure {
            hide()
            Log.e("DexKit scan dialog failed to show", it)
        }
    }

    private fun hide() {
        dialog?.dismissImmediately()
        dialog = null
        activity = null
    }

    private fun close() {
        if (!state.value.canDismiss) return
        application.unregisterActivityLifecycleCallbacks(this)
        dialog?.dismiss()
        dialog = null
        activity = null
        onClosed()
    }

    override fun onActivityResumed(activity: Activity) = show(activity)

    override fun onActivityPaused(activity: Activity) {
        if (this.activity === activity) hide()
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (this.activity === activity) hide()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

private class DexKitScanDialog(
    activity: Activity,
    private val scanState: StateFlow<DexKitScanState>,
    private val onClose: () -> Unit,
) : CompatibleComposeDialog(activity) {
    init {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        // Keep this enabled while dismissal is blocked so every back event follows scan policy.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (scanState.value.canDismiss) onClose()
            }
        })
    }

    @Composable
    override fun DialogContent() {
        val state by scanState.collectAsState()
        AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (state.canDismiss) onClose() },
                    )
                    .safeDrawingPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            text = if (state.finished) "DexKit 扫描结果" else "DexKit 方法特征扫描中",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!state.finished) {
                            Text(
                                text = "${state.completed} / ${state.total}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            LinearProgressIndicator(
                                progress = if (state.total == 0) 0f
                                    else state.completed.toFloat() / state.total,
                            )
                            Text(
                                text = if (state.completed == state.total) "正在保存扫描结果…"
                                    else "正在查找混淆类和方法，请稍候…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = buildAnnotatedString {
                                    append("共 ${state.total} 项，成功 ")
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold)) { append("${state.succeeded}") }
                                    append(" 项，失败 ")
                                    withStyle(SpanStyle(color = if (state.failed > 0)
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold)) { append("${state.failed}") }
                                    append(" 项。")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "宿主：${HookEnv.appName} ${HookEnv.versionName} (${HookEnv.versionCode})\n" +
                                    "模块：${TCQTBuild.VER_NAME} (${TCQTBuild.VER_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.errorMessage ?: buildString {
                                    append(if (state.failed == 0) "全部扫描成功，" else "部分项目扫描失败，相关功能可能不可用。")
                                    append(if (state.restartRequired) "请重启宿主以生效。"
                                        else "扫描结果与上次缓存完全一致，无需重启。")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = {
                                    if (state.restartRequired) {
                                        ModuleCommandBus.sendCommand(HookEnv.application, ModuleCommandBus.CMD_RESTART)
                                    } else {
                                        onClose()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(if (state.restartRequired) "重启${HookEnv.appName}" else "关闭")
                            }
                        }
                    }
                }
            }
        }
    }
}
