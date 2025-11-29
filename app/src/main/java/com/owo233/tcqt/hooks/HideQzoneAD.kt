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
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.paramCount
import com.qzone.proxy.feedcomponent.model.BusinessFeedData

@RegisterAction
@RegisterSetting(
    key = "hide_qzone_ad",
    name = "隐藏QQ空间广告",
    type = SettingType.BOOLEAN,
    desc = "隐藏空间里那无时无刻不在显示的广告。",
    uiOrder = 27
)
class HideQzoneAD : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            loadOrThrow("com.qzone.reborn.feedpro.itemview.ad.card.QZoneCardAdFeedProItemView")
                .declaredMethods
                .first { it.returnType == Void.TYPE && it.paramCount == 1 &&
                        it.parameterTypes[0].name.contains("com.qzone.reborn.feedpro.data.ad") }
                .hookAfterMethod { param ->
                    val view = param.thisObject as View
                    view.visibility = View.GONE
                    view.layoutParams = view.layoutParams.apply {
                        height = 0
                        width = 0
                    }
                    view.requestLayout()
                }
        }

        if (HookEnv.isTim()) {
            loadOrThrow("com.qzone.proxy.feedcomponent.model.gdt.QZoneAdFeedDataExtKt")
                .hookBeforeMethod(
                    "isShowingRecommendAd",
                    BusinessFeedData::class.java
                ) { param ->
                    param.result = true
                }
        }
    }

    override val key: String get() = GeneratedSettingList.HIDE_QZONE_AD

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
