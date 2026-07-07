package com.owo233.tcqt.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CopyTicketDialog(
    context: Context,
    private val onConfirm: () -> Unit
) : CompatibleComposeDialog(context) {

    @Composable
    override fun DialogContent() {
        var text by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf<String?>(null) }
        val scrollState = rememberScrollState()

        val warningMsg = "此操作会复制你的敏感数据。这些数据一旦泄露，攻击者将绕过身份验证直接接管你的账号，造成不可逆的资产损失和隐私泄露。\n\n" +
                "此风险不因本工具或模块作者而转移，完全由操作者自行承担。\n" +
                "模块作者不对因用户主动分享或被第三方APP读取内容导致的资产损失、隐私泄露承担任何法律责任。\n\n" +
                "如你已完全理解此行为的致命后果，并确认由自己承担全部责任，请在下方输入“我已知晓风险”以继续。"

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::dismissWithAnimation
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} // Consume click
                            ),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "严重安全警告",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = warningMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            OutlinedTextField(
                                value = text,
                                onValueChange = { newValue ->
                                    text = newValue
                                    errorText = null
                                },
                                label = { Text("确认输入") },
                                placeholder = { Text("输入：我已知晓风险") },
                                isError = errorText != null,
                                supportingText = errorText?.let { { Text(it) } },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val userInput = text.trim()
                                        val expectedText = "我已知晓风险"
                                        if (userInput != expectedText) {
                                            errorText = "请输入“${expectedText}”以确认操作"
                                        } else {
                                            dismissWithAnimation()
                                            onConfirm()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("确认复制")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
