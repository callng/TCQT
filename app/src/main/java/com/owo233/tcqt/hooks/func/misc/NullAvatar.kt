package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import android.graphics.Bitmap
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.avatar.AvatarUtil
import com.owo233.tcqt.utils.avatar.toStream
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.File
import java.io.FileOutputStream

@RegisterAction
@RegisterSetting(
    key = "null_avatar",
    name = "上传透明头像",
    type = SettingType.BOOLEAN,
    desc = "随便从相册选择一张图片上传即可，自动替换为透明头像。",
    uiTab = "杂项"
)
class NullAvatar : IAction, DexKitTask {

    override val key: String
        get() = GeneratedSettingList.NULL_AVATAR

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
            matcher {
                declaredClass("com.tencent.mobileqq.util.ProfileCardUtil")
                usingEqStrings("image illegal, size must be square.")
            }
        },
        "compressUtils" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.pic.compress")
            matcher {
                declaredClass(
                    "com.tencent.mobileqq.pic.compress",
                    StringMatchType.StartsWith
                )
                usingEqStrings("JpegCompressor.compress() error")
            }
        }
    )
}
