package com.owo233.tcqt.features.general

import android.view.View
import android.widget.ImageView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.PlatformTools
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.hook.hookMethodReplace
import com.owo233.tcqt.core.hook.invokeOriginal
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.mobileqq.activity.VisitorsActivity
import com.tencent.mobileqq.data.CardProfile
import com.tencent.mobileqq.profile.vote.VoteHelper
import com.tencent.mobileqq.profilecard.base.component.AbsProfileHeaderComponent
import com.tencent.mobileqq.vas.api.IVasSingedApi

@RegisterAction(processes = [ActionProcess.MAIN])
object OneClickLikes : Feature(
    key = "one_click_likes",
    name = "一键20赞",
    desc = "开启后点赞时将自动点赞20个（非SVIP为10个）。",
) {


    override fun install() {
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

}
