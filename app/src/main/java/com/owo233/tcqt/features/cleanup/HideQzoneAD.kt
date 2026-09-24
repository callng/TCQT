package com.owo233.tcqt.features.cleanup

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.reflect.findMethod
import com.qzone.proxy.feedcomponent.model.BusinessFeedData
import com.tencent.mobileqq.vas.adv.common.data.AlumBasicData

@RegisterAction
object HideQzoneAD : Feature(
    key = "hide_qzone_ad",
    name = "隐藏空间广告",
    desc = "隐藏好友动态里那无时无刻不在显示的广告。",
    processes = setOf(ActionProcess.MAIN, ActionProcess.QZONE),
) {

    override fun install() {
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
                        val lp = view.layoutParams
                        if (lp != null) {
                            lp.height = 0
                            lp.width = 0
                        } else {
                            view.layoutParams = ViewGroup.LayoutParams(0, 0)
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

        if (HookEnv.isTIM()) {
            "com.qzone.proxy.feedcomponent.model.gdt.QZoneAdFeedDataExtKt".toClass.findMethod {
                name = "isShowingRecommendAd"
                paramTypes(BusinessFeedData::class.java)
            }.hookBefore { param ->
                param.result = true
            }
        }
    }
}
