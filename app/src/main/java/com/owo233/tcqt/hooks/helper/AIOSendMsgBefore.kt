package com.owo233.tcqt.hooks.helper

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.func.basic.RenameBaseApk
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.MsgElement

@RegisterAction
class AIOSendMsgBefore : AlwaysRunAction() {

    private val decorators: Array<out OnAIOSendMsgBefore> = arrayOf(
        RenameBaseApk()
    )

    @Suppress("UNCHECKED_CAST")
    override fun onRun(app: Application, process: ActionProcess) {
        val activeDecorators = decorators
            .filterIsInstance<IAction>()
            .filter { it.canRun() }
            .filterIsInstance<OnAIOSendMsgBefore>()
            .takeIf { it.isNotEmpty() } ?: return

        IKernelMsgService.CppProxy::class.java.findMethod {
            name = "sendMsg"
        }.hookBefore { param ->
            val elements = param.args[2] as ArrayList<MsgElement>
            activeDecorators.forEach {
                it.onSend(elements)
            }
        }
    }

    override fun canRun(): Boolean {
        return PlatformTools.isNt()
    }
}

fun interface OnAIOSendMsgBefore {
    fun onSend(elements: ArrayList<MsgElement>)
}
