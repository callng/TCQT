// hook 代码来自 https://github.com/cinit/QAuxiliary

package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.IntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact

@RegisterAction
class FakePicSize : IAction {

    override val name: String get() = "篡改图片显示大小"
    override val desc: String get() = "将发送的消息图片以指定的比例显示。"
    override val uiTab: String get() = "界面"
    override val settings: List<Setting<*>>
        get() = listOf(
            IntSetting(
                "fake_pic_size.type",
                "图片比例",
                1,
                "",
                listOf("默认", "最小", "略小", "略大", "最大")
            ),
        )

    override fun onRun(app: Application, process: ActionProcess) {
        val type = TCQTSetting.getInt("fake_pic_size.type")
        val targetSize = type.toTargetSize() ?: return

        hookSendMsg(targetSize)
    }

    override val key: String get() = "fake_pic_size"

    private fun hookSendMsg(targetSize: Int) {
        IKernelMsgService.CppProxy::class.java.findMethod {
            name = "sendMsg"
            paramCount = 5
        }.hookBefore { param ->
            val contact = param.args[1] as Contact
            val elements = param.args[2] as Iterable<*>

            elements
                .filterIsInstance<MsgElement>()
                .forEach { it.adjustPicSize(contact, targetSize) }
        }
    }

    private fun MsgElement.adjustPicSize(contact: Contact, targetSize: Int) {
        val pic = picElement ?: return

        if (contact.chatType != 4) {
            pic.picSubType = 0
        }

        if (targetSize == 1) {
            pic.picWidth = targetSize
            pic.picHeight = targetSize
            return
        }

        val oldW = pic.picWidth.takeIf { it > 0 } ?: return
        val oldH = pic.picHeight.takeIf { it > 0 } ?: return

        val ratio = oldW.toDouble() / oldH.toDouble()

        if (oldW > oldH) {
            pic.picWidth = targetSize
            pic.picHeight = (targetSize / ratio).toInt()
        } else {
            pic.picWidth = (targetSize * ratio).toInt()
            pic.picHeight = targetSize
        }
    }

    private fun Int.toTargetSize(): Int? = when (this) {
        0, 1 -> null
        2 -> 1
        3 -> 64
        4 -> 512
        5 -> 1024
        else -> null
    }
}
