package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.emptyParam
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.isPublic

@RegisterAction
class RemoveMediaLimit : IAction {

    override val name: String get() = "移除媒体选择数量限制"
    override val desc: String get() = "移除聊天页相册最多只能选择20张图片/视频的限制，移除空间上传最多只能选择50张图片/视频的限制。"
    override val uiTab: String get() = "基础"

    override fun onRun(app: Application, process: ActionProcess) {
        // 群聊私聊
        loadOrThrow(
            "com.tencent.qqnt.qbasealbum.select.viewmodel.SelectedMediaViewModel"
        )
            .declaredMethods
            .single { method ->
                method.isPublic && method.emptyParam && method.returnType == Boolean::class.java
            }.hookBefore { param ->
                param.result = true
            }

        // 空间相册选择器移除数量限制
        loadOrThrow(
            "com.tencent.mobileqq.wink.picker.core.viewmodel.WinkSelectedMediaViewModel"
        )
            .declaredMethods
            .filter { method -> // 为什么有两个符合条件的方法!!!，都hook罢!
                method.isPublic && method.emptyParam && method.returnType == Boolean::class.java
            }.forEach { method ->
                method.hookBefore { param ->
                    param.result = true
                }
            }

        // 移除下一步点击限制
        loadOrThrow(
            "com.tencent.mobileqq.wink.picker.qzone.viewmodel.QZoneSelectedMediaViewModel"
        )
            .getMethod("getCurSelectedSize")
            .hookBefore { param ->
                param.result = 1
            }

        // 移除上传配置活动数量限制
        loadOrThrow("common.config.service.QzoneConfig")
            .getMethod(
                "getConfig",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            .hookBefore { param ->
                val key1 = param.args[0] as String
                val key2 = param.args[1] as String
                if (key1 == "PublishMood" && key2 == "MoodPhotoMaxNum") {
                    param.result = 114514
                }
            }
    }

    override val key: String get() = "remove_media_limit"
}
