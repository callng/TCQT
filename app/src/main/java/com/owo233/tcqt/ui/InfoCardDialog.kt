@file:OptIn(ExperimentalFoundationApi::class)

package com.owo233.tcqt.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.owo233.tcqt.ui.miuix.MaterialTheme
import com.owo233.tcqt.ui.miuix.TextButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField as OutlinedTextField

class InfoCardDialog(
    context: Context,
    private val onOpenUser: (String) -> Unit,
    private val onOpenGroup: (String) -> Unit,
    private val onApplyTheme: (String) -> Unit
) : CompatibleComposeDialog(context) {

    @Composable
    override fun DialogContent() {
        var text by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf<String?>(null) }
        val scrollState = rememberScrollState()

        fun validateAndGet(emptyMsg: String, invalidMsg: String, isThemeId: Boolean = false): String? {
            val uin = text.trim()
            if (uin.isEmpty()) {
                errorText = emptyMsg
                return null
            }
            val minVal = if (isThemeId) 1000 else 10000
            val isInvalid = runCatching { uin.toLong() < minVal }.getOrDefault(true)
            if (isInvalid) {
                errorText = invalidMsg
                return null
            }
            return uin
        }

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
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "打开资料卡片",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = text,
                                onValueChange = { newValue ->
                                    text = newValue.filter { it.isDigit() }
                                    errorText = null
                                },
                                label = "QQ号 / 群号",
                                singleLine = true,
                                cornerRadius = 12.dp,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            errorText?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        validateAndGet("请输入群号", "请输入正确的群号")?.let {
                                            dismissWithAnimation()
                                            onOpenGroup(it)
                                        }
                                    }
                                ) {
                                    Text("群聊")
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .combinedClickable(
                                            onClick = {
                                                validateAndGet("请输入QQ号", "请输入正确的账号")?.let {
                                                    dismissWithAnimation()
                                                    onOpenUser(it)
                                                }
                                            },
                                            onLongClick = {
                                                validateAndGet("请输入主题ID", "请输入正确的主题ID", isThemeId = true)?.let {
                                                    dismissWithAnimation()
                                                    onApplyTheme(it)
                                                }
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "用户",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
