package com.owo233.tcqt.features.chat

import android.view.ViewGroup
import android.widget.TextView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.isPrivate
import com.owo233.tcqt.core.hook.isPublic
import com.owo233.tcqt.core.hook.paramCount
import com.owo233.tcqt.core.reflect.callMethod

@RegisterAction
object HideGrayTipText : Feature(
    key = "hide_gray_tip_text",
    name = "隐藏灰色提示文本",
    desc = "隐藏聊天界面上的灰色提示文本。",
) {

    private val saveConfig by stringOption(
        settingKey = "string.saveConfig",
        name = "保存的配置",
        placeholder = "即将彻底消失\n加入了群聊\n我也要打卡\n一起来玩吧\n... 一行一个关键字",
    )

    private val configList by lazy { saveConfig.lines() }

    override fun install() {
        val grayTipClass =
            loadOrThrow("com.tencent.mobileqq.aio.msglist.holder.component.graptips.common.CommonGrayTipsComponent")
        val initMethod = grayTipClass.declaredMethods.single { method ->
            method.isPublic && method.returnType == Void.TYPE && method.paramCount == 3
                    && method.parameterTypes[0] == Int::class.javaPrimitiveType
                    && method.parameterTypes[2] == List::class.java
        }
        val textViewMethod = grayTipClass.declaredMethods.single { method ->
            method.isPrivate && method.returnType == loadOrThrow("com.tencent.qqnt.aio.widget.AIOMsgTextView")
        }

        initMethod.hookAfter { param ->
            val textView = requireNotNull(param.thisObject).callMethod(textViewMethod.name) as TextView
            val container = textView.parent.parent as ViewGroup
            val shouldHide = configList.any { textView.text.toString().contains(it) }
            if (shouldHide) container.layoutParams = ViewGroup.LayoutParams(0, 0)
        }
    }
}
