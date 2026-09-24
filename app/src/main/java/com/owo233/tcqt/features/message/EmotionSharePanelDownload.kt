/**
 * 摘取: https://github.com/cinit/QAuxiliary/commit/72b7ff9881d7d14f5d04f8f35f616921cb152fc9
 * 提供: HdShare
 * 改进: owo233
 */
package com.owo233.tcqt.features.message

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.dexkit.DexKitLookupTracker
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.returnConstant
import org.luckypray.dexkit.DexKitBridge
import java.io.File

@RegisterAction
object EmotionSharePanelDownload : Feature(
    key = "emotion_share_panel_download",
    name = "表情分享菜单允许保存图片",
    desc = "表情分享菜单显示保存到手机选项，与《以图片方式打开表情》不能同时启用。",
), DexKitTask {


    override fun install() {
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30)) {
            requireMethod(EMOTION_DETAIL_AI).returnConstant(true)
            hookMarketFaceSave()
        } else {
            requireMethod(EMOTION_DOWNLOAD_DISABLE_SWITCH).returnConstant(false)
        }
    }

    private fun hookMarketFaceSave() {
        requireMethod(EMOTION_SAVE_FILE).hookBefore { param ->
            val message = param.args.getOrNull(0) ?: return@hookBefore
            if (message.javaClass.name != MESSAGE_FOR_MARKET_FACE) return@hookBefore
            param.result = resolveMarketFaceFile(message)
        }
    }

    private fun resolveMarketFaceFile(message: Any): File? {
        return runCatching {
            val messageClass = message.javaClass
            val packageId = messageClass.getMethod("getMarketFacePackageId").invoke(message) as Int
            val faceId = messageClass.getMethod("getMarketFaceId").invoke(message) as String
            val packageDir = File(
                "com.tencent.mobileqq.app.AppConstants".toClass
                    .getField("SDCARD_EMOTICON_SAVE").get(null) as String,
                packageId.toString()
            )
            // 优先原始资源文件：GIF/APNG 等动图只有原图才保留动画
            // 做过异或加密（见 decryptMarketFaceFile），识别不了就先解密再用于保存
            listOf(File(packageDir, faceId), File(packageDir, "${faceId}_apng"))
                .firstNotNullOfOrNull { file ->
                    when {
                        file.isFile && file.isImageFile() -> file
                        file.isFile -> decryptMarketFaceFile(file)
                        else -> null
                    }
                }
                // 兜底使用 AIO 预览图（静态 png）
                ?: File(packageDir, "${faceId}_aio.png").takeIf { it.isFile }
        }.getOrNull()
    }

    /**
     * 商城表情原始文件在磁盘上是加密的，需要解密后保存
     * SecurityUtile.codeEmosmKey = {0x00, 0x01, 0x00, 0x01} 逐字节异或（XOR 自反）
     */
    private fun decryptMarketFaceFile(encrypted: File): File? {
        return runCatching {
            val bytes = encrypted.readBytes()
            if (bytes.isEmpty()) return@runCatching null
            val limit = minOf(EMO_DECRYPT_LIMIT, bytes.size)
            for (i in 0 until limit) {
                bytes[i] = (bytes[i].toInt() xor EMO_DECRYPT_KEY[i % EMO_DECRYPT_KEY.size].toInt()).toByte()
            }
            val extension = imageExtension(bytes) ?: return@runCatching null
            val decoded = File(encrypted.parentFile, "${encrypted.name}_tcqt$extension")
            if (!decoded.isFile) {
                decoded.writeBytes(bytes)
            }
            decoded.takeIf { it.isFile }
        }.getOrNull()
    }

    private fun File.isImageFile(): Boolean {
        return runCatching { imageExtension(readBytes()) != null }.getOrDefault(false)
    }

    private fun imageExtension(bytes: ByteArray): String? {
        fun match(offset: Int, magic: ByteArray): Boolean {
            if (bytes.size < offset + magic.size) return false
            for (i in magic.indices) {
                if (bytes[offset + i] != magic[i]) return false
            }
            return true
        }
        return when {
            match(0, byteArrayOf(0x47, 0x49, 0x46, 0x38)) -> ".gif"                     // GIF87a / GIF89a
            match(0, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) -> ".png"            // PNG / APNG
            match(0, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) -> ".jpg" // JPEG
            match(0, byteArrayOf(0x52, 0x49, 0x46, 0x46)) &&                             // WebP
                match(8, byteArrayOf(0x57, 0x45, 0x42, 0x50)) -> ".webp"
            else -> null
        }
    }

    override fun getCacheKeys(): Set<String> {
        return buildSet {
            if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30)) {
                add(EMOTION_DETAIL_AI)
                add(EMOTION_SAVE_FILE)
            } else {
                add(EMOTION_DOWNLOAD_DISABLE_SWITCH)
            }
        }
    }

    override fun execute(
        bridge: DexKitBridge,
        cache: MutableMap<String, String>,
        tracker: DexKitLookupTracker
    ) {
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30)) {
            lookup(EMOTION_DETAIL_AI, bridge, cache, tracker) {
                findClass {
                    searchPackages("com.tencent.mobileqq.emotionintegrate")
                    matcher {
                        usingStrings("MsgEmoticonPreviewData", "doRestoreSaveInstanceState")
                    }
                }.findMethod {
                    matcher {
                        returnType(Boolean::class.java)
                        usingNumbers(14)
                    }
                }.singleOrNull()?.descriptor
            }

            lookup(EMOTION_SAVE_FILE, bridge, cache, tracker) {
                findMethod {
                    searchPackages("com.tencent.mobileqq.emotionintegrate")
                    matcher {
                        declaredClass("com.tencent.mobileqq.emotionintegrate.AIOEmotionFragment")
                        returnType(File::class.java)
                        paramTypes = listOf("com.tencent.mobileqq.data.MessageRecord")
                    }
                }.singleOrNull()?.descriptor
            }
        } else {
            lookup(EMOTION_DOWNLOAD_DISABLE_SWITCH, bridge, cache, tracker) {
                findMethod {
                    searchPackages("com.tencent.mobileqq.emotionintegrate")
                    matcher {
                        returnType(Boolean::class.java)
                        usingStrings("emotion_download_disable_8980_887036489")
                    }
                }.singleOrNull()?.descriptor
            }
        }
    }

    private const val EMOTION_DETAIL_AI = "EmotionDetailAi "
    private const val EMOTION_DOWNLOAD_DISABLE_SWITCH = "EmotionDownloadDisableSwitch"
    private const val EMOTION_SAVE_FILE = "EmotionSaveFile"
    private const val MESSAGE_FOR_MARKET_FACE = "com.tencent.mobileqq.data.MessageForMarketFace"
    private val EMO_DECRYPT_KEY = byteArrayOf(0x00, 0x01, 0x00, 0x01)
    private const val EMO_DECRYPT_LIMIT = 200
}
