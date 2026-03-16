package com.owo233.tcqt.hooks.func.misc

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.servlet.MiniSvc
import com.owo233.tcqt.utils.hookAfterMethod
import java.util.concurrent.atomic.AtomicBoolean

@RegisterAction
@RegisterSetting(
    key = "card_fetcher",
    name = "自动领取达人补登卡",
    type = SettingType.BOOLEAN,
    desc = "在进入QQ达人页时自动领取当天的补登卡，不用再玩**的**小程序游戏！",
    uiTab = "杂项"
)
class CardFetcher : IAction {

    override val key: String
        get() = GeneratedSettingList.CARD_FETCHER

    override fun onRun(ctx: Context, process: ActionProcess) {
        Application::class.java.hookAfterMethod("onCreate") { param ->
            val application = param.thisObject as? Application ?: return@hookAfterMethod
            registerLifecycleCallbacksIfNeeded(application)
        }
    }

    private fun registerLifecycleCallbacksIfNeeded(application: Application) {
        if (!lifecycleRegistered.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(CardFetcherLifecycleCallbacks())
    }

    private inner class CardFetcherLifecycleCallbacks : SimpleActivityLifecycleCallbacks() {

        override fun onActivityResumed(activity: Activity) {
            if (!activity.isTargetBrowserActivity()) return
            if (!activity.isTargetDarenPage()) return

            activity.window?.decorView?.postDelayed(
                {
                    if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                    MiniSvc.judgeTiming()
                },
                FETCH_DELAY_MILLIS
            )
        }
    }

    private fun Activity.isTargetBrowserActivity(): Boolean {
        return javaClass.name == QQ_BROWSER_ACTIVITY
    }

    private fun Activity.isTargetDarenPage(): Boolean {
        val url = intent?.getStringExtra(KEY_URL) ?: return false
        return url.startsWith(QQ_DAREN_URL_PREFIX)
    }

    private open class SimpleActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        private const val QQ_BROWSER_ACTIVITY = "com.tencent.mobileqq.activity.QQBrowserActivity"
        private const val QQ_DAREN_URL_PREFIX = "https://ti.qq.com/qqdaren/index"
        private const val KEY_URL = "url"
        private const val FETCH_DELAY_MILLIS = 1500L

        private val lifecycleRegistered = AtomicBoolean(false)
    }
}
