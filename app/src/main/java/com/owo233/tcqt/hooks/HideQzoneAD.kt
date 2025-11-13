package com.owo233.tcqt.hooks

import android.content.Context
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.paramCount

@RegisterAction
@RegisterSetting(
    key = "hide_qzone_ad",
    name = "隐藏QQ空间广告",
    type = SettingType.BOOLEAN,
    desc = "隐藏空间里那些烦人的广告，目前只针对改版的新空间，未改版的旧空间可能没有效果。可以打开「AB测试强制转B组」功能来强制使用新空间。",
    uiOrder = 27
)
class HideQzoneAD : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val clazz = XpClassLoader.load(
                "com.qzone.reborn.feedpro.itemview.ad.card.QZoneCardAdFeedProItemView"
            ) ?: error("隐藏QQ空间广告: 加载类QZoneCardAdFeedProItemView失败!")
        val method = clazz.declaredMethods.firstOrNull { m ->
            m.returnType == Void.TYPE && m.paramCount == 1 &&
                    m.parameterTypes[0].name.contains("com.qzone.reborn.feedpro.data.ad")
        } ?: error("隐藏QQ空间广告: 没有找到隐藏方法!")

        method.hookAfterMethod { param ->
            val view = param.thisObject as View
            view.visibility = View.GONE
            view.layoutParams = view.layoutParams.apply {
                height = 0
                width = 0
            }
            view.requestLayout()
        }
    }

    override val key: String get() = GeneratedSettingList.HIDE_QZONE_AD

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
