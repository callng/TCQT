/**
 * 功能来自 https://github.com/callng/QAuxiliary
 * 代码提供者： HdShare
 */
package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.widget.TextView
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.StringSetting
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.invokeMethod

@RegisterAction
class ChangePreviewTextSize : IAction {

    override val name: String get() = "修改预览字体大小"
    override val desc: String get() = "修改双击启动预览界面的文本字体大小, 缩小方便预览和复制。"
    override val uiTab: String get() = "界面"
    override val settings: List<Setting<*>>
        get() = listOf(
            StringSetting(
                "change_preview_text_size.string.textSize",
                "textSize",
                "",
                "",
                "默认大小 14\n配置为空或无效值则使用默认大小\n",
                false
            ),
        )

    override val key: String
        get() = "change_preview_text_size"

    override fun onRun(app: Application, process: ActionProcess) {
        val containerViewClass =
            "com.tencent.qqnt.textpreview.PreviewTextContainerView".toHostClass()

        "com.tencent.mobileqq.activity.TextPreviewActivity".toHostClass().findMethod {
            name = "onCreate"
            paramTypes = arrayOf(bundle)
        }.hookAfter { param ->
            val containerView = FieldUtils.create(param.thisObject)
                .typed(containerViewClass)
                .getValue()
                ?: error("修改预览字体大小: 未找到 PreviewTextContainerView 字段")

            val textView = containerView.invokeMethod {
                returnType == TextView::class.java && parameterCount == 0
            } as TextView

            textView.textSize = configTextSize
        }
    }

    companion object {

        val configTextSize: Float by lazy {
            TCQTSetting
                .getString("change_preview_text_size.string.textSize")
                .trim()
                .toFloatOrNull()
                ?.takeIf { it > 0f }
                ?: 14f
        }
    }
}
