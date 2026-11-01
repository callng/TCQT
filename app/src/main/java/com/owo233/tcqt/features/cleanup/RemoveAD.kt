package com.owo233.tcqt.features.cleanup

import android.os.Message
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.env.ClassCacheUtils
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.emptyParam
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.hook.isFinal
import com.owo233.tcqt.core.hook.isNotStatic
import com.owo233.tcqt.core.hook.isPublic
import com.owo233.tcqt.core.hook.paramCount

@RegisterAction(priority = ActionPriority.CRITICAL)
object RemoveAD : Feature(
    key = "remove_ad",
    name = "移除部分广告",
    desc = "移除一些常见的广告弹窗。",
) {


    override fun install() {
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
            ?.onEach { it.hookBefore { p -> p.result = null } }
    }

    private fun removeKeywordAD() {
        if (HookEnv.isQQ()) {
            loadOrThrow("com.tencent.mobileqq.springhb.interactive.ui.InteractivePopManager")
                .declaredMethods
                .firstOrNull {
                    it.isPublic && it.paramCount > 0 &&
                            it.parameterTypes[0].name == "androidx.fragment.app.Fragment"
                }?.hookBefore { param -> param.result = null }
            load("com.tencent.mobileqq.aio.animation.pag.PagEasterEggPopManager")
                ?.declaredMethods
                ?.firstOrNull {
                    it.isPublic && it.paramCount > 0 &&
                            it.parameterTypes[0].name == "androidx.fragment.app.Fragment"
                }?.hookBefore { param -> param.result = null }
        }
    }

    private fun removePopupAD() {
        load(
            "com.tencent.mobileqq.activity.recent.bannerprocessor.VasADBannerProcessor"
        )?.hookMethodBefore({
            name = "updateBanner"
            paramTypes = arrayOf(null, Message::class.java)
        }) {
            it.result = null
        }

        load("cooperation.vip.ad.GrowHalfLayerHelper")
            ?.declaredMethods
            ?.firstOrNull { method ->
                method.returnType == Void.TYPE && method.isPublic &&
                        method.isFinal && method.paramCount == 3 &&
                        method.parameterTypes[0].name == "android.app.Activity"
            }?.hookBefore { param -> param.result = null }
    }
}
