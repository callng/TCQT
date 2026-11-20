package com.owo233.tcqt.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.service.api.GroupSignInService
import com.owo233.tcqt.host.service.api.parseGroupNumbers
import com.owo233.tcqt.ui.component.ActionButton
import com.owo233.tcqt.ui.component.DialogForm
import com.owo233.tcqt.ui.component.HostDialog
import com.owo233.tcqt.ui.component.SecondaryButton
import com.owo233.tcqt.ui.component.TCQTPanel
import com.owo233.tcqt.ui.settings.model.TextAreaUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/** 群勾选器的入口行与弹窗。 */
@Composable
internal fun GroupPickerPreference(
    area: TextAreaUiState,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ArrowPreference(
        title = area.label,
        summary = selectedSummary(area.value),
        insideMargin = SettingsRowPadding,
        onClick = { open = true },
    )
    if (open) {
        GroupPickerDialog(
            initial = area.value,
            onDismiss = { open = false },
            onConfirm = {
                onChange(it)
                open = false
            },
        )
    }
}

@Composable
private fun GroupPickerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }
    // 框内文本就是已选群号的真值：列表勾选、手改、粘贴都写回这里，保存时按它解析。
    var groupText by remember { mutableStateOf(parseGroupNumbers(initial).joinToString(",")) }
    var importHint by remember { mutableStateOf("") }
    val selectedUins = parseGroupNumbers(groupText)

    val groups = produceState<GroupLoadState>(GroupLoadState.Loading, reloadKey) {
        value = GroupLoadState.Loading
        value = withContext(Dispatchers.IO) {
            runCatching { GroupSignInService.joinedGroups() }
        }.fold(
            onSuccess = { list ->
                if (list.isEmpty()) GroupLoadState.Empty else GroupLoadState.Loaded(list)
            },
            onFailure = { error ->
                Log.e("读取群列表失败", error)
                GroupLoadState.Failed
            },
        )
    }.value

    HostDialog(
        show = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
    ) {
    DialogForm(
        title = "选择打卡的群",
        actions = {
            SecondaryButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            ActionButton(
                // 保存以框内文本为准：解析、去重后落库。
                onClick = { onConfirm(selectedUins.joinToString(",")) },
                modifier = Modifier.weight(1f),
                primary = true,
            ) {
                Text("确定")
            }
        },
    ) {
        GroupImportPanel(
            text = groupText,
            importHint = importHint,
            onTextChange = {
                groupText = it
                importHint = ""
            },
            onImport = {
                val parsed = parseGroupNumbers(groupText)
                if (parsed.isEmpty()) {
                    importHint = "没有识别到 5–12 位的群号"
                } else {
                    // 整串重新解析：去重、统一成逗号分隔，再写回框内。
                    groupText = parsed.joinToString(",")
                    importHint = "已导入 ${parsed.size} 个群号（逗号分隔、已去重）"
                }
            },
        )

        SearchField(query) { query = it }

        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp)) {
            when (val state = groups) {
                GroupLoadState.Loading -> HintText("正在读取群列表…")
                GroupLoadState.Empty -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HintText("未读取到群列表")
                    SecondaryButton(onClick = { reloadKey++ }) { Text("重新读取") }
                }

                GroupLoadState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HintText("读取群列表失败，请确认 QQ 已登录")
                    SecondaryButton(onClick = { reloadKey++ }) { Text("重试") }
                }

                is GroupLoadState.Loaded -> {
                    val filter = query.trim()
                    val visible = remember(state.groups, filter) {
                        if (filter.isEmpty()) state.groups
                        else state.groups.filter {
                            it.name.contains(filter, ignoreCase = true) || it.uin.contains(filter)
                        }
                    }
                    if (visible.isEmpty()) {
                        HintText("没有匹配的群")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(visible, key = { it.uin }) { group ->
                                GroupRow(
                                    name = group.name,
                                    uin = group.uin,
                                    avatarUrl = group.avatarUrl,
                                    checked = group.uin in selectedUins,
                                    onToggle = {
                                        // 勾选/取消立即重写框内群号串，保证两边一致。
                                        val uins = parseGroupNumbers(groupText)
                                        val updated = if (group.uin in uins) uins - group.uin else uins + group.uin
                                        groupText = updated.joinToString(",")
                                        importHint = ""
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            "列表来自 QQ 当前账号的群会话。" + if (selectedUins.isEmpty()) {
                "当前未勾选任何群。"
            } else {
                "点“确定”保存这 ${selectedUins.size} 个群号。"
            },
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
    }
}

/**
 * 弹窗顶部常驻面板：输入框本身就是已选群号的真值，可直接增删、粘贴、长按选中复制；
 * 「导入群号」把框内整串重新解析、去重并写回，同时给出结果提示。
 */
@Composable
private fun GroupImportPanel(
    text: String,
    importHint: String,
    onTextChange: (String) -> Unit,
    onImport: () -> Unit,
) {
    TCQTPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        color = MiuixTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "已勾选 ${parseGroupNumbers(text).size} 个群号",
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                "框内就是当前已选的群号（逗号分隔）：可直接增删、粘贴，长按可整串选中复制。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            TCQTPanel(
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 140.dp),
                cornerRadius = 10.dp,
                color = MiuixTheme.colorScheme.surface,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    textStyle = MiuixTheme.textStyles.body1.copy(
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Normal,
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                "粘贴群号，例如 123456,234567 或 123456#群名",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                        inner()
                    },
                )
            }
            SecondaryButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("导入群号")
            }
            if (importHint.isNotEmpty()) {
                Text(
                    importHint,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun GroupRow(
    name: String,
    uin: String,
    avatarUrl: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupAvatar(name, avatarUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                uin,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        SelectionMark(checked)
    }
}

/** 群头像：先同步命中的缓存出图，未命中时保留占位，避免列表滚动时闪烁。 */
@Composable
private fun GroupAvatar(name: String, avatarUrl: String) {
    val image by produceState(GroupAvatarLoader.cached(avatarUrl), avatarUrl) {
        if (value == null) value = GroupAvatarLoader.load(avatarUrl)
    }
    val current = image
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
    } else {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MiuixTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1),
                style = MiuixTheme.textStyles.body1,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SelectionMark(checked: Boolean) {
    val primary = MiuixTheme.colorScheme.primary
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(if (checked) Modifier else Modifier.border(1.5.dp, MiuixTheme.colorScheme.onSurfaceSecondary, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Box(Modifier.size(12.dp).clip(CircleShape).background(primary))
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        singleLine = true,
        textStyle = MiuixTheme.textStyles.body1.copy(
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        decorationBox = { inner ->
            if (query.isEmpty()) {
                Text(
                    "搜索群名或群号",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            inner()
        },
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

private sealed interface GroupLoadState {
    data object Loading : GroupLoadState
    data object Empty : GroupLoadState
    data object Failed : GroupLoadState
    data class Loaded(val groups: List<GroupSignInService.GroupInfo>) : GroupLoadState
}

private fun selectedSummary(raw: String): String {
    val count = parseGroupNumbers(raw).size
    return if (count == 0) "未选择群，点此从 QQ 群列表勾选" else "已选 $count 个群"
}

/**
 * 轻量头像加载器：不引入图片库，按最大并发 4 路加载，解码时降采样到约 144px，
 * 结果放在 64 项的内存缓存里。设置页与头像都运行在 QQ 进程内，直接复用宿主网络权限。
 */
private object GroupAvatarLoader {

    private const val MAX_ENTRIES = 64
    private const val TARGET_SIZE = 144
    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 8_000

    private val cache = object : LruCache<String, ImageBitmap>(MAX_ENTRIES) {}

    private val permits = Semaphore(4)

    fun cached(url: String): ImageBitmap? = if (url.isBlank()) null else cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it }
        if (url.isBlank()) return null
        return withContext(Dispatchers.IO) {
            permits.withPermit {
                cached(url) ?: runCatching { fetch(url) }.getOrNull()?.also { cache.put(url, it) }
            }
        }
    }

    private fun fetch(url: String): ImageBitmap? {
        val bytes = runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                try {
                    if (responseCode !in 200..299) return null
                    inputStream.readBytes()
                } finally {
                    disconnect()
                }
            }
        }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_SIZE && bounds.outHeight / (sample * 2) >= TARGET_SIZE) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, options)?.asImageBitmap()
    }
}
