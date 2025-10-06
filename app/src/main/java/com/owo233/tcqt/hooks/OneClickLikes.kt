package com.owo233.tcqt.hooks

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.beforeHook
import com.owo233.tcqt.utils.hookMethod
import com.tencent.mobileqq.activity.VisitorsActivity
import com.tencent.mobileqq.data.CardProfile
import com.tencent.mobileqq.profile.vote.VoteHelper
import com.tencent.mobileqq.profilecard.base.component.AbsProfileHeaderComponent
import com.tencent.mobileqq.vas.api.IVasSingedApi
import de.robv.android.xposed.XposedBridge

@RegisterAction
@RegisterSetting(
    key = "one_click_likes",
    name = "一键20赞",
    type = SettingType.BOOLEAN,
    desc = "开启后点赞时将自动点赞20个（非SVIP为10个）。",
    uiOrder = 17
)
class OneClickLikes : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        // TIM不支持点赞行为
        if (PlatformTools.isMqq()) {
            val vote = VoteHelper::class.java.declaredMethods.firstOrNull {
                it.parameterCount == 2 && it.parameterTypes[0] == CardProfile::class.java && it.parameterTypes[1] == ImageView::class.java
            }
            val voteHelperField = VisitorsActivity::class.java.declaredFields.firstOrNull {
                it.type == VoteHelper::class.java
            }

            if (vote == null || voteHelperField == null) {
                Log.e("获取点赞方法失败")
                return
            }

            voteHelperField.isAccessible = true
            VisitorsActivity::class.java.hookMethod("onClick", beforeHook {
                val view = it.args[0] as View
                val tag = view.tag

                if (tag == null || tag !is CardProfile) return@beforeHook

                val voteHelper = voteHelperField.get(it.thisObject) as VoteHelper
                for (i in 0..<getMaxCount()) {
                    vote.invoke(voteHelper, tag, view)
                }

                it.result = Unit
            })

            AbsProfileHeaderComponent::class.java.hookMethod("handleVoteBtnClickForGuestProfile", beforeHook {
                for (i in 0..<getMaxCount()) {
                    XposedBridge.invokeOriginalMethod(it.method, it.thisObject, it.args)
                }

                it.result = Unit
            })
        }
    }

    private fun getMaxCount(): Int {
        return if (isSVip()) 20 else 10
    }

    private fun isSVip(): Boolean {
        runCatching {
            val service = QQInterfaces.appRuntime.getRuntimeService(IVasSingedApi::class.java, "all")
            return service.vipStatus.isSVip
        }.onFailure {
            Log.e("获取账号会员状态失败", it)
        }

        return false
    }

    override val key: String get() = GeneratedSettingList.ONE_CLICK_LIKES

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
