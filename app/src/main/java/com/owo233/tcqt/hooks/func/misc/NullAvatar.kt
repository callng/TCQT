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
import com.owo233.tcqt.utils.log.Log
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.File
import java.io.FileOutputStream

@RegisterAction
class NullAvatar : IAction, DexKitTask {

    override val name: String get() = "上传透明头像"
    override val desc: String get() = "随便从相册选择一张图片上传即可，自动替换为透明头像。"
    override val uiTab: String get() = "杂项"
    override val key: String
        get() = "null_avatar"

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

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        val nullAvatarMethod = bridge.findClass {
            matcher {
                className("com.tencent.mobileqq.util.ProfileCardUtil")
            }
        }.findMethod {
            matcher {
                usingEqStrings("image illegal, size must be square.")
            }
        }.singleOrNull()
        if (nullAvatarMethod != null) {
            cache["NullAvatar"] = nullAvatarMethod.descriptor
        } else {
            Log.e("NullAvatar: No method found matching query")
        }

        val compressUtilsMethod = bridge.findClass {
            searchPackages("com.tencent.mobileqq.pic.compress")
            matcher {
                className("com.tencent.mobileqq.pic.compress", StringMatchType.StartsWith)
            }
        }.findMethod {
            matcher {
                usingEqStrings("JpegCompressor.compress() error")
            }
        }.singleOrNull()
        if (compressUtilsMethod != null) {
            cache["compressUtils"] = compressUtilsMethod.descriptor
        } else {
            Log.e("compressUtils: No method found matching query")
        }
    }
}
