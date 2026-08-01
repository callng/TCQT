package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.doNothing
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class BlockChainAniSticker : IAction {

    override val key: String get() = "block_chain_ani_sticker"
    override val name: String get() = "屏蔽全屏动画彩蛋"
    override val desc: String get() = "屏蔽发送或接收超级表情时触发的全屏连锁动画播放。"
    override val uiTab: String get() = "界面"

    override fun onInit(): Boolean {
        return HookEnv.requireMinQQVersion(QQVersion.QQ_9_0_20)
    }

    override fun onRun(app: Application, process: ActionProcess) {
        "com.tencent.mobileqq.aio.animation.api.impl.AioAnimationApiImpl".toClass.findMethod {
            name = "handleNewMsg"
        }.doNothing()
    }
}
