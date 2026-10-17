package com.owo233.tcqt.features.advanced

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.owo233.tcqt.ui.component.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

class InfoCardDialog(
    context: Context,
    private val onOpenUser: (String) -> Unit,
    private val onOpenGroup: (String) -> Unit,
    private val onApplyTheme: (String) -> Unit,
) : CompatibleComposeDialog(context) {
    @Composable
    override fun DialogContent() {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        fun open(min: Long, action: (String) -> Unit) {
            val value = text.trim()
            if ((value.toLongOrNull() ?: 0L) < min) {
                error = if (min == 1000L) "请输入正确的主题 ID" else "请输入正确的 QQ 号或群号"
            } else {
                dismissWithAnimation()
                action(value)
            }
        }
        HostDialog(isVisible, ::dismissWithAnimation, ::dismissImmediately) {
            DialogForm(
                title = "打开资料卡片",
                actions = {
                    ActionButton(onClick = ::dismissWithAnimation, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    ActionButton(
                        onClick = { open(10000, onOpenGroup) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("群聊")
                    }
                    ActionButton(
                        onClick = { open(10000, onOpenUser) },
                        primary = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("用户")
                    }
                },
            ) {
                TextField(
                    value = text,
                    onValueChange = {
                        text = it.filter(Char::isDigit)
                        error = null
                    },
                    label = "QQ 号 / 群号 / 主题 ID",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                error?.let {
                    Text(
                        it,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
                ActionButton(
                    onClick = { open(1000, onApplyTheme) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("应用主题")
                }
            }
        }
    }
}
