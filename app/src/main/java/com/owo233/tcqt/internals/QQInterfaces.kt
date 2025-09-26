package com.owo233.tcqt.internals

import com.owo233.tcqt.hooks.maple.Maple
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.PlatformTools.QQ_9_0_70_VER
import com.owo233.tcqt.utils.PlatformTools.TIM_4_0_95_VER
import com.tencent.common.app.AppInterface
import com.owo233.tcqt.utils.PlatformTools.isMqqPackage
import com.owo233.tcqt.utils.PlatformTools.getHostVersionCode
import mqq.app.MobileQQ

open class QQInterfaces {
    companion object {
        val appRuntime by lazy {
            (if (isMqqPackage())
                MobileQQ.getMobileQQ().waitAppRuntime()
            else
                MobileQQ.getMobileQQ().waitAppRuntime(null)) as AppInterface
        }

        val maple by lazy {
            val ver = getHostVersionCode()
            val usePublic = (PlatformTools.isMqq() && ver >= QQ_9_0_70_VER) ||
                    (PlatformTools.isTim() && ver >= TIM_4_0_95_VER)
            if (usePublic) Maple.PublicKernel else Maple.Kernel
        }
    }
}
