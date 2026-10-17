package com.owo233.tcqt.features.advanced

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.owo233.tcqt.ui.component.*
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

class CopyTicketDialog(context: Context, private val onConfirm: () -> Unit) :
    CompatibleComposeDialog(context) {
    @Composable
    override fun DialogContent() {
        var text by remember { mutableStateOf("") }
        val warningMsg =
            "此操作会复制你的敏感数据。这些数据一旦泄露，攻击者将绕过身份验证直接接管你的账号，造成不可逆的资产损失和隐私泄露。\n\n" +
                "此风险不因本工具或模块作者而转移，完全由操作者自行承担。\n" +
                "模块作者不对因用户主动分享或被第三方APP读取内容导致的资产损失、隐私泄露承担任何法律责任。\n\n" +
                "如你已完全理解此行为的致命后果，并确认由自己承担全部责任，请在下方输入“我已知晓风险”以继续。"

        HostDialog(isVisible, ::dismissWithAnimation, ::dismissImmediately) {
            DialogForm(
                title = "复制敏感信息",
                actions = {
                    ActionButton(onClick = ::dismissWithAnimation, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            dismissWithAnimation()
                            onConfirm()
                        },
                        enabled = text.trim() == "我已知晓风险",
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.error,
                                contentColor = MiuixTheme.colorScheme.onError,
                            ),
                    ) {
                        Text("确认复制")
                    }
                },
            ) {
                Text(warningMsg, style = MiuixTheme.textStyles.body2)
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "输入：我已知晓风险",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
