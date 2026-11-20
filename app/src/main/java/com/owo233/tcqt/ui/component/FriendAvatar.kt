package com.owo233.tcqt.ui.component

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcelable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.reflect.new
import com.owo233.tcqt.features.notification.QQAvatarHelper
import com.owo233.tcqt.host.service.FriendSnapshotReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 好友头像：先读 QQ 本地头像缓存（无需联网），再按 qlogo 地址下载；
 * 两者都失败时显示圆形底色 + QQ 号末两位，保证条目结构不塌。
 */
@Composable
internal fun FriendAvatar(uin: String, avatarUrl: String = "", size: Dp = 44.dp) {
    val image by produceState<Bitmap?>(initialValue = FriendAvatarCache.peek(uin), uin, avatarUrl) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { FriendAvatarCache.load(uin, avatarUrl) }
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = uin.takeLast(2).ifBlank { "?" },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

private object FriendAvatarCache {

    private val memory = LruCache<String, Bitmap>(120)
    private val failures = ConcurrentHashMap<String, Long>()
    private val helper by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { QQAvatarHelper() }

    fun peek(uin: String): Bitmap? = memory.get(uin)

    fun load(uin: String, avatarUrl: String): Bitmap? {
        if (uin.isBlank()) return null
        memory.get(uin)?.let { return it }
        // 失败后 10 分钟内不重复请求，避免列表滚动时反复打网络。
        val failedAt = failures[uin]
        if (failedAt != null && System.currentTimeMillis() - failedAt < 10 * 60_000L) return null

        val bitmap = runCatching { helper.getAvatar(uin)?.bitmap }.getOrNull()
            ?: download(avatarUrl.ifBlank { FriendSnapshotReader.avatarOf(uin) })
        if (bitmap != null) {
            memory.put(uin, bitmap)
            failures.remove(uin)
        } else {
            failures[uin] = System.currentTimeMillis()
        }
        return bitmap
    }

    private fun download(url: String): Bitmap? = runCatching {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.getInputStream().use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

/**
 * QQ 号是否像合法号码：5~12 位数字。
 *
 * 非法值（为空、含字母、位数不对）直接拒绝：把脏数据交给宿主 Activity 会让它卡在
 * 好友/资料查询流程里，最终表现就是 QQ 整个卡住。
 */
internal fun isUinLike(uin: String): Boolean =
    uin.length in 5..12 && uin.all { it in '0'..'9' }

/**
 * 打开官方用户页（**必须在后台线程调用**）。
 *
 * 反射加载类、构造 `AllInOne`、拼 Intent 全部是阻塞操作，统一放在 IO 线程；
 * 主线程只执行最后一次 `startActivity`。点击即可返回，不会把宿主主线程占住。
 *
 * 用宿主 Application Context + `FLAG_ACTIVITY_NEW_TASK` 启动，避免把宿主 Activity
 * 塞进模块寄生页（CameraPreview 壳）的任务栈里而触发宿主重新走一遍启动流程。
 *
 * @param preferSearch 本地昵称/备注都没有时优先走搜索页。
 * @return false 表示所有入口都不可用，调用方负责给可见提示。
 */
internal suspend fun openOfficialUserPage(
    context: Context,
    uin: String,
    preferSearch: Boolean,
): Boolean {
    if (!isUinLike(uin)) return false
    val host = hostContext(context)
    return withContext(Dispatchers.IO) {
        val candidates = resolveOfficialUserPageIntents(host, uin, preferSearch)
        if (candidates.isEmpty()) return@withContext false
        withContext(Dispatchers.Main) {
            candidates.any { intent -> runCatching { host.startActivity(intent) }.isSuccess }
        }
    }
}

/** 打开模块自己的能力范围之外的东西时，一律用宿主 Context。 */
private fun hostContext(context: Context): Context =
    runCatching { HookEnv.hostAppContext }.getOrNull() ?: context.applicationContext ?: context

private fun hostPackage(context: Context): String =
    runCatching { HookEnv.hostAppPackageName }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: context.packageName

private fun launchable(context: Context, intent: Intent): Intent =
    if (context is Activity) intent else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * 按优先级拼出所有可用入口；调用方在主线程按顺序尝试，失败自动换下一个。
 *
 * 只在后台线程调用（内含类加载与反射构造）。
 */
private fun resolveOfficialUserPageIntents(
    context: Context,
    uin: String,
    preferSearch: Boolean,
): List<Intent> {
    val intents = mutableListOf<Intent>()
    if (preferSearch) {
        // 本地没有任何名字：先按 QQ 号搜索，不行再进通用资料页。
        intents += searchIntents(context, uin)
        mqqCardIntent(context, uin)?.let(intents::add)
        friendCardIntent(context, uin)?.let(intents::add)
    } else {
        // 有名字：优先通用资料页（陌生号也能开），再退到资料卡 Activity 与搜索。
        mqqCardIntent(context, uin)?.let(intents::add)
        friendCardIntent(context, uin)?.let(intents::add)
        intents += searchIntents(context, uin)
    }
    return intents.map { launchable(context, it) }
}

/** 宿主自带的搜索入口；按版本从新到旧尝试，类不存在就跳过。 */
private val HOST_SEARCH_ACTIVITIES = listOf(
    "com.tencent.mobileqq.activity.contact.addcontact.SearchActivity",
    "com.tencent.mobileqq.activity.contact.addcontact.AddContactsActivity",
    "com.tencent.mobileqq.search.activity.UniteSearchActivity",
)

private fun searchIntents(context: Context, uin: String): List<Intent> {
    val intents = mutableListOf<Intent>()
    for (name in HOST_SEARCH_ACTIVITIES) {
        if (load(name) == null) continue
        intents += Intent().apply {
            component = ComponentName(hostPackage(context), name)
            // 不同版本读取的 key 不一样，全部带上；多出来的 extra 对宿主无副作用。
            putExtra("key_keyword", uin)
            putExtra("keyword", uin)
            putExtra("key_search_type", 0)
            putExtra("key_from", "add_friend")
        }
    }
    return intents
}

/** mqqapi 资料卡入口：陌生 QQ 也能打开官方资料页，不依赖本地好友关系。 */
private fun mqqCardIntent(context: Context, uin: String): Intent? = runCatching {
    val uri = android.net.Uri.parse(
        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$uin" +
            "&card_type=person&source=profile_friend_list"
    )
    Intent(Intent.ACTION_VIEW, uri).apply { setPackage(hostPackage(context)) }
}.getOrNull()

/**
 * 好友资料卡（与「添加模块入口」里的资料卡跳转保持同一套参数）。
 *
 * 好友历史里的条目全部是「已删除 / 手动添加」的人，所以 `key_is_friend_profile_card`
 * 固定为 false：对陌生人谎称是好友，宿主会一直等好友数据，表现为点一下就卡死。
 */
private fun friendCardIntent(context: Context, uin: String): Intent? {
    val allInOneClass = load("com.tencent.mobileqq.profilecard.data.AllInOne") ?: return null
    val activityClass = if (HookEnv.isQQ()) {
        "com.tencent.mobileqq.profilecard.activity.FriendProfileCardActivity"
    } else {
        "com.tencent.mobileqq.profilecard.activity.TimFriendProfileCardActivity"
    }
    if (load(activityClass) == null) return null

    val allInOne = runCatching { allInOneClass.new(uin, 83) as? Parcelable }.getOrNull() ?: return null
    return Intent().apply {
        component = ComponentName(hostPackage(context), activityClass)
        putExtra("key_is_friend_profile_card", false)
        putExtra("fling_action_key", 2)
        putExtra("AllInOne", allInOne)
    }
}

