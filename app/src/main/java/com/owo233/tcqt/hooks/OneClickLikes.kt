package com.owo233.tcqt.hooks

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.logE
import com.tencent.mobileqq.activity.VisitorsActivity
import com.tencent.mobileqq.data.CardProfile
import com.tencent.mobileqq.profile.vote.VoteHelper
import com.tencent.mobileqq.profilecard.base.component.AbsProfileHeaderComponent
import com.tencent.mobileqq.vas.api.IVasSingedApi
import de.robv.android.xposed.XposedBridge

@RegisterAction
class OneClickLikes: IAction {

    override fun onRun(ctx: Context) {
        // TIM不支持点赞行为
        if (PlatformTools.isMqq()) {
            val vote = VoteHelper::class.java.declaredMethods.firstOrNull {
                it.parameterCount == 2 && it.parameterTypes[0] == CardProfile::class.java && it.parameterTypes[1] == ImageView::class.java
            }
            val voteHelperField = VisitorsActivity::class.java.declaredFields.firstOrNull {
                it.type == VoteHelper::class.java
            }

            if (vote == null || voteHelperField == null) {
                logE(msg = "获取点赞方法失败")
                return
            }

            voteHelperField.isAccessible = true
            VisitorsActivity::class.java.hookMethod("onClick").before {
                val view = it.args[0] as View
                val tag = view.tag

                if (tag == null || tag !is CardProfile) return@before

                val voteHelper = voteHelperField.get(it.thisObject) as VoteHelper
                for (i in 0..<getMaxCount()) {
                    vote.invoke(voteHelper, tag, view)
                }

                it.result = Unit
            }

            AbsProfileHeaderComponent::class.java.hookMethod("handleVoteBtnClickForGuestProfile").before {
                for (i in 0..<getMaxCount()) {
                    XposedBridge.invokeOriginalMethod(it.method, it.thisObject, it.args)
                }

                it.result = Unit
            }
        }
    }

    private fun getMaxCount(): Int {
        return if (isSVip()) 20 else 10
    }

    private fun isSVip(): Boolean {
        runCatching {
            val service = QQInterfaces.app.getRuntimeService(IVasSingedApi::class.java, "all")
            return service.vipStatus.isSVip
        }.onFailure {
            logE(msg = "获取账号会员状态失败", cause = it)
        }

        return false
    }

    override val name: String get() = "一键点赞"

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

}
