package com.owo233.tcqt.features.appearance.chatgroups

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.CompatibleComposeDialog
import com.owo233.tcqt.ui.component.DialogForm
import com.owo233.tcqt.ui.component.HostDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal class ChatGroupDeleteDialog(
    context: Context,
    private val session: ChatGroupSession,
    private val groupId: String,
) : CompatibleComposeDialog(context) {
    private val account = session.editingAccount()
    private val group = session.groups.first { it.id == groupId }

    @Composable
    override fun DialogContent() {
        var error by remember { mutableStateOf<String?>(null) }
        HostDialog(isVisible, ::dismissWithAnimation, ::dismissImmediately) {
            DialogForm(title = "删除文件夹“${group.name}”？", actions = {
                ActionButton(onClick = ::dismissWithAnimation, modifier = Modifier.weight(1f)) { Text("取消") }
                ActionButton(onClick = {
                    try {
                        session.save(session.groups.filterNot { it.id == groupId }, account)
                        dismissWithAnimation()
                    } catch (failure: Exception) {
                        error = failure.message ?: "删除失败，请重试"
                    }
                }, modifier = Modifier.weight(1f)) { Text("删除", color = MiuixTheme.colorScheme.error) }
            }) {
                Text("会话仍保留在“全部”中。")
                error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
            }
        }
    }
}
