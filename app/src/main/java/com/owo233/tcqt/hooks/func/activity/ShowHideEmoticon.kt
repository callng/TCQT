package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.hook.isNotAbstract
import com.tencent.qqnt.kernel.nativeinterface.CommonTabEmojiInfo
import com.tencent.qqnt.kernel.nativeinterface.SysEmoji

@RegisterAction
class ShowHideEmoticon : IAction {

    override val name: String get() = "显示隐藏表情"
    override val desc: String get() = "一些表情只会在特定时间内可见，启用后，这些隐藏表情将显示到表情列表中。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        load("com.tencent.mobileqq.emoticon.QQSysAndEmojiResInfo")
            ?.declaredMethods
            ?.filter { m -> m.returnType == Boolean::class.java && m.isNotAbstract }
            ?.onEach { it.hookAfter { p -> p.result = false } }

        SysEmoji::class.java.hookMethodBefore({
            name = "getIsHide"
        }) {
            val emoji = it.thisObject as SysEmoji
            emoji.isHide = false
        }

        CommonTabEmojiInfo::class.java.hookMethodBefore({
            name = "getIsHide"
        }) {
            val emoji = it.thisObject as CommonTabEmojiInfo
            emoji.isHide = false
        }
    }

    override val key: String get() = "show_hide_emoticon"
}
