package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.content.Context
import android.view.View
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.qzone.proxy.feedcomponent.model.BusinessFeedData
import com.tencent.mobileqq.vas.adv.common.data.AlumBasicData

@RegisterAction
class HideQzoneAD : IAction {

    override val name: String get() = "隐藏空间广告"
    override val desc: String get() = "隐藏好友动态里那无时无刻不在显示的广告。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            listOf(
                // super class
                "com.qzone.reborn.feedpro.itemview.ad.QZoneAdBaseMediaFeedProItemView",
                "com.qzone.reborn.feedx.itemview.ad.QZoneAdBaseFeedItemView",

                // FeedPro
                "com.qzone.reborn.feedpro.itemview.QzoneFeedProGeneralBigCardItemView",
                "com.qzone.reborn.feedpro.itemview.QZoneAdFeedProForwardMixPicVideoItemView",
                "com.qzone.reborn.feedpro.widget.comment.QZoneFeedProDetailBottomAdBlockView", // 说说详情页广告

                // FeedX
                "com.qzone.reborn.feedx.itemview.ad.QZoneAdRewardFeedItemView",
            ).forEach { name ->
                load(name)
                    ?.getDeclaredConstructor(Context::class.java)
                    ?.hookAfter { param ->
                        val view = param.thisObject as View
                        view.visibility = View.GONE
                        view.layoutParams.apply {
                            height = 0
                            width = 0
                        }
                    }
            }

            load("com.tencent.mobileqq.vas.adv.qzone.logic.AlbumRecommendAdvController")
                ?.hookMethodBefore({
                    name = "initAndRenderData"
                    paramTypes = arrayOf(AlumBasicData::class.java)
                }) { param ->
                    val alumBasicData = param.args[0] as AlumBasicData
                    alumBasicData.advimageUrl = ""
                    alumBasicData.videoUrl = ""
                    alumBasicData.videoReportUrl = ""
                    alumBasicData.negativeFeedbackUrl = ""
                    alumBasicData.clickUrl = ""
                    alumBasicData.advLogoUrl = ""
                }
        }

        if (HookEnv.isTim()) {
            loadOrThrow("com.qzone.proxy.feedcomponent.model.gdt.QZoneAdFeedDataExtKt")
                .hookMethodBefore({
                    name = "isShowingRecommendAd"
                    paramTypes = arrayOf(BusinessFeedData::class.java)
                }) { param ->
                    param.result = true
                }
        }
    }

    override val key: String get() = "hide_qzone_ad"
    override val processes: Set<ActionProcess>
        get() = setOf(
            ActionProcess.MAIN,
            ActionProcess.QZONE
        )
}
