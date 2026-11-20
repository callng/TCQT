package com.owo233.tcqt.features.checkin

import android.content.Context
import android.content.Intent
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ModuleComponents
import com.owo233.tcqt.core.log.Log

/**
 * 群打卡监控面板入口。
 *
 * 独立于「群打卡」开关：关闭自动打卡后仍可查看今天已经打过卡的群。
 * `ActionUiType.ENTRY` 不安装 hook，只负责打开页面。
 */
@RegisterAction(processes = [ActionProcess.MAIN])
object GroupCheckInLogEntry : Feature(
    key = "group_check_in_log_entry",
    name = "今日打卡记录",
    desc = "查看今天已成功打卡的群号、群名与时间；每天 0 点自动清空。",
    uiOrder = 13,
    uiType = ActionUiType.ENTRY,
) {

    override fun install() = Unit

    override fun onUiClick(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent()
                .setClassName(HookEnv.hostAppPackageName, ModuleComponents.GROUP_CHECK_IN_LOG_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.onFailure {
        Log.e("群打卡：打开今日打卡记录失败", it)
    }.getOrDefault(false)
}
