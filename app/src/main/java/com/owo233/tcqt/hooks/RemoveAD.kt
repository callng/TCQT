package com.owo233.tcqt.hooks

import android.content.Context
import android.view.View
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.ClassCacheUtils
import com.owo233.tcqt.utils.emptyParam
import com.owo233.tcqt.utils.hookBeforeAllMethods
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.isFinal
import com.owo233.tcqt.utils.isNotStatic
import com.owo233.tcqt.utils.isPublic
import com.owo233.tcqt.utils.paramCount

@RegisterAction
@RegisterSetting(
    key = "remove_ad",
    name = "移除部分广告",
    type = SettingType.BOOLEAN,
    desc = "移除一些常见的广告弹窗。",
)
class RemoveAD : IAction {

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
            syntheticIndex(1, 2, 3, 5)
        }?.declaredMethods
            ?.filter { it.returnType == View::class.java && it.emptyParam && it.isNotStatic }
            ?.onEach { it.hookBeforeMethod { p -> p.result = Unit } }
    }

    private fun removeKeywordAD() {
        if (HookEnv.isQQ()) {
            loadOrThrow(
                "com.tencent.mobileqq.springhb.interactive.ui.InteractivePopManager"
            ).declaredMethods.firstOrNull {
                it.isPublic && it.paramCount > 0 &&
                        it.parameterTypes[0].name == "androidx.fragment.app.Fragment"
            }?.hookBeforeMethod { param -> param.result = Unit }
        }
    }

    private fun removePopupAD() {
        load(
            "com.tencent.mobileqq.activity.recent.bannerprocessor.VasADBannerProcessor"
        )?.hookBeforeAllMethods("updateBanner") {
            it.result = Unit
        }

        load("cooperation.vip.ad.GrowHalfLayerHelper")
            ?.declaredMethods
            ?.firstOrNull { method ->
                method.returnType == Void.TYPE && method.isPublic &&
                        method.isFinal && method.paramCount == 3 &&
                        method.parameterTypes[0].name == "android.app.Activity"
            }?.hookBeforeMethod { param -> param.result = Unit }
    }

    override val key: String get() = GeneratedSettingList.REMOVE_AD
}
