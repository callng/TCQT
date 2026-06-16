package com.owo233.tcqt.internals

import android.app.Activity
import android.content.Context
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.hooks.helper.NTServiceFetcher
import com.owo233.tcqt.hooks.maple.Maple
import com.owo233.tcqt.internals.helper.GuidHelper
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.TIMVersion
import com.owo233.tcqt.utils.context.ContextUtils
import com.tencent.mobileqq.app.QBaseActivity
import com.tencent.mobileqq.mqq.api.IAccountRuntime
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import mqq.app.AppRuntime
import mqq.app.Foreground
import mqq.app.MobileQQ

open class QQInterfaces {

    companion object {
        val appRuntime: AppRuntime get() = MobileQQ.getMobileQQ().waitAppRuntime(null)

        val context: Context get() = QRoute.api(IAccountRuntime::class.java).applicationContext

        val isLogin: Boolean get() = appRuntime.isLogin

        val currentUin: String get() = appRuntime.currentAccountUin ?: ""

        val currentUid: String get() = appRuntime.currentUid ?: ""

        val guid: String get() = GuidHelper.getGuid()

        val topActivity: Activity
            get() = QBaseActivity.sTopActivity
                ?: Foreground.getTopActivity()
                ?: ContextUtils.getCurrentActivity()

        val maple by lazy {
            val usePublic =
                HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_70_BETA_17590) ||
                        HookEnv.requireMinTimVersion(TIMVersion.TIM_4_0_95_BETA)
            if (usePublic) Maple.PublicKernel else Maple.Kernel
        }

        val msgService: IKernelMsgService
            get() = NTServiceFetcher.kernelService
                .wrapperSession
                .msgService

        fun getServiceTime(): Long = runCatching {
            loadOrThrow("com.tencent.mobileqq.msf.core.NetConnInfoCenter")
                .getDeclaredMethod("getServerTimeMillis").apply { isAccessible = true }
                .invoke(null) as Long
        }.getOrDefault(0L)
    }
}
