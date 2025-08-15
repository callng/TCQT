package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.isPublic
import com.tencent.mobileqq.aio.msglist.AIOMsgItemFactoryProvider
import de.robv.android.xposed.XposedBridge

@RegisterAction
class DisableFlashPic: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        AIOMsgItemFactoryProvider::class.java.declaredMethods.first {
            it.isPublic && it.returnType != Void.TYPE
                    && it.parameterCount == 1 && it.parameterTypes[0] == Integer.TYPE
        }.hookMethod(afterHook {
            val id = it.args[0] as Int
            if (id == 84) {
                it.result = XposedBridge.invokeOriginalMethod(it.method, it.thisObject, arrayOf(5))
            } else if (id == 85) {
                it.result = XposedBridge.invokeOriginalMethod(it.method, it.thisObject, arrayOf(4))
            }
        })
    }

    override val name: String get() = "将闪照视为正常图片"

    override val key: String get() = TCQTSetting.DISABLE_FLASH_PIC

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
