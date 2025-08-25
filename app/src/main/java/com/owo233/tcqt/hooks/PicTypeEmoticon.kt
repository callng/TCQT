package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.internals.setting.TCQTSetting

@RegisterAction
class PicTypeEmoticon: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.qqnt.aio.adapter.api.impl.RichMediaBrowserApiImpl")!!
            .hookMethod("checkIsFavPicAndShowPreview", beforeHook {
                it.result = false
            })
    }

    override val name: String get() = "以图片方式打开表情"

    override val key: String get() = TCQTSetting.PIC_TYPE_EMOTICON
}
