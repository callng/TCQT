// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import android.view.View
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.fieldValueAs
import com.owo233.tcqt.utils.reflect.findMethodOrNull
import com.owo233.tcqt.utils.reflect.findMethods
import com.owo233.tcqt.utils.reflect.invoke
import com.owo233.tcqt.utils.QQVersion
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import java.lang.reflect.Method
import java.util.WeakHashMap

@RegisterAction
class AutoReceiveOriginalPhoto : IAction, DexKitTask {

    override val name: String get() = "聊天自动接收原图"
    override val desc: String get() = "打开聊天图片时自动加载并显示原图。"
    override val uiTab: String get() = "基础"
    override val key: String get() = "auto_receive_original_photo"

    override fun onRun(app: Application, process: ActionProcess) {
        val hooked = if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_95)) {
            hookNewOriginPicSection() || hookLegacyOriginLayer()
        } else {
            hookLegacyOriginLayer() || hookNewOriginPicSection()
        }

        if (!hooked) {
            Log.d("聊天自动接收原图: 新旧原图接收逻辑均未定位")
        }
    }

    private fun hookNewOriginPicSection(): Boolean {
        val originPicSection = runCatching { requireMethod(NEW_ORIGIN_PIC_SECTION_ON_INIT_VIEW).declaringClass }.getOrNull()
            ?: load(ORIGIN_PIC_SECTION)
            ?: return false

        val hookMethods = Reflection.findAutoReceiveEntryMethods(originPicSection)
        if (hookMethods.isEmpty()) {
            return false
        }

        hookMethods.forEach { method ->
            method.hookAfter { param ->
                runCatching {
                    Reflection.performShowOriginClick(param.thisObject)
                }.onFailure {
                    Log.e("聊天自动接收原图: 自动点击查看原图失败", it)
                }
            }
        }
        return true
    }

    private fun hookLegacyOriginLayer(): Boolean {
        val onInitView = runCatching { requireMethod(LEGACY_ORIGINAL_LAYER_ON_INIT_VIEW) }.getOrNull()
            ?: Reflection.findLegacyOnInitView()
            ?: return false

        if (!Reflection.isLegacyOriginLayer(onInitView.declaringClass)) return false

        onInitView.hookAfter { param ->
            if (param.args.getOrNull(0) != ORIGINAL_LAYER_SHOW) return@hookAfter

            runCatching {
                param.thisObject.invoke("loadOriginImageInner")
                val listener = param.thisObject.invoke("getMLayerOperateListener")
                listener.invoke("clickShowOriginPicBtn")
            }.onFailure {
                Log.e("聊天自动接收原图: 旧版逻辑自动加载原图失败", it)
            }
        }
        return true
    }

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        val cached = if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_95)) {
            cacheNewOriginPicSection(bridge, cache) || cacheLegacyOriginLayer(bridge, cache)
        } else {
            cacheLegacyOriginLayer(bridge, cache) || cacheNewOriginPicSection(bridge, cache)
        }

        if (!cached) {
            Log.d("聊天自动接收原图: 新旧原图接收 DexKit 目标均未定位")
        }
    }

    private fun cacheNewOriginPicSection(
        bridge: DexKitBridge,
        cache: MutableMap<String, String>
    ): Boolean {
        val method = bridge.findMethod(
            FindMethod().apply {
                matcher {
                    name = "onInitView"
                    paramCount = 1
                    usingStrings("em_bas_view_the_original_picture")
                }
            }
        ).firstOrNull() ?: return false
        cache[NEW_ORIGIN_PIC_SECTION_ON_INIT_VIEW] = method.descriptor
        return true
    }

    private fun cacheLegacyOriginLayer(
        bridge: DexKitBridge,
        cache: MutableMap<String, String>
    ): Boolean {
        val method = bridge.findMethod(
            FindMethod().apply {
                matcher {
                    name = "onInitView"
                    paramCount = 1
                    usingStrings("rootView", "em_bas_view_the_original_picture")
                }
            }
        ).firstOrNull() ?: return false
        cache[LEGACY_ORIGINAL_LAYER_ON_INIT_VIEW] = method.descriptor
        return true
    }

    private object Reflection {

        private val clickedMedia = WeakHashMap<Any, String>()

        fun findAutoReceiveEntryMethods(clazz: Class<*>): List<Method> {
            val updateRawPic = clazz.findMethodOrNull {
                name = "updateRawPic"
                paramCount = 1
            }
            val onVisibleChanged = clazz.findMethodOrNull {
                name = "onVisibleChangedV2"
                paramTypes(boolean, boolean)
            }
            val onBindData = clazz.findMethods {
                name = "onBindData"
                paramCount = 3
            }

            return (listOfNotNull(updateRawPic, onVisibleChanged) + onBindData).distinct()
        }

        fun performShowOriginClick(section: Any) {
            if ((section.fieldValueAs<Int>("mButtonState", false) ?: 0) != BUTTON_STATE_CAN_SHOW_ORIGIN) {
                clickedMedia.remove(section)
                return
            }

            val originPicLayout = section.fieldValueAs<View>("originPicLayout", false) ?: return
            if (originPicLayout.visibility != View.VISIBLE || !originPicLayout.isShown) return

            val mediaToken = section.mediaToken()
            if (clickedMedia[section] == mediaToken) return
            clickedMedia[section] = mediaToken

            originPicLayout.post {
                runCatching {
                    if ((section.fieldValueAs<Int>("mButtonState", false) ?: 0) != BUTTON_STATE_CAN_SHOW_ORIGIN) {
                        clickedMedia.remove(section)
                        return@post
                    }
                    if (section.mediaToken() != mediaToken) {
                        clickedMedia.remove(section)
                        return@post
                    }
                    if (originPicLayout.visibility == View.VISIBLE && originPicLayout.isShown) {
                        originPicLayout.performClick()
                    } else {
                        clickedMedia.remove(section)
                    }
                }.onFailure {
                    Log.e("聊天自动接收原图: 自动点击查看原图失败", it)
                }
            }
        }

        private fun Any.mediaToken(): String {
            val mediaData = fieldValueAs<Any>("mData") ?: return "section:${System.identityHashCode(this)}"
            return runCatching { mediaData.invoke("getMediaId").toString() }
                .getOrNull()
                ?: "data:${System.identityHashCode(mediaData)}"
        }

        fun findLegacyOnInitView(): Method? {
            val candidates = listOf(
                "com.tencent.qqnt.aio.gallery.part.d",
                "com.tencent.qqnt.aio.gallery.part.OriginalPhotoLayer"
            )
            return candidates.asSequence()
                .mapNotNull { load(it) }
                .filter { isLegacyOriginLayer(it) }
                .mapNotNull { clazz ->
                    clazz.findMethodOrNull {
                        name = "onInitView"
                        paramCount = 1
                    }
                }
                .firstOrNull()
        }

        fun isLegacyOriginLayer(clazz: Class<*>): Boolean {
            return clazz.findMethodOrNull {
                name = "loadOriginImageInner"
                paramCount = 0
            } != null && clazz.findMethodOrNull {
                name = "getMLayerOperateListener"
                paramCount = 0
            } != null
        }

        private const val BUTTON_STATE_CAN_SHOW_ORIGIN = 1
    }

    companion object {
        private const val ORIGINAL_LAYER_SHOW = 0
        private const val NEW_ORIGIN_PIC_SECTION_ON_INIT_VIEW =
            "AutoReceiveOriginalPhoto.newOriginPicSectionOnInitView"
        private const val LEGACY_ORIGINAL_LAYER_ON_INIT_VIEW =
            "AutoReceiveOriginalPhoto.legacyOriginalLayerOnInitView"
        private const val ORIGIN_PIC_SECTION =
            "com.tencent.richframework.gallery.section.QQLayerCommonOriginPicSection"
    }
}
