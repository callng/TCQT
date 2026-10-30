package com.owo233.tcqt.ui.parasitic

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.ui.settings.BaseComposeActivity

internal object ActivityProxyIntents {

    const val STUB_ACTIVITY = "com.tencent.mobileqq.activity.photo.CameraPreviewActivity"
    // 保留键名，支持模块热重载前已发出但尚未分发的启动事务。
    private const val PROXY_EXTRA = "ACTIVITY_PROXY_INTENT"

    fun activityClass(name: String): Class<out Activity>? {
        DynamicActivityRegistry.getActivityClass(name)?.let { return it }
        if (!name.startsWith("${TCQTBuild.APP_ID}.")) return null
        val clazz = try {
            HookEnv.moduleClassLoader.loadClass(name)
        } catch (_: ClassNotFoundException) {
            return null
        }
        if (!BaseComposeActivity::class.java.isAssignableFrom(clazz)) return null
        return clazz.asSubclass(Activity::class.java)
    }

    fun wrap(intent: Intent): Intent {
        val component = intent.component ?: return intent
        if (component.packageName != HookEnv.hostAppPackageName ||
            activityClass(component.className) == null
        ) return intent

        // 保留 flags、data/type、ClipData/URI grant、categories 等系统启动语义。
        // 模块自定义 Parcelable 留在内层，避免 system_server 解包模块类。
        return intent.cloneFilter().apply {
            this.component = ComponentName(component.packageName, STUB_ACTIVITY)
            flags = intent.flags
            clipData = intent.clipData
            putExtra(PROXY_EXTRA, intent)
        }
    }

    @Suppress("DEPRECATION")
    fun unwrap(wrapper: Intent): Intent? {
        val component = wrapper.component ?: return null
        if (component.packageName != HookEnv.hostAppPackageName ||
            component.className != STUB_ACTIVITY
        ) return null
        // 不读取普通宿主 Intent 的 extras，也不改动它的 ClassLoader。
        val copy = Intent(wrapper).apply { setExtrasClassLoader(HookEnv.moduleClassLoader) }
        val original = copy.getParcelableExtra<Intent>(PROXY_EXTRA) ?: return null
        val target = original.component ?: return null
        if (target.packageName != HookEnv.hostAppPackageName ||
            activityClass(target.className) == null
        ) return null
        original.setExtrasClassLoader(HookEnv.moduleClassLoader)
        return original
    }
}
