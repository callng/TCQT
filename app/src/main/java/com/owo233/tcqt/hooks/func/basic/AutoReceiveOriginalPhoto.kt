// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findMethodOrNull
import com.owo233.tcqt.utils.reflect.invoke
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import java.lang.reflect.Method

@RegisterAction
class AutoReceiveOriginalPhoto : IAction, DexKitTask {

    override val name: String get() = "聊天自动接收原图"
    override val desc: String get() = "打开聊天图片时自动加载并显示原图。"
    override val uiTab: String get() = "基础"
    override val key: String get() = "auto_receive_original_photo"

    override fun onRun(app: Application, process: ActionProcess) {
        val onInitView = runCatching { requireMethod(ORIGINAL_PHOTO_ON_INIT_VIEW) }.getOrNull()
            ?: Reflection.findOnInitView()
            ?: return

        onInitView.hookAfter { param ->
            if (param.args.getOrNull(0) != ORIGINAL_LAYER_SHOW) return@hookAfter

            runCatching {
                param.thisObject.invoke("loadOriginImageInner")
                val listener = param.thisObject.invoke("getMLayerOperateListener")
                listener.invoke("clickShowOriginPicBtn")
            }.onFailure {
                Log.e("聊天自动接收原图: 自动加载原图失败", it)
            }
        }
    }

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        bridge.findMethod(
            FindMethod().apply {
                matcher {
                    name = "onInitView"
                    paramCount = 1
                    usingStrings("rootView", "em_bas_view_the_original_picture")
                }
            }
        ).firstOrNull()?.let { cache[ORIGINAL_PHOTO_ON_INIT_VIEW] = it.descriptor }
            ?: Log.d("聊天自动接收原图: 未定位原图层初始化方法")
    }

    private object Reflection {

        fun findOnInitView(): Method? {
            val candidates = listOf(
                "com.tencent.qqnt.aio.gallery.part.d",
                "com.tencent.qqnt.aio.gallery.part.OriginalPhotoLayer"
            )
            return candidates.asSequence()
                .mapNotNull { load(it) }
                .mapNotNull { clazz ->
                    clazz.findMethodOrNull {
                        name = "onInitView"
                        paramCount = 1
                    }
                }
                .firstOrNull()
        }
    }

    companion object {
        private const val ORIGINAL_LAYER_SHOW = 0
        private const val ORIGINAL_PHOTO_ON_INIT_VIEW = "AutoReceiveOriginalPhoto.onInitView"
    }
}
