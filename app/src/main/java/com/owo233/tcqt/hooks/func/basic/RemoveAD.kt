package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import android.os.Message
import android.view.View
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionPriority
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.ClassCacheUtils
import com.owo233.tcqt.utils.hook.emptyParam
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.hook.isFinal
import com.owo233.tcqt.utils.hook.isNotStatic
import com.owo233.tcqt.utils.hook.isPublic
import com.owo233.tcqt.utils.hook.paramCount

@RegisterAction
class RemoveAD : IAction {

    override val key: String get() = "remove_ad"
    override val name: String get() = "移除部分广告"
    override val desc: String get() = "移除一些常见的广告弹窗。"
    override val uiTab: String get() = "基础"
    override val priority: ActionPriority get() = ActionPriority.CRITICAL

    override fun onRun(app: Application, process: ActionProcess) {
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
