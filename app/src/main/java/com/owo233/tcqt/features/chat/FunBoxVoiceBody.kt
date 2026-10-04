package com.owo233.tcqt.features.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.service.VoiceSendUtils
import com.owo233.tcqt.host.service.funbox.FunBoxVoiceRepository
import com.owo233.tcqt.ui.component.MaterialTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import java.io.File

/**
 * FunBox 在线语音子页面：浏览分享语音包 → 包内列表/搜索 → 下载（含缓存）→ 发送。
 * 业务照搬 WeKit FunBoxVoiceRepository；UI 按 TCQT 面板风格重写。
 *
 * 线程模型：与 VoiceSendUtils 一致，网络/转码都在自建后台线程跑，
 * 结束后用 Toasts（内部已切 UI 线程）提示；状态用 mutableStateOf 驱动 Compose 刷新。
 */
class FunBoxVoiceBody(
    private val context: android.content.Context,
    private val chatType: Int,
    private val peerUid: String,
    /** 延迟读取主面板「直发原文件」开关（全局生效，含 FunBox 在线语音）。 */
    private val sendOriginalProvider: () -> Boolean,
) {
    /** 页面状态：语音包列表 / 包内列表（含搜索结果）。 */
    private sealed interface Page {
        data object Packs : Page
        data class Items(val title: String) : Page
    }

    private var page by mutableStateOf<Page>(Page.Packs)
    private var packs by mutableStateOf<List<FunBoxVoiceRepository.VoicePack>>(emptyList())
    private var items by mutableStateOf<List<FunBoxVoiceRepository.VoiceItem>>(emptyList())
    private var query by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var sendingId by mutableStateOf<String?>(null)
    private var loadedPacks by mutableStateOf(false)

    @Composable
    fun Content() {
        LaunchedEffect(Unit) {
            if (!loadedPacks && !loading) runLoadPacks()
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // 整页切换动画（顶栏/搜索框/列表一起动）：与主面板「本地 ⇄ 在线」一致
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val goingDeeper = targetState is Page.Items && initialState is Page.Packs
                    if (goingDeeper) {
                        (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                label = "funbox_page",
            ) { current ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 顶栏：返回 + 标题
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (current is Page.Items) {
                            Text(
                                text = "‹ 返回",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            page = Page.Packs
                                            items = emptyList()
                                            error = null
                                        },
                                    )
                                    .padding(end = 12.dp),
                            )
                        }
                        Text(
                            text = when (val p = current) {
                                is Page.Packs -> "FunBox 分享"
                                is Page.Items -> p.title
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (current is Page.Packs) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "搜索在线语音",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            trailingIcon = {
                                Text(
                                    text = "搜索",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                val q = query.trim()
                                                if (q.isNotEmpty()) runSearch(q)
                                            },
                                        )
                                        .padding(horizontal = 10.dp),
                                )
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    error?.let { msg ->
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Box(modifier = Modifier.height(380.dp)) {
                        when (current) {
                            is Page.Packs -> PackList()
                            is Page.Items -> ItemList()
                        }
                        if (loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "加载中…",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PackList() {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // key 用 index 兜底：聚合包（最新上传等）里条目 id 可能重复，用 id 当 key 会崩溃
            itemsIndexed(packs) { _, pack ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { runLoadPack(pack) },
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pack.title,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (packs.isEmpty() && !loading && error == null) {
                item {
                    Text(
                        text = "暂无语音包",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    @Composable
    private fun ItemList() {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(items) { _, item ->
                val isSending = sendingId == item.id
                val cached = remember(item.id) {
                    runCatching { FunBoxVoiceRepository.cachedVoiceFile(context, item) != null }
                        .getOrDefault(false)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !isSending,
                            onClick = { runSend(item) },
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isSending -> "发送中…"
                            cached -> "已缓存 · 发送"
                            else -> "点击发送"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (items.isEmpty() && !loading && error == null) {
                item {
                    Text(
                        text = "语音包为空",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // ---------- 业务动作（后台线程，与 VoiceSendUtils 相同的线程模型） ----------

    private fun runLoadPacks() {
        loading = true
        error = null
        Thread({
            try {
                val result = FunBoxVoiceRepository.listSharedPacks(context)
                packs = result
                loadedPacks = true
            } catch (t: Throwable) {
                error = friendly(t)
                Log.e("$TAG: 语音包列表失败", t)
            } finally {
                loading = false
            }
        }, "FunBox-Packs").start()
    }

    private fun runLoadPack(pack: FunBoxVoiceRepository.VoicePack) {
        loading = true
        error = null
        Thread({
            try {
                val result = FunBoxVoiceRepository.loadSharedPack(context, pack.id)
                items = result
                page = Page.Items(pack.title)
            } catch (t: Throwable) {
                error = friendly(t)
                Log.e("$TAG: 语音包内容失败 pack=${pack.id}", t)
            } finally {
                loading = false
            }
        }, "FunBox-Items").start()
    }

    private fun runSearch(q: String) {
        loading = true
        error = null
        Thread({
            try {
                val result = FunBoxVoiceRepository.searchSharedVoices(context, q)
                items = result
                page = Page.Items("搜索：$q")
            } catch (t: Throwable) {
                error = friendly(t)
                Log.e("$TAG: 搜索失败 q=$q", t)
            } finally {
                loading = false
            }
        }, "FunBox-Search").start()
    }

    /**
     * 下载并发送。默认走内置 silk 转码（保证双方都能播）；
     * 「直发原文件」开启时全局生效——FunBox 语音也原样上传不转码。
     */
    private fun runSend(item: FunBoxVoiceRepository.VoiceItem) {
        if (sendingId != null) return
        sendingId = item.id
        Thread({
            try {
                // 缓存命中直接用，否则下载（JSON 错误嗅探 + 魔数嗅探定扩展名）
                val file: File = FunBoxVoiceRepository.cachedVoiceFile(context, item)
                    ?: FunBoxVoiceRepository.downloadVoice(context, item)
                // sendOriginal 跟随主面板「直发原文件」开关（每次发送时读最新值，全局生效含 FunBox）
                val ok = VoiceSendUtils.sendVoice(chatType, peerUid, file.absolutePath, sendOriginal = sendOriginalProvider())
                if (ok) Toasts.success("语音已发送") else Toasts.error("发送失败，请查看日志")
            } catch (t: Throwable) {
                Log.e("$TAG: 发送失败 id=${item.id}", t)
                Toasts.error(friendly(t))
            } finally {
                sendingId = null
            }
        }, "FunBox-Send").start()
    }

    private fun friendly(t: Throwable): String = t.message?.take(120) ?: "操作失败"

    companion object {
        private const val TAG = "FunBoxVoice"
    }
}
