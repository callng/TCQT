// hook 代码来自 https://github.com/cinit/QAuxiliary

package com.owo233.tcqt.features.hooks.func.activity

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.foundation.utils.hookBeforeMethod
import com.owo233.tcqt.foundation.utils.reflect.MethodUtils
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact

@RegisterAction
@RegisterSetting(
    key = "fake_pic_size",
    name = "篡改图片显示大小",
    type = SettingType.BOOLEAN,
    desc = "将发送的消息图片以指定的比例显示。",
    uiTab = "界面"
)
@RegisterSetting(
    key = "fake_pic_size.type",
    name = "图片比例",
    type = SettingType.INT,
    defaultValue = "1",
    options = "默认|最小|略小|略大|最大",
)
class FakePicSize : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val type = GeneratedSettingList.getInt(GeneratedSettingList.FAKE_PIC_SIZE_TYPE)
        val targetSize = type.toTargetSize() ?: return

        hookSendMsg(targetSize)
    }

    override val key: String get() = GeneratedSettingList.FAKE_PIC_SIZE

    private fun hookSendMsg(targetSize: Int) {
        MethodUtils.create(IKernelMsgService.CppProxy::class.java)
            .named("sendMsg")
            .paramCount(5)
            .findOrThrow()
            .hookBeforeMethod { param ->
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
