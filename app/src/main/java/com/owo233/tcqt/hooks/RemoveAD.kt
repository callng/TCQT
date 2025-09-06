package com.owo233.tcqt.hooks

import android.content.Context
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.replaceHook
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.ClassCacheUtils
import com.owo233.tcqt.utils.emptyParam
import com.owo233.tcqt.utils.isFinal
import com.owo233.tcqt.utils.isNotStatic
import com.owo233.tcqt.utils.isPublic
import com.owo233.tcqt.utils.paramCount
import de.robv.android.xposed.XposedBridge

@RegisterAction
@RegisterSetting(
    key = "remove_ad",
    name = "移除部分广告",
    type = SettingType.BOOLEAN,
    desc = "移除一些常见的广告弹窗。",
    uiOrder = 21
)
class RemoveAD: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        removeImmersionBannerAD()
        removeKeywordAD()
        removePopupAD()
    }

    private fun removeImmersionBannerAD() {
        ClassCacheUtils.findClass {
            candidates(
                "cooperation.vip.qqbanner.QbossADImmersionBannerManager",
                "cooperation.vip.qqbanner.manager.VasADImmersionBannerManager"
            )
            syntheticIndex(1, 2)
        }?.declaredMethods
            ?.filter { it.returnType == View::class.java && it.emptyParam && it.isNotStatic }
            ?.onEach { it.hookMethod(beforeHook { p -> p.result = Unit }) }

        XpClassLoader.load(
            "com.tencent.mobileqq.activity.recent.bannerprocessor.VasADBannerProcessor"
        )?.hookMethod("handleMessage", replaceHook { param ->
            return@replaceHook XposedBridge.invokeOriginalMethod(
                param.method,
                param.thisObject,
                param.args
            )
        })
    }

    private fun removeKeywordAD() {
        XpClassLoader.load(
            "com.tencent.mobileqq.springhb.interactive.ui.InteractivePopManager"
        )?.declaredMethods?.firstOrNull {
            it.paramCount > 0 && it.parameterTypes[0].name == "androidx.fragment.app.Fragment"
                    && it.isPublic && it.isFinal
        }?.hookMethod(beforeHook { param -> param.result = Unit})
    }

    private fun removePopupAD() {
        XpClassLoader.load(
            "com.tencent.mobileqq.activity.recent.bannerprocessor.VasADBannerProcessor"
        )?.hookMethod("updateBanner",beforeHook { it.result = Unit })

        XpClassLoader.load("cooperation.vip.ad.GrowHalfLayerHelper")
            ?.declaredMethods
            ?.firstOrNull { method ->
                method.returnType == Void.TYPE &&
                method.isPublic && method.isFinal && method.isNotStatic &&
                method.paramCount == 3 && method.parameterTypes[0].name == "android.app.Activity"
            }?.hookMethod(beforeHook { param -> param.result = Unit })
    }

    override val key: String get() = GeneratedSettingList.REMOVE_AD
}
