/**
 * 功能来自 https://github.com/callng/QAuxiliary
 * 代码提供者： HdShare
 */
package com.owo233.tcqt.features.chat

import android.widget.TextView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.reflect.FieldUtils
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.invokeMethod

@RegisterAction
object ChangePreviewTextSize : Feature(
    key = "change_preview_text_size",
    name = "修改预览字体大小",
    desc = "修改双击启动预览界面的文本字体大小, 缩小方便预览和复制。",
) {

    private val textSize by stringOption(
        settingKey = "string.textSize",
        name = "字体大小",
        placeholder = "默认 14，留空恢复默认",
        desc = "默认大小 14\n配置为空或无效值则使用默认大小\n",
    )

    /** 配置为空或无效值时用 14f。 */
    val configTextSize: Float
        get() = textSize.trim().toFloatOrNull()?.takeIf { it > 0f } ?: 14f

    override fun install() {
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
}
