package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadFromPlugin
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
class RemoveFavPreviewLimit : IAction {

    override val key: String
        get() = GeneratedSettingList.REMOVE_FAV_PREVIEW_LIMIT

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.QQFAV)

    override fun canRun(): Boolean {
        return GeneratedSettingList.getBoolean(key) &&
                HookEnv.isQQ() &&
                HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_85)
    }

    override fun onRun(app: Application, process: ActionProcess) {
        loadFromPlugin(PLUGIN_NAME, "com.qqfav.FavoriteService").findMethod {
            returnType = loadFromPlugin(PLUGIN_NAME, "com.qqfav.data.FavoriteData")
            paramTypes = arrayOf(long, boolean)
        }.hookAfter { param ->
            param.result!!.setObject("mSecurityBeat", 0)
        }
    }

    companion object {
        private const val PLUGIN_NAME = "qqfav.apk"
    }
}
