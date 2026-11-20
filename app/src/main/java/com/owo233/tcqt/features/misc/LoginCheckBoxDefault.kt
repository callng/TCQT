package com.owo233.tcqt.features.misc

import android.content.Context
import android.widget.CheckBox
import com.owo233.tcqt.annotations.ActionPriority
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.reflect.allConstructors

// 只作用于登录页的 CheckBox。r755 在 CRITICAL 里花 161ms，r756 挪到 EARLY 后反而涨到
// 280~422ms，且仍挡在首帧之前；r757 再降一级，首帧提交后才安装。
// 回退方式：把 priority 改回 EARLY 即可。若发现冷启动直达登录页时复选框没被勾上，
// 说明首帧就是登录页、DEFERRED 晚于 CheckBox 构造，此时应回退到 EARLY。
@RegisterAction(processes = [ActionProcess.MAIN], priority = ActionPriority.DEFERRED)
object LoginCheckBoxDefault : Feature(
    key = "login_check_box_default",
    name = "默认勾选登录协议",
    desc = "登录界面自动勾选复选框（用户协议，有人看了吗）。",
) {


    override fun install() {
        CheckBox::class.java.allConstructors().forEach {
            it.hookAfter { param ->
                val context = param.args.getOrNull(0) as? Context ?: return@hookAfter
                val className = context.javaClass.name

                if (!loginContextNames.contains(className)) return@hookAfter

                val checkBox = param.thisObject as CheckBox

                if (!checkBox.isChecked) {
                    checkBox.post { checkBox.isChecked = true }
                }
            }
        }
    }


    private val loginContextNames = setOf(
        "com.tencent.mobileqq.activity.LoginActivity",
        "com.tencent.mobileqq.activity.LoginPublicFragmentActivity"
    )
}
