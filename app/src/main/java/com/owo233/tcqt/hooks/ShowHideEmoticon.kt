package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.isNotAbstract
import com.tencent.qqnt.kernel.nativeinterface.CommonTabEmojiInfo
import com.tencent.qqnt.kernel.nativeinterface.SysEmoji

@RegisterAction
@RegisterSetting(key = "show_hide_emoticon", name = "显示被隐藏的表情", type = SettingType.BOOLEAN, defaultValue = "false")
class ShowHideEmoticon: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.mobileqq.emoticon.QQSysAndEmojiResInfo")
            ?.declaredMethods
            ?.filter { m -> m.returnType == Boolean::class.java && m.isNotAbstract }
            ?.onEach { it.hookMethod(beforeHook { p -> p.result = false }) }

        SysEmoji::class.java.hookMethod("getIsHide", beforeHook {
            val emoji = it.thisObject as SysEmoji
            emoji.isHide = false
        })

        CommonTabEmojiInfo::class.java.hookMethod("getIsHide", beforeHook {
            val emoji = it.thisObject as CommonTabEmojiInfo
            emoji.isHide = false
        })
    }

    override val key: String get() = GeneratedSettingList.SHOW_HIDE_EMOTICON
}
