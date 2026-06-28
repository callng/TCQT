/**
 * 代码来自：https://github.com/oneQAQone/QFun
 * 翻译：owo233
 * 效果优化：owo233，oneQAQone
 */

package com.owo233.tcqt.hooks.func.theme

import android.os.Build
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.api.packet.PacketHelper
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.proto2json.ProtoByteString
import com.owo233.tcqt.utils.proto2json.ProtoMap
import com.owo233.tcqt.utils.proto2json.ProtoUtils
import com.owo233.tcqt.utils.proto2json.asInt
import com.owo233.tcqt.utils.proto2json.asUtf8String
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.common.app.BaseApplicationImpl
import com.tencent.mobileqq.vas.theme.ThemeSwitcher
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal object ThemeEngine {

    private const val THEME_SLOT = "2105"

    private class ThemeSession(
        val id: String,
        val callback: IThemeCallback
    ) {
        var md5: String = ""
        var baseUrl: String = ""
        var videoUrl: String = ""
        val counter = AtomicInteger(0)
        @Volatile var failed: Boolean = false
    }

    fun applyThemeLogic(themeId: String, callback: IThemeCallback?) {
        if (HookEnv.isQQ()) {
            val session = ThemeSession(
                id = themeId.trim(),
                callback = callback ?: IThemeCallback {}
            )
            if (ThemeSwitcher.getRoamingThemeId() != THEME_SLOT) {
                setRoamingTheme()
            }
            dispatchPacket(session, "theme.${session.id}", 101)
        } else {
            callback?.onFinish(false)
        }
    }

    private fun setRoamingTheme(tid: String = THEME_SLOT) {
        val proto = ProtoMap().apply {
            this[1] = 0
            this[3] = """{"stUniBusinessItem":{"appid":3,"itemid":${tid}}}"""
        }

        PacketHelper.sendRequest(
            "MQUpdateSvc_com_qq_vip_zb.web.OidbSvcTrpcJsapiTcp.0x942d_0",
            proto.toByteArray()
        )
    }

    private fun dispatchPacket(session: ThemeSession, scid: String, ck: Int) {
        try {
            val ver = BaseApplicationImpl.sApplication.publishVersion
            val proto = ProtoMap().apply {
                this[1] = 2

                this[2, 1] = 109
                this[2, 2] = ver
                this[2, 3] = "${Build.VERSION.SDK_INT}"
                this[2, 4] = 1
                this[2, 5] = "theme"
                this[2, 6] = ck.toLong()
                this[2, 9] = 1
                this[2, 10] = ver
                this[2, 11] = "theme"

                this[4, 1] = 1
                this[4, 2] = 1
                this[4, 3] = 1

                this[4, 4, 1] = 3
                this[4, 4, 2] = scid
                this[4, 4, 3] = ""
                this[4, 4, 7] = "theme.version2.base.day.high.android.${session.id}"
                this[4, 4, 8] = 1

                this[4, 5] = 1
            }

            PacketHelper.sendRequest("scupdate.handle", proto.toByteArray()) { data ->
                onReceivePacket(session, ck, data)
            }
        } catch (e: Exception) {
            Log.e("dispatchPacket error", e)
            session.callback.onFinish(false)
        }
    }

    @Synchronized
    private fun onReceivePacket(session: ThemeSession, ck: Int, data: ByteArray) {
        if (session.failed) return

        try {
            val resp = ProtoUtils.decodeFromByteArray(data)
            val f2 = if (resp.has(6, 2)) resp[6, 2] as? ProtoMap else null
            val code = if (f2 != null && f2.has(11)) f2[11].asInt else 0

            if (ck == 101) {
                if (code == 0) {
                    session.baseUrl = parseUrl(f2)
                    dispatchPacket(session, "theme.$THEME_SLOT", 100)
                    dispatchPacket(session, "theme.video.${session.id}", 102)
                } else {
                    session.failed = true
                    session.callback.onFinish(false)
                }
                return
            }

            if (ck == 100) {
                if (code == 0) {
                    session.md5 = if (f2 != null && f2.has(3)) f2[3].asUtf8String else ""
                } else {
                    session.failed = true
                    session.callback.onFinish(false)
                }
            } else if (ck == 102 && code == 0) {
                session.videoUrl = parseUrl(f2)
            }

        } catch (e: Exception) {
            Log.e("onReceivePacket error: ck=$ck", e)
            if (ck != 102) {
                session.failed = true
                session.callback.onFinish(false)
                return
            }
        }

        if (session.counter.incrementAndGet() == 2) {
            if (session.md5.isEmpty() || session.baseUrl.isEmpty()) {
                Log.e("onReceivePacket error: md5 or base_url is empty!")
                session.callback.onFinish(false)
            } else {
                processThemeFiles(session)
            }
        }
    }

    private fun processThemeFiles(session: ThemeSession) {
        thread {
            val pathRoot = FileUtil.getThemeRootDir()
            try {
                val rootFile = File(pathRoot + THEME_SLOT)

                FileUtil.forceDelete(rootFile)
                rootFile.mkdirs()

                val tmp = File(rootFile, "work_tmp")
                tmp.mkdirs()

                FileUtil.downloadFile(session.baseUrl, File(tmp, "b.zip"))
                val bZip = File(rootFile, "theme.$THEME_SLOT.zip")
                FileUtil.extractInnerPackage(File(tmp, "b.zip"), bZip)

                val md5Dir = File(rootFile, "theme.$THEME_SLOT/${session.md5}")
                md5Dir.mkdirs()
                FileUtil.unzipFile(bZip, md5Dir)

                val vUrl = session.videoUrl
                if (vUrl.isNotEmpty()) {
                    try {
                        val vRaw = File(tmp, "v.zip")
                        FileUtil.downloadFile(vUrl, vRaw)
                        val vZip = File(rootFile, "theme.video.$THEME_SLOT.zip")
                        FileUtil.extractInnerPackage(vRaw, vZip)
                        val rawDir = File(md5Dir, "raw")
                        rawDir.mkdirs()
                        FileUtil.unzipFile(vZip, rawDir)
                    } catch (ev: Exception) {
                        Log.e("processThemeFiles video error", ev)
                    }
                }

                FileUtil.forceDelete(tmp)

                QQInterfaces.topActivity.runOnUiThread {
                    runCatching {
                        val p = "${pathRoot}${THEME_SLOT}/theme.${THEME_SLOT}/${session.md5}/"
                        ThemeSwitcher::class.java.findMethod {
                            name = "realDoSwitchTheme"
                            paramTypes = arrayOf(context, string, string)
                        }.invoke(ThemeSwitcher(), BaseApplicationImpl.sApplication, THEME_SLOT, p)
                    }.onFailure { err ->
                        Log.e("processThemeFiles ThemeSwitcher error on main thread", err)
                    }
                }
                session.callback.onFinish(true)
            } catch (e: Exception) {
                Log.e("processThemeFiles error in thread", e)
                session.callback.onFinish(false)
            }
        }
    }

    private fun parseUrl(f2: ProtoMap?): String {
        if (f2 == null || !f2.has(8)) return ""
        return when (val o = f2[8]) {
            is ProtoByteString -> o.toUtfString()
            is ProtoMap -> {
                val tag7 = if (o.has(7)) o[7].asUtf8String else ""
                val tag12 = if (o.has(12)) o[12].asUtf8String else ""
                "https:/$tag7$tag12"
            }
            else -> ""
        }
    }
}
