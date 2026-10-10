package com.owo233.tcqt.host

import android.app.Activity
import android.content.Context
import com.owo233.tcqt.core.env.ContextUtils
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.TIMVersion
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.env.runRetry
import com.owo233.tcqt.host.service.GuidReader
import com.owo233.tcqt.host.service.NTServiceFetcher
import com.owo233.tcqt.host.service.maple.Maple
import com.tencent.common.app.AppInterface
import com.tencent.mobileqq.app.QBaseActivity
import com.tencent.mobileqq.mqq.api.IAccountRuntime
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.qroute.QRouteApi
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupService
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import mqq.app.Foreground
import mqq.app.MobileQQ
import mqq.app.api.IRuntimeService

open class QQInterfaces {

    companion object {
        val appRuntime: AppInterface
            get() = runRetry(
                retryNum = 50,
                sleepMs = 100L,
            ) {
                MobileQQ.getMobileQQ().peekAppRuntime() as? AppInterface
            } ?: throw IllegalStateException("QQInterfaces appRuntime: peekAppRuntime is null")

        val context: Context get() = QRoute.api(IAccountRuntime::class.java).applicationContext

        val isLogin: Boolean get() = appRuntime.isLogin

        val currentUin: String get() = appRuntime.currentAccountUin ?: ""

        val currentUid: String get() = appRuntime.currentUid ?: ""

        val guid: String get() = GuidReader.getGuid()

        /** May be null while QQ is running in background. */
        val topActivity: Activity?
            get() = runCatching { QBaseActivity.sTopActivity }
                .getOrNull()
                ?: runCatching { Foreground.getTopActivity() }.getOrNull()
                ?: ContextUtils.getCurrentActivity()

        val maple by lazy {
            val usePublic =
                HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_70_BETA_17590) ||
                        HookEnv.requireMinTimVersion(TIMVersion.TIM_4_0_95_BETA)
            if (usePublic) Maple.PublicKernel else Maple.Kernel
        }

        /** `getWrapperSession` may be null during early init. */
        val msgService: IKernelMsgService
            get() = NTServiceFetcher.kernelService
                .wrapperSession
                .msgService

        /** `getWrapperSession` may be null during early init. */
        val groupService: IKernelGroupService
            get() = NTServiceFetcher.kernelService
                .wrapperSession
                .groupService

        fun getServiceTime(): Long = runCatching {
            loadOrThrow("com.tencent.mobileqq.msf.core.NetConnInfoCenter")
                .getDeclaredMethod("getServerTimeMillis").apply { isAccessible = true }
                .invoke(null) as Long
        }.getOrDefault(0L)

        inline fun <reified T : QRouteApi> api(): T {
            return QRoute.api(T::class.java)
        }

        inline fun <reified T : IRuntimeService> runtime(): T {
            return appRuntime.getRuntimeService(T::class.java, "all")
        }
    }
}
