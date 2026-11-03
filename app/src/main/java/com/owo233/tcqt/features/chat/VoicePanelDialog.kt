package com.owo233.tcqt.features.chat

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.host.service.VoiceSendUtils
import com.owo233.tcqt.host.service.api.GroupService
import com.owo233.tcqt.ui.component.CompatibleComposeDialog
import com.owo233.tcqt.ui.component.MaterialTheme
import com.owo233.tcqt.ui.component.TextButton
import com.tencent.qqnt.ntrelation.friendsinfo.api.IFriendsInfoService
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import java.io.File

/**
 * 语音面板对话框：本地音频发送 + FunBox 在线语音两个视图。
 *
 * 复刻自 fork 版本 TCQT 的语音面板功能；在线语音业务照搬 WeKit FunBox 分享。
 */
class VoicePanelDialog(
    private val hostContext: Context,
    private val chatType: Int,
    private val peerUid: String,
) : CompatibleComposeDialog(hostContext) {

    @Composable
    override fun DialogContent() = PanelBody()

    @Composable
    private fun PanelBody() {
        val context = LocalContext.current
        var path by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var sendOriginal by remember { mutableStateOf(false) }
        var showFunbox by remember { mutableStateOf(false) }
        var sessionLabel by remember { mutableStateOf(fallbackLabel()) }

        LaunchedEffect(Unit) { sessionLabel = resolveSessionLabel() }

        val openPicker: () -> Unit = {
            VoiceFilePicker.pick(context) { uri: Uri? ->
                if (uri != null) {
                    val local = copyUriToLocal(context, uri)
                    if (local != null) {
                        path = local
                        error = null
                    } else {
                        error = "无法读取所选文件"
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200)),
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(horizontal = 4.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                        ) {
                            Text(
                                text = if (showFunbox) "在线语音" else "发送语音",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "发送给：$sessionLabel",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))

                            AnimatedContent(
                                targetState = showFunbox,
                                transitionSpec = {
                                    if (targetState) {
                                        (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))) togetherWith
                                                (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(200)))
                                    } else {
                                        (slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                                                (slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200)))
                                    }
                                },
                                label = "panel_view",
                            ) { funboxView ->
                                // AnimatedContent 内部是 Box 语义，必须包一层 Column，
                                // 否则各子元素全部叠在同一位置（重叠 bug 根因）
                                Column(modifier = Modifier.fillMaxWidth()) {
                                if (funboxView) {
                                // ---- FunBox 在线语音视图 ----
                                // remember 缓存实例：页面状态存在实例属性里，重组时不能重建。
                                // sendOriginal 通过 lambda 延迟取值，始终读到开关最新状态
                                val funboxBody = remember(chatType, peerUid) {
                                    FunBoxVoiceBody(context, chatType, peerUid) { sendOriginal }
                                }
                                funboxBody.Content()

                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { showFunbox = false },
                                        ),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = "返回本地语音发送",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                // ---- 本地文件发送视图 ----
                                // 点击调起系统文件选择器（输入框样式：带描边、左对齐）
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { openPicker() })
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    // 输入框样式：左对齐单行文本，超出省略
                                    Text(
                                        text = if (path.isBlank()) "点击选择音频" else File(path).name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 14.dp),
                                        fontSize = 14.sp,
                                        color = if (path.isBlank()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                error?.let { msg ->
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = msg,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                // 直发原文件开关：不转码原样上传，音质 100% 保留。
                                // 副作用：发送者本机无法试听（QQ 本地播放器只认 silk），
                                // 对方接收完全正常
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { sendOriginal = !sendOriginal },
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "直发原文件",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Switch(
                                        checked = sendOriginal,
                                        onCheckedChange = { sendOriginal = it },
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                // 在线语音（FunBox 分享）入口
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { showFunbox = true },
                                        ),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = "在线语音",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Button(
                                        onClick = { dismissWithAnimation() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    ) {
                                        Text("取消", color = MaterialTheme.colorScheme.primary)
                                    }
                                    Button(
                                        onClick = { onSend(path, sendOriginal) { error = it } },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("发送")
                                    }
                                }
                                }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fallbackLabel(): String = when (chatType) {
        1 -> "好友($peerUid)"
        2 -> "群聊($peerUid)"
        else -> "会话($peerUid)"
    }

    /** 群聊 -> 群名称(群号)；私聊 -> 备注名(QQ号)。 */
    private fun resolveSessionLabel(): String = runCatching {
        when (chatType) {
            2 -> {
                val groupUin = runCatching { GroupService.getUinFromUid(peerUid) }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: peerUid
                val troop = runCatching { GroupService.getGroupInfo(groupUin) }.getOrNull()
                val name = troop?.troopname?.takeIf { it.isNotBlank() } ?: "群聊"
                "$name($groupUin)"
            }

            1 -> {
                val info = runCatching {
                    QQInterfaces.api<IFriendsInfoService>()
                        .getFriendsSimpleInfoWithUid(peerUid, null)
                }.getOrNull()
                val name = sequenceOf(info?.friendName, info?.remark, info?.nick)
                    .mapNotNull { it?.takeIf { s -> s.isNotBlank() } }
                    .firstOrNull() ?: "好友"
                val uin = info?.uin?.takeIf { it.isNotBlank() }
                    ?: runCatching { GroupService.getUinFromUid(peerUid) }.getOrNull()
                    ?: peerUid
                "$name($uin)"
            }

            else -> fallbackLabel()
        }
    }.getOrElse { fallbackLabel() }

    private fun copyUriToLocal(context: Context, uri: Uri): String? = runCatching {
        val displayName = queryDisplayName(context, uri)
        val dir = File(context.cacheDir, "voice_panel").apply { mkdirs() }
        val out = File(dir, displayName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        out.absolutePath
    }.getOrNull()

    private fun queryDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    val n = c.getString(idx)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
        val seg = uri.lastPathSegment?.substringAfterLast('/')
        return if (!seg.isNullOrBlank()) seg else "voice_${System.currentTimeMillis()}.mp3"
    }

    private fun onSend(path: String, sendOriginal: Boolean, onError: (String) -> Unit) {
        if (path.isBlank()) {
            onError("请选择语音文件")
            return
        }
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            onError("文件不存在：$path")
            return
        }
        if (!VoiceSendUtils.isSupported(file.name)) {
            onError("不支持的格式，仅支持 ${VoiceSendUtils.getSupportedExtsDesc()}")
            return
        }
        dismissWithAnimation()
        if (VoiceSendUtils.sendVoice(chatType, peerUid, path, sendOriginal)) {
            Toasts.success(if (sendOriginal) "原文件已发送" else "语音已发送")
        } else {
            Toasts.error("发送失败，请查看日志")
        }
    }
}
