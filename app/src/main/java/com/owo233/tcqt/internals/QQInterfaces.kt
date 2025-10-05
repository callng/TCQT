package com.owo233.tcqt.internals

import com.owo233.tcqt.hooks.maple.Maple
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.PlatformTools.getHostVersionCode
import mqq.app.AppRuntime
import mqq.app.MobileQQ

open class QQInterfaces {
    companion object {
        val appRuntime: AppRuntime get() = MobileQQ.getMobileQQ().waitAppRuntime(null)

        val currentUin: Long inline get() = appRuntime.longAccountUin

        val currentUid: String inline get() = appRuntime.currentUid

        val maple by lazy {
            val ver = getHostVersionCode()
            val usePublic = (PlatformTools.isMqq() && ver >= PlatformTools.QQ_9_0_70_VER) ||
                    (PlatformTools.isTim() && ver >= PlatformTools.TIM_4_0_95_VER)
            if (usePublic) Maple.PublicKernel else Maple.Kernel
        }
    }
}
