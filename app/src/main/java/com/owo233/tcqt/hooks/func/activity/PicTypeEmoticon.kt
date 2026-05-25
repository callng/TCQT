package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.PicElement

@RegisterAction
class PicTypeEmoticon : IAction {

    override val name: String get() = "以图片方式打开表情"
    override val desc: String get() = "可以保存一些不让保存的表情。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        load("com.tencent.qqnt.aio.adapter.api.impl.RichMediaBrowserApiImpl")!!
            .hookMethodBefore({
                name = "checkIsFavPicAndShowPreview"
                paramTypes = arrayOf(AIOMsgItem::class.java, PicElement::class.java, view, list)
            }) {
                it.result = false
            }
    }

    override val key: String get() = "pic_type_emoticon"
}
