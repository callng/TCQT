package com.owo233.tcqt.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.miuix.MaterialTheme
import com.owo233.tcqt.ui.miuix.TextButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

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
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::dismissWithAnimation,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .imePadding()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "设置自定义 GUID",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        TextField(
                            value = guid,
                            onValueChange = { guid = it.take(32) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            label = "32 位 GUID（可为空）",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            cornerRadius = 14.dp,
                        )
                        TextButton(
                            onClick = {
                                guid = buildString(32) {
                                    val hex = "0123456789abcdef"
                                    repeat(32) { append(hex.random()) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("随机生成")
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    dismissWithAnimation()
                                    onRestore()
                                },
                                enabled = restoreEnabled,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("恢复")
                            }
                            TextButton(
                                onClick = ::dismissWithAnimation,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("取消")
                            }
                            Button(
                                onClick = {
                                    dismissWithAnimation()
                                    onSave(guid.trim())
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }
    }
}
