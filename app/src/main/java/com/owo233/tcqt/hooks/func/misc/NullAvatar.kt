package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import android.graphics.Bitmap
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.avatar.AvatarUtil
import com.owo233.tcqt.utils.avatar.toStream
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.io.File
import java.io.FileOutputStream

@RegisterAction
class NullAvatar : IAction, DexKitTask {

    override val name: String get() = "上传透明头像"
    override val desc: String get() = "随便从相册选择一张图片上传即可，自动替换为透明头像。"
    override val uiTab: String get() = "杂项"
    override val key: String get() = "null_avatar"

    override fun onRun(app: Application, process: ActionProcess) {
        requireMethod("NullAvatar").hookBefore { param ->
            File(param.args[0] as String).outputStream().use {
                AvatarUtil.getBitmap(app).toStream().writeTo(it)
            }
        }

        requireMethod("compressUtils").hookBefore { param ->
            val path = param.args[0] as String
            val bitmap = param.args[1] as Bitmap

            FileOutputStream(path).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            param.result = true
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "NullAvatar" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.util")
            matcher {
                usingEqStrings("image illegal, size must be square.")
            }
        },
        "compressUtils" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.pic.compress")
            matcher {
                usingEqStrings("JpegCompressor.compress() error")
            }
        }
    )
}
