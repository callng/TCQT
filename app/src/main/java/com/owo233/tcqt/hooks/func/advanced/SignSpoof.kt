package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.StringSetting
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.tencent.commonsdk.util.HexUtil
import oicq.wlogin_sdk.tools.MD5
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod

@RegisterAction
class SignSpoof : IAction, DexKitTask {

    override val key: String get() = "share_sign_spoof"
    override val name: String get() = "绕过签名验证"
    override val desc: String get() = "绕过从其他APP分享或授权登录时的签名校验，已内置部分常见APP「哔哩哔哩、酷我、知乎、小红书、网易云、酷安、QQ邮箱」"
    override val uiTab: String get() = "高级"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.OPENSDK)

    override val settings: List<Setting<*>>
        get() = listOf(
            StringSetting(
                key = SETTING_KEY_CUSTOM_MAP,
                name = "一行一条「包名,原始签名MD5」；分隔符支持英文逗号、中文逗号或空格。内置映射之外的包名在此追加，同名会覆盖内置值。",
            )
        )

    override fun onRun(app: Application, process: ActionProcess) {
        // 第三方应用分享内容
        requireMethod(TASK_SIGN).hookReplace { param ->
            val pkg = (param.args.getOrNull(1) as? String)?.trim()
                ?: return@hookReplace param.invokeOriginal()

            val md5 = parseCustomMap()[pkg]?.lowercase() ?: BUILT_IN_SIGN_MAP[pkg]
            if (md5.isNullOrEmpty()) param.invokeOriginal() else md5
        }

        // 三方应用授权登录
        requireMethod(TASK_AUTH_SIGN).hookReplace { param ->
            val pkg = (param.args.getOrNull(0) as? String)?.trim()
                ?: return@hookReplace param.invokeOriginal()

            val md5 = parseCustomMap()[pkg]?.lowercase() ?: BUILT_IN_SIGN_MAP[pkg]
            if (md5.isNullOrEmpty()) return@hookReplace param.invokeOriginal()

            val timestamp = (System.currentTimeMillis() / 1000).toString()
            arrayOf(md5, composeAuthSign(pkg, md5, timestamp), timestamp)
        }
    }

    private fun parseCustomMap(): Map<String, String> {
        val text = TCQTSetting.getString(SETTING_KEY_CUSTOM_MAP)
        if (text.isBlank()) return emptyMap()

        val map = LinkedHashMap<String, String>()
        text.lineSequence().forEach { rawLine ->
            val parts = rawLine.trim()
                .split(Regex("[,，\\s　]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val pkg = parts[0]
                val md5 = parts[1]
                if (pkg.isNotEmpty() && md5.isNotEmpty()) {
                    map[pkg] = md5.lowercase()
                }
            }
        }
        return map
    }

    override fun getCacheKeys(): Set<String> = setOf(TASK_SIGN, TASK_AUTH_SIGN)

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        cache[TASK_SIGN] = bridge.findMethod(signQuery()).singleOrNull()?.descriptor ?: ""
        cache[TASK_AUTH_SIGN] = bridge.findMethod(authSignQuery()).singleOrNull()?.descriptor ?: ""
    }

    // 9.3.55 com.tencent.mobileqq.forward.bs.e(Context, String) String
    // getPackageManager -> getPackageInfo(pkg, 0x40) -> signatures[0].toByteArray()
    // -> MessageDigest("MD5") -> bytes2HexStr -> lowercase
    private fun signQuery(): FindMethod = FindMethod().apply {
        searchPackages("com.tencent.mobileqq.forward")
        matcher {
            paramCount(2)
            paramTypes("android.content.Context", "java.lang.String")
            returnType("java.lang.String")
            usingStrings("MD5")
            usingNumbers(0x40)
        }
    }

    // 9.3.55 com.tencent.open.virtual.OpenSdkVirtualUtil.g(String)[String]（getAuthorizeSign）
    // 返回 [真实签名MD5, MD5(pkg_sign_timestamp), timestamp]
    private fun authSignQuery(): FindMethod = FindMethod().apply {
        searchPackages("com.tencent.open.virtual")
        matcher {
            paramCount(1)
            paramTypes("java.lang.String")
            returnType("java.lang.String[]")
            usingNumbers(0x3e8, 0x40)
            usingStrings("MD5")
        }
    }

    private fun composeAuthSign(pkg: String, signMd5: String, extra: String): String =
        HexUtil.bytes2HexStr(MD5.toMD5Byte("${pkg}_${signMd5}_${extra}"))

    companion object {
        private const val SETTING_KEY_CUSTOM_MAP = "share_sign_spoof.string.customMap"
        private const val TASK_SIGN = "share_sign_method"
        private const val TASK_AUTH_SIGN = "auth_sign_method"

        private val BUILT_IN_SIGN_MAP = linkedMapOf(
            "tv.danmaku.bili" to "7194d531cbe7960a22007b9f6bdaa38b", // B站
            "cn.kuwo.player" to "bf9ff4ffb4c558a34ee3fd52c223ebf5", // 酷我
            "com.zhihu.android" to "5c4f618536eaf9ae0e2628c5af1693bc", // 知乎
            "com.xingin.xhs" to "6cfca61d9d1eca56844806706ba18cf7", // 小红书
            "com.netease.cloudmusic" to "da6b069da1e2982db3e386233f68d76d", // 网易云
            "com.coolapk.market" to "03722d493a5a6f991b9bb8a8f2006a17", // 酷安
            "com.tencent.androidqqmail" to "b7a2083459d01bb79c3d813242dc1f68", // QQ邮箱
        )
    }
}
