/**
 * 功能来自 https://github.com/callng/QAuxiliary
 * 代码提供者： HdShare
 */
package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import android.widget.TextView
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.invokeMethod

@RegisterAction
@RegisterSetting(
    key = "change_preview_text_size",
    name = "修改预览字体大小",
    type = SettingType.BOOLEAN,
    desc = "修改双击启动预览界面的文本字体大小, 缩小方便预览和复制。",
    uiTab = "界面"
)
@RegisterSetting(
    key = "change_preview_text_size.string.textSize",
    name = "textSize",
    type = SettingType.STRING,
    textAreaPlaceholder = "默认大小 14\n配置为空或无效值则使用默认大小\n"
)
class ChangePreviewTextSize : IAction {

    override val key: String
        get() = GeneratedSettingList.CHANGE_PREVIEW_TEXT_SIZE

    override fun onRun(ctx: Context, process: ActionProcess) {
        val containerViewClass = "com.tencent.qqnt.textpreview.PreviewTextContainerView".toHostClass()

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
            GeneratedSettingList
                .getString(GeneratedSettingList.CHANGE_PREVIEW_TEXT_SIZE_STRING_TEXTSIZE)
                .trim()
                .toFloatOrNull()
                ?.takeIf { it > 0f }
                ?: 14f
        }
    }
}
