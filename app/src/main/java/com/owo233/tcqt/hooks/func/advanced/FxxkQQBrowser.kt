package com.owo233.tcqt.hooks.func.advanced

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.findMethod
import java.util.regex.Pattern

@RegisterAction
@RegisterSetting(
    key = "fxxk_qq_browser",
    name = "去你大爷的QQ浏览器",
    type = SettingType.BOOLEAN,
    desc = "在宿主内访问网页时，强制使用系统默认浏览器打开，而非使用内置浏览器。",
    uiTab = "高级"
)
class FxxkQQBrowser : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        hookExecStartActivity()
    }

    override val key: String
        get() = GeneratedSettingList.FXXK_QQ_BROWSER

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.ALL)

    private fun hookExecStartActivity() {
        Instrumentation::class.java.findMethod {
            name = "execStartActivity"
            paramTypes(context, IBinder::class.java, IBinder::class.java,
                Activity::class.java, Intent::class.java, int, bundle
            )}.hookAfter { param ->
            val intent = param.args.getOrNull(4) as? Intent ?: return@hookAfter
            val url = intent.getStringExtra("url") ?: return@hookAfter

            if (!shouldHijack(intent, url)) {
                return@hookAfter
            }

            openWithCustomTabs(url)
            param.result = null
        }
    }

    private fun shouldHijack(intent: Intent, url: String): Boolean {
        if (!URL_PATTERN.matcher(url.lowercase()).matches()) return false
        if (PlatformTools.isHostWhitelisted(url)) return false

        val shortName = intent.component?.shortClassName ?: return false
        return shortName.contains("QQBrowserActivity")
    }

    private fun openWithCustomTabs(rawUrl: String) {
        val uri = rawUrl.toWebUri()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setColorScheme(
                if (HookEnv.isNightMode())
                    CustomTabsIntent.COLOR_SCHEME_DARK
                else
                    CustomTabsIntent.COLOR_SCHEME_LIGHT
            )
            .setShowTitle(true)
            .build()

        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(HookEnv.application, uri)
    }

    private fun String.toWebUri(): Uri {
        return if (startsWith("http://") || startsWith("https://")) {
            toUri()
        } else {
            "http://$this".toUri()
        }
    }

    companion object {
        private val URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$|^www\\.[^.]+\\.[^.]+$|^[^.]+\\.[^.]+$"
        )
    }
}
