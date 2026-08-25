package com.owo233.tcqt.hooks.func.misc

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Canvas
import android.widget.EditText
import android.widget.TextView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class FilterSpecialCharacters : IAction {

    override val key: String get() = "filter_special_characters"
    override val name: String get() = "过滤聊天消息特殊字符"
    override val desc: String get() = "将聊天消息中出现的特殊字符替换为空格。"
    override val uiTab: String get() = "杂项"

    override fun onRun(app: Application, process: ActionProcess) {
        TextView::class.java.findMethod {
            name = "onDraw"
            paramTypes(Canvas::class.java)
        }.hookBefore { param ->
            if (param.thisObject !is TextView) return@hookBefore
            if (param.thisObject is EditText) return@hookBefore

            val str = (param.thisObject as TextView).text.toString()
            if (BLACKLIST.none { ch -> str.contains(ch) }) return@hookBefore
            (param.thisObject as TextView).text = filterControlCharacter(str)
        }
    }

    private fun filterControlCharacter(str: CharSequence): CharSequence {
        var ret = str.toString()
        BLACKLIST.forEach { ret = ret.replace(it, ' ') }
        return ret
    }

    private companion object {

        @SuppressLint("BidiSpoofing")
        private const val BLACKLIST = "‭‮‪‫‎⁦⁧‏"
    }
}
