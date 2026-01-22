package com.owo233.tcqt.features.hooks.func.activity

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.base.loadOrThrow
import com.owo233.tcqt.foundation.utils.callMethodAs
import com.owo233.tcqt.foundation.utils.hookAfterMethod
import com.owo233.tcqt.foundation.utils.isPrivate
import com.owo233.tcqt.foundation.utils.isPublic
import com.owo233.tcqt.foundation.utils.paramCount

@RegisterAction
@RegisterSetting(
    key = "hide_gray_tip_text",
    name = "隐藏灰色提示文本",
    type = SettingType.BOOLEAN,
    desc = "隐藏聊天界面上的灰色提示文本。",
    hasTextAreas = true,
    uiTab = "界面"
)
@RegisterSetting(
    key = "hide_gray_tip_text.string.saveConfig",
    name = "保存的配置",
    type = SettingType.STRING,
    textAreaPlaceholder = "即将彻底消失\n加入了群聊\n我也要打卡\n一起来玩吧\n... 一行一个关键字"
)
class HideGrayTipText : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val grayTipClass = loadOrThrow("com.tencent.mobileqq.aio.msglist.holder.component.graptips.common.CommonGrayTipsComponent")
        val initMethod = grayTipClass.declaredMethods.single { method ->
            method.isPublic && method.returnType == Void.TYPE && method.paramCount == 3
                    && method.parameterTypes[0] == Int::class.javaPrimitiveType
                    && method.parameterTypes[2] == List::class.java
        }
        val textViewMethod = grayTipClass.declaredMethods.single { method ->
            method.isPrivate && method.returnType == loadOrThrow("com.tencent.qqnt.aio.widget.AIOMsgTextView")
        }

        initMethod.hookAfterMethod { param ->
            val textView = param.thisObject.callMethodAs<TextView>(textViewMethod.name)
            val container = textView.parent.parent as ViewGroup
            val shouldHide = configList.any { textView.text.toString().contains(it) }
            if (shouldHide) container.layoutParams = ViewGroup.LayoutParams(0, 0)
        }
    }

    companion object {

        private val configList by lazy {
            GeneratedSettingList.getString(
                GeneratedSettingList.HIDE_GRAY_TIP_TEXT_STRING_SAVECONFIG
            ).lines()
        }
    }

    override val key: String get() = GeneratedSettingList.HIDE_GRAY_TIP_TEXT
}
