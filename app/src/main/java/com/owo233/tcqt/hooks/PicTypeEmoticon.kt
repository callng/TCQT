package com.owo233.tcqt.hooks

import android.content.Context
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.PicElement

@RegisterAction
@RegisterSetting(
    key = "pic_type_emoticon",
    name = "以图片方式打开表情",
    type = SettingType.BOOLEAN,
    desc = "可以保存一些不让保存的表情。",
    uiOrder = 18
)
class PicTypeEmoticon : IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.qqnt.aio.adapter.api.impl.RichMediaBrowserApiImpl")!!
            .hookBeforeMethod(
                "checkIsFavPicAndShowPreview",
                AIOMsgItem::class.java,
                PicElement::class.java,
                View::class.java,
                List::class.java
            ) {
                it.result = false
            }
    }

    override val key: String get() = GeneratedSettingList.PIC_TYPE_EMOTICON
}
