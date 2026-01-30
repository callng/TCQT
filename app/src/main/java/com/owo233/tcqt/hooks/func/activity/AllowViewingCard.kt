package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import android.os.Bundle
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.getIntField
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.reflect.MethodUtils
import com.owo233.tcqt.utils.setIntField
import com.tencent.mobileqq.data.Card

@RegisterAction
@RegisterSetting(
    key = "allow_viewing_card",
    name = "允许查看异常资料卡",
    type = SettingType.BOOLEAN,
    desc = "忽略账号的异常状态，使其能够正常查看资料卡。",
    uiTab = "界面"
)
class AllowViewingCard : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        "com.tencent.mobileqq.profilecard.api.impl.ProfileDataServiceImpl".toHostClass().also { clazz ->
            hookProfileCardMethod(
                clazz,
                "getProfileCard",
                String::class.java,
                Boolean::class.javaPrimitiveType!!
            )
            hookProfileCardMethod(
                clazz,
                "getProfileCardFromCache",
                String::class.java
            )
        }

        "com.tencent.mobileqq.profilecard.processor.ProfileSecureProcessor".toHostClass().also { clazz ->
            MethodUtils.create(clazz)
                .named("processProfileCard")
                .params(
                    Bundle::class.java,
                    "SummaryCard.RespHead".toHostClass(),
                    "SummaryCard.RespSummaryCard".toHostClass()
                    )
                .findOrThrow()
                .hookBeforeMethod { param ->
                    val respHead = param.args.getOrNull(1) ?: return@hookBeforeMethod
                    val result = respHead.getIntField("iResult")
                    if (result == 201 || result == 202) {
                        respHead.setIntField("iResult", 0)
                    }
                }
        }
    }

    override val key: String
        get() = GeneratedSettingList.ALLOW_VIEWING_CARD

    private fun hookProfileCardMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>) {
        MethodUtils.create(clazz)
            .named(methodName)
            .params(*paramTypes)
            .returns<Card>()
            .findOrThrow()
            .hookAfterMethod { param ->
                val card = param.result as? Card ?: return@hookAfterMethod
                if (card.forbidCode == 201 || card.forbidCode == 202) {
                    card.isForbidAccount = false
                    card.forbidCode = 0
                }
            }
    }
}
