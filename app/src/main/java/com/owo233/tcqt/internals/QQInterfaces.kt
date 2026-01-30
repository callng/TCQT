package com.owo233.tcqt.internals

import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.hooks.maple.Maple
import com.owo233.tcqt.internals.helper.GuidHelper
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.PlatformTools.getHostVersionCode
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import mqq.app.AppRuntime
import mqq.app.MobileQQ

open class QQInterfaces {

    companion object {
        val appRuntime: AppRuntime get() = MobileQQ.getMobileQQ().waitAppRuntime(null)

        val isLogin: Boolean inline get() = appRuntime.isLogin

        val currentUin: String inline get() = appRuntime.currentAccountUin ?: ""

        val currentUid: String inline get() = appRuntime.currentUid ?: ""

        val guid: String inline get() = GuidHelper.getGuid()

        val maple by lazy {
            val ver = getHostVersionCode()
            val usePublic = (PlatformTools.isMqq() && ver >= PlatformTools.QQ_9_0_70_VER) ||
                    (PlatformTools.isTim() && ver >= PlatformTools.TIM_4_0_95_VER)
            if (usePublic) Maple.PublicKernel else Maple.Kernel
        }

        val msgService: IKernelMsgService inline get() = NTServiceFetcher.kernelService
            .wrapperSession
            .msgService

        fun getServiceTime(): Long = runCatching {
            loadOrThrow("com.tencent.mobileqq.msf.core.NetConnInfoCenter")
                .getDeclaredMethod("getServerTimeMillis").apply { isAccessible = true }
                .invoke(null) as Long
        }.getOrDefault(0L)
    }
}
