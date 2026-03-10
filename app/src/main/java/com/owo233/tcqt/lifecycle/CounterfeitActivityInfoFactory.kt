/**
 * 本页代码是 QFun 项目的一部分
 * https://github.com/oneQAQone/QFun
 */

package com.owo233.tcqt.lifecycle

import android.content.ComponentName
import android.content.pm.ActivityInfo
import com.owo233.tcqt.HookEnv

object CounterfeitActivityInfoFactory {

    fun makeProxyActivityInfo(className: String, flags: Long): ActivityInfo? {
        runCatching { Class.forName(className) }.getOrNull() ?: return null

        val ctx = HookEnv.hostAppContext
        val candidates = listOf(
            "com.tencent.mobileqq.activity.QQSettingSettingActivity",
            "com.tencent.mobileqq.activity.QPublicFragmentActivity"
        )

        val proto = candidates.firstNotNullOfOrNull { activityName ->
            runCatching {
                ctx.packageManager.getActivityInfo(
                    ComponentName(ctx.packageName, activityName),
                    flags.toInt()
                )
            }.getOrNull()
        } ?: throw IllegalStateException("Required host activity not found, are we in the host?")

        return proto.applyCommon(className)
    }

    private fun ActivityInfo.applyCommon(name: String): ActivityInfo = apply {
        targetActivity = null
        taskAffinity = null
        descriptionRes = 0
        this.name = name
        splitName = null
        configChanges = configChanges or ActivityInfo.CONFIG_UI_MODE
    }
}
