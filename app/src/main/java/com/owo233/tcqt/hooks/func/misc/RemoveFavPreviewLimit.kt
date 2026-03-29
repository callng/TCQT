package com.owo233.tcqt.hooks.func.misc

import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.PluginHook
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.setObject

@RegisterAction
@RegisterSetting(
    key = "remove_fav_preview_limit",
    name = "移除收藏预览限制",
    type = SettingType.BOOLEAN,
    desc = "移除收藏预览限制，它是这样说的。",
    uiTab = "杂项"
)
class RemoveFavPreviewLimit : PluginHook() {

    override val key: String
        get() = GeneratedSettingList.REMOVE_FAV_PREVIEW_LIMIT

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.QQFAV)

    override val pluginID: String
        get() = "qqfav.apk"

    override fun canRun(): Boolean {
        return GeneratedSettingList.getBoolean(key) &&
            HookEnv.isQQ() &&
            HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_85)
    }

    override fun startHook(classLoader: ClassLoader) {
        loadOrThrow("com.qqfav.FavoriteService", classLoader).findMethod {
            returnType = loadOrThrow("com.qqfav.data.FavoriteData", classLoader)
            paramTypes(long, boolean)
        }.hookAfter { param ->
            param.result!!.setObject("mSecurityBeat", 0)
        }
    }
}
