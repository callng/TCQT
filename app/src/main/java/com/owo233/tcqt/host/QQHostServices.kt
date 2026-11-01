package com.owo233.tcqt.host

import android.content.Context
import com.owo233.tcqt.core.env.HostServices
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.tencent.mobileqq.pluginsdk.PluginStatic
import com.tencent.mobileqq.vas.theme.api.ThemeUtil
import com.tencent.util.QQToastUtil
import cooperation.qzone.QUA

internal object QQHostServices : HostServices {
    override fun isNightMode(): Boolean = ThemeUtil.isNowThemeIsNight(null, true, null) ||
        HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_55_BETA_32895) && ThemeUtil.isThemeNightModeV2()

    override fun pluginClassLoader(context: Context, pluginName: String): ClassLoader =
        PluginStatic.getOrCreateClassLoader(context, pluginName)

    override fun alternateVersionCode(): Long =
        QUA.getQUA3().split("_").getOrNull(4)?.toLongOrNull() ?: 0L

    override fun showToast(icon: Int, message: String) = QQToastUtil.showQQToastInUiThread(icon, message)
}
