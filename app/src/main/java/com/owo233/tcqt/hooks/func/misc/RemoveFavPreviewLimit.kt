package com.owo233.tcqt.hooks.func.misc

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.loadFromPlugin
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.setObject

@RegisterAction
class RemoveFavPreviewLimit : IAction {

    override val name: String get() = "移除收藏预览限制"
    override val desc: String get() = "移除收藏预览限制，它是这样说的。"
    override val uiTab: String get() = "杂项"
    override val key: String get() = "remove_fav_preview_limit"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.QQFAV)

    override fun onInit(): Boolean {
        return HookEnv.isQQ() && HookEnv.requireMinQQVersion(QQVersion.QQ_8_9_85)
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
