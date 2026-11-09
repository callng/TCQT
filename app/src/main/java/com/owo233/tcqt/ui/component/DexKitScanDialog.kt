package com.owo233.tcqt.ui.component

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.owo233.tcqt.core.command.ModuleCommandBus
import com.owo233.tcqt.core.dexkit.DexKitScanState
import com.owo233.tcqt.core.dexkit.DexKitScanPhase
import com.owo233.tcqt.core.dexkit.dexKitScanClockMillis
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HostBridge
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.log.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

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
            DexKitScanDialog(host, state, onClose = ::close, onRestart = ::restart).also {
                activity = host
                dialog = it
                it.show()
            }
        }
            .onFailure {
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
        detach()?.dismiss()
        onClosed()
    }

    private fun restart() {
        if (!state.value.finished || !state.value.restartRequired) return
        detach()?.dismissImmediately()
        onClosed()
        ModuleCommandBus.sendCommand(
            HookEnv.application,
            ModuleCommandBus.CMD_RESTART,
        )
    }

    private fun detach(): DexKitScanDialog? {
        application.unregisterActivityLifecycleCallbacks(this)
        val currentDialog = dialog
        dialog = null
        activity = null
        return currentDialog
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
    private val onRestart: () -> Unit,
) : CompatibleComposeDialog(activity) {
    init {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        // Keep this enabled while dismissal is blocked so every back event follows scan policy.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (scanState.value.canDismiss) onClose()
                }
            },
        )
    }

    @Composable
    override fun DialogContent() {
        val state by scanState.collectAsStateWithLifecycle()
        HostDialog(
            show = isVisible,
            onDismissRequest = { if (state.canDismiss) onClose() },
            onDismissFinished = ::dismissImmediately,
        ) {
            DialogForm(
                title = if (state.finished) "DexKit 扫描结果" else "DexKit 方法特征扫描中",
                actions = { if (state.finished) {
                    Button(
                        onClick = {
                            if (state.restartRequired) {
                                onRestart()
                            } else {
                                onClose()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(if (state.restartRequired) "重启${HookEnv.appName}" else "关闭")
                    }
                } },
            ) {
                if (!state.finished) {
                    DexKitScanProgressContent(state)
                } else {
                    Text(
                        text =
                            buildAnnotatedString {
                                append("共 ${state.total} 项，成功 ")
                                withStyle(
                                    SpanStyle(
                                        color = MiuixTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                ) {
                                    append("${state.succeeded}")
                                }
                                append(" 项，失败 ")
                                withStyle(
                                    SpanStyle(
                                        color =
                                            if (state.failed > 0) MiuixTheme.colorScheme.error
                                            else MiuixTheme.colorScheme.onSurfaceSecondary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                ) {
                                    append("${state.failed}")
                                }
                                append(" 项。")
                            },
                        style = MiuixTheme.textStyles.body2,
                    )
                    DexKitScanTimingSummary(state)
                    Text(
                        text =
                            "宿主：${HookEnv.appName} ${HookEnv.versionName} (${HookEnv.versionCode})\n" +
                                "模块：${TCQTBuild.VER_NAME} (${TCQTBuild.VER_CODE})",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    Text(
                        text =
                            state.errorMessage
                                ?: buildString {
                                    append(
                                        if (state.failed == 0) "全部扫描成功，" else "部分项目扫描失败，相关功能可能不可用。"
                                    )
                                    append(
                                        if (state.restartRequired) "请重启宿主以生效。"
                                        else "扫描结果与上次缓存完全一致，无需重启。"
                                    )
                                },
                        style = MiuixTheme.textStyles.body2,
                    )
                    if (state.failedFeatureNames.isNotEmpty()) {
                        Text(
                            text = "失败功能：\n" + state.failedFeatureNames.joinToString("\n"),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DexKitScanProgressContent(state: DexKitScanState) {
    var nowMillis by remember { mutableLongStateOf(dexKitScanClockMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, state.startedAtMillis, state.finished) {
        if (state.finished) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // The worker can be inside one blocking DexKit query for several seconds.
            // Refresh the visible clock independently of descriptor completion events.
            while (isActive) {
                nowMillis = dexKitScanClockMillis()
                delay(100.milliseconds)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "总进度 ${state.completed} / ${state.total}",
            style = MiuixTheme.textStyles.title4,
            color = MiuixTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = if (state.total == 0) 0f else state.completed.toFloat() / state.total,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "总耗时 ${formatDexKitScanDuration(state.elapsedMillisAt(nowMillis))}",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }

    val feature = state.currentFeature
    if (feature != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildAnnotatedString {
                    append("正在查找 ")
                    withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append(feature.name)
                    }
                },
                style = MiuixTheme.textStyles.body2,
            )
            // A new feature starts its own bar instead of animating down from the last one.
            key(feature.name, feature.startedAtMillis) {
                LinearProgressIndicator(
                    progress = if (feature.total == 0) 0f else feature.completed.toFloat() / feature.total,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = "功能进度 ${feature.completed} / ${feature.total} · 已耗时 " +
                    formatDexKitScanDuration((nowMillis - feature.startedAtMillis).coerceAtLeast(feature.elapsedMillis)),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    } else {
        Text(
            text = when (state.phase) {
                DexKitScanPhase.PREPARING -> "正在准备功能查找…"
                DexKitScanPhase.SCANNING -> "正在切换查找功能…"
                DexKitScanPhase.SAVING -> "正在保存扫描结果…"
            },
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@Composable
internal fun DexKitScanTimingSummary(state: DexKitScanState) {
    Text(
        text = "总耗时 ${formatDexKitScanDuration(state.elapsedMillis)}",
        style = MiuixTheme.textStyles.body2,
    )
    val slowFeatures = state.slowFeatures
    if (slowFeatures.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "耗时较长的功能",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            slowFeatures.forEach { feature ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary)) {
                            append(feature.name)
                        }
                        append(" · ${formatDexKitScanDuration(feature.elapsedMillis)}")
                    },
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
    }
}

private fun formatDexKitScanDuration(millis: Long): String {
    val tenths = millis.coerceAtLeast(0) / 100
    val seconds = "${tenths / 10 % 60}.${tenths % 10} 秒"
    return if (tenths < 600) seconds else "${tenths / 600} 分 $seconds"
}
