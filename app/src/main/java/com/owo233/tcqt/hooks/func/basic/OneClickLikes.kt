package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import android.view.View
import android.widget.ImageView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.hook.hookMethodReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findField
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.mobileqq.activity.VisitorsActivity
import com.tencent.mobileqq.data.CardProfile
import com.tencent.mobileqq.profile.vote.VoteHelper
import com.tencent.mobileqq.profilecard.base.component.AbsProfileHeaderComponent
import com.tencent.mobileqq.vas.api.IVasSingedApi

@RegisterAction
@RegisterSetting(
    key = "one_click_likes",
    name = "一键20赞",
    type = SettingType.BOOLEAN,
    desc = "开启后点赞时将自动点赞20个（非SVIP为10个）。",
)
class OneClickLikes : IAction {

    override fun onRun(app: Application, process: ActionProcess) {
        // TIM不支持点赞行为
        if (PlatformTools.isMqq()) {
            val vote = VoteHelper::class.java.findMethod {
                paramCount = 2
                paramTypes = arrayOf(CardProfile::class.java, ImageView::class.java)
            }

            val voteHelperField = VisitorsActivity::class.java.findField {
                type = VoteHelper::class.java
            }

            VisitorsActivity::class.java.hookMethodBefore(
                "onClick",
                View::class.java
            ) { param ->
                val view = param.args[0] as View
                val tag = view.tag

                if (tag == null || tag !is CardProfile) return@hookMethodBefore

                val voteHelper = voteHelperField.get(param.thisObject) as VoteHelper

                repeat(getMaxCount()) {
                    vote.invoke(voteHelper, tag, view)
                }

                param.result = Unit
            }

            AbsProfileHeaderComponent::class.java.hookMethodReplace(
                "handleVoteBtnClickForGuestProfile",
                load("com.tencent.mobileqq.data.Card")
            ) { param ->
                repeat(getMaxCount()) {
                    param.invokeOriginal()
                }

                null
            }
        }
    }

    private fun getMaxCount(): Int {
        return if (isSVip()) 20 else 10
    }

    private fun isSVip(): Boolean {
        runCatching {
            val service =
                QQInterfaces.appRuntime.getRuntimeService(IVasSingedApi::class.java, "all")
            return service.vipStatus.isSVip
        }.onFailure {
            Log.e("获取账号会员状态失败", it)
        }

        return false
    }

    override val key: String get() = GeneratedSettingList.ONE_CLICK_LIKES

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
