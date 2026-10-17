package com.owo233.tcqt.features.advanced

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.owo233.tcqt.ui.component.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

class GuidEditorDialog(
    context: Context,
    private val initialGuid: String,
    private val restoreEnabled: Boolean,
    private val onSave: (String) -> Unit,
    private val onRestore: () -> Unit,
) : CompatibleComposeDialog(context) {
    @Composable
    override fun DialogContent() {
        var guid by remember { mutableStateOf(initialGuid) }
        var error by remember { mutableStateOf(false) }
        HostDialog(isVisible, ::dismissWithAnimation, ::dismissImmediately) {
            DialogForm(
                title = "设置自定义 GUID",
                actions = {
                    ActionButton(
                        onClick = {
                            dismissWithAnimation()
                            onRestore()
                        },
                        enabled = restoreEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("恢复")
                    }
                    ActionButton(onClick = ::dismissWithAnimation, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    ActionButton(
                        onClick = {
                            val value = guid.trim()
                            if (value.isNotEmpty() && !value.matches(Regex("[a-fA-F0-9]{32}"))) {
                                error = true
                            } else {
                                dismissWithAnimation()
                                onSave(value)
                            }
                        },
                        primary = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("保存")
                    }
                },
            ) {
                TextField(
                    value = guid,
                    onValueChange = {
                        guid = it
                        error = false
                    },
                    label = "32 位 GUID（留空以禁用）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error)
                    Text(
                        "请输入 32 位十六进制字符，或留空以禁用",
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                ActionButton(
                    onClick = {
                        guid =
                            buildString(32) { repeat(32) { append("0123456789abcdef".random()) } }
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("随机生成")
                }
            }
        }
    }
}
