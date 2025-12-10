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
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.hookBeforeMethod
import com.qzone.proxy.feedcomponent.model.BusinessFeedData
import com.tencent.mobileqq.vas.adv.common.data.AlumBasicData

@RegisterAction
@RegisterSetting(
    key = "hide_qzone_ad",
    name = "隐藏QQ空间广告",
    type = SettingType.BOOLEAN,
    desc = "隐藏空间里那无时无刻不在显示的广告。",
    uiTab = "界面"
)
class HideQzoneAD : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (HookEnv.isQQ()) {
            listOf(
                "com.qzone.reborn.feedpro.itemview.ad.card.QZoneCardAdFeedProItemView",
                "com.qzone.reborn.feedpro.itemview.ad.card.QZoneCardMultiPicAdFeedProItemView",
                "com.qzone.reborn.feedpro.itemview.ad.carousel.QZoneCarouselCardVideoAdFeedProItemView",
                "com.qzone.reborn.feedpro.itemview.ad.contract.QZoneContractCardAdFeedProItemView",
                "com.qzone.reborn.feedx.itemview.ad.QZoneAdVideoFeedItemView", // 9.2.5 样式(好友动态)
                "com.qzone.reborn.feedx.itemview.ad.QZoneAdRewardFeedItemView",
                "com.qzone.reborn.feedx.itemview.ad.QZoneAdPictureFeedItemView",
                "com.qzone.reborn.feedx.itemview.ad.QZoneAdMDPAFeedItemView"
            ).forEach { name ->
                load(name)
                    ?.getDeclaredConstructor(Context::class.java)
                    ?.hookAfterMethod { param ->
                        val view = param.thisObject as View
                        view.visibility = View.GONE
                        view.layoutParams = view.layoutParams.apply {
                            height = 0
                            width = 0
                        }
                        view.requestLayout()
                    }
            }

            // 干掉"我的更多相册"页广告
            /*if (TextUtils.isEmpty(alumBasicData.advimageUrl)) {
                hideAdView();
                return;
            }*/
            load("com.tencent.mobileqq.vas.adv.qzone.logic.AlbumRecommendAdvController")
                ?.hookBeforeMethod(
                    "initAndRenderData",
                    AlumBasicData::class.java
                ) { param ->
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
                .hookBeforeMethod(
                    "isShowingRecommendAd",
                    BusinessFeedData::class.java
                ) { param ->
                    param.result = true
                }
        }
    }

    override val key: String get() = GeneratedSettingList.HIDE_QZONE_AD

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.QZONE)
}
