package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.func.ModuleCommand
import com.owo233.tcqt.hooks.helper.ExtraMenuItem
import com.owo233.tcqt.hooks.helper.PlusMenuManager

@RegisterAction
class AddPlusMenu : IAction {

    override val key: String get() = "add_plus_menu"
    override val name: String get() = "添加额外选项"
    override val desc: String get() = "给主页右上角菜单添加额外功能选项(结束/重启进程)。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        PlusMenuManager.registerAll(
            ExtraMenuItem(
                id = 23331,
                title = "结束进程",
                iconResId = com.owo233.tcqt.R.drawable.ic_item_exit_72dp,
                onClick = { ModuleCommand.sendCommand(app, "exit") }
            ),
            ExtraMenuItem(
                id = 23332,
                title = "重启进程",
                iconResId = com.owo233.tcqt.R.drawable.ic_item_reboot_72dp,
                onClick = { ModuleCommand.sendCommand(app, "restart") }
            )
        )
    }
}
