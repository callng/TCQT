package com.owo233.tcqt.hooks.helper

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.hooks.PttForward
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.MethodHookParam
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.isAbstract
import com.owo233.tcqt.utils.paramCount

@RegisterAction
class MenuBuilder : AlwaysRunAction() {

    private val decorators: Array<out OnMenuBuilder> = arrayOf(
        PttForward()
    )

    override fun onRun(ctx: Context, process: ActionProcess) {
        if (!PlatformTools.isNt()) {
            Log.e("The current host is not an NT architecture!")
            return
        }

        val activeDecorators = decorators
            .filterIsInstance<IAction>()
            .filter { it.canRun() }
            .filterIsInstance<OnMenuBuilder>()
            .takeIf { it.isNotEmpty() } ?: return

        val msgClass = load(
            "com.tencent.mobileqq.aio.msg.AIOMsgItem"
        ) ?: error("MenuBuilder Load AIOMsgItem Error")
        val baseCompClass = load(
                "com.tencent.mobileqq.aio.msglist.holder.component.BaseContentComponent"
            ) ?: error("MenuBuilder Load BaseContentComponent Error")

        val getMsgMethod = baseCompClass.declaredMethods.firstOrNull {
            it.returnType == msgClass && it.paramCount == 0
        }?.apply { isAccessible = true } ?: error("MenuBuilder: getMsgMethod not found")
        val listMethodName = baseCompClass.declaredMethods.firstOrNull {
            it.isAbstract && it.returnType == MutableList::class.java && it.paramCount == 0
        }?.name ?: error("MenuBuilder: listMethod not found")

        val decoratorMap = activeDecorators
            .flatMap { decorator -> decorator.targetComponentTypes.map { it to decorator } }
            .groupBy({ it.first }, { it.second })

        decoratorMap.keys.forEach { target ->
            load(target)?.declaredMethods
                ?.firstOrNull { it.name == listMethodName && it.paramCount == 0 }
                ?.hookMethod(afterHook(48) { param ->
                    val msg = getMsgMethod.invoke(param.thisObject) ?: return@afterHook
                    decoratorMap[target]?.forEach { decorator ->
                        runCatching {
                            decorator.onGetMenuNt(msg, target, param)
                        }.onFailure { e ->
                            Log.e("MenuBuilder error in ${decorator.javaClass.simpleName}", e)
                        }
                    }
                }) ?: error("MenuBuilder Load $target Error")
        }
    }
}

interface OnMenuBuilder {
    val targetComponentTypes: Array<String>
        get() = arrayOf(
            "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent", // 文本
            "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOUnsuportContentComponent", // 不支持的
            "com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent", // 语音
            "com.tencent.mobileqq.aio.msglist.holder.component.flashpic.AIOFlashPicContentComponent", // 闪照
            "com.tencent.mobileqq.aio.msglist.holder.component.video.AIOVideoContentComponent", // 视频
            "com.tencent.mobileqq.aio.msglist.holder.component.zplan.AIOZPlanContentComponent", // 超级QQ秀
            "com.tencent.mobileqq.aio.msglist.holder.component.videochat.AIOVideoResultContentComponent", // 视频通话
            "com.tencent.mobileqq.aio.msglist.holder.component.timestamp.AIOTimestampComponent", // 时间戳
            "com.tencent.mobileqq.aio.msglist.holder.component.template.AIOTemplateMsgComponent", // 模板
            "com.tencent.mobileqq.aio.msglist.holder.component.sysface.AIOSingleSysFaceContentComponent", // 系统表情
            "com.tencent.mobileqq.aio.msglist.holder.component.select.AIOSelectComponent", // 选择器
            "com.tencent.mobileqq.aio.msglist.holder.component.reply.AIOReplyComponent", // 回复消息
            "com.tencent.mobileqq.aio.msglist.holder.component.prologue.AIOPrologueContentComponent", // 引导消息
            "com.tencent.mobileqq.aio.msglist.holder.component.position.AIOPositionMsgComponent", // 位置消息
            "com.tencent.mobileqq.aio.msglist.holder.component.poke.AIOPokeContentComponent", // 戳一戳消息
            "com.tencent.mobileqq.aio.msglist.holder.component.pic.AIOPicContentComponent", // 图片消息
            "com.tencent.mobileqq.aio.msglist.holder.component.multipci.AIOMultiPicContentComponent", // 多图消息
            "com.tencent.mobileqq.aio.msglist.holder.component.multifoward.AIOMultifowardContentComponent", // 合并转发
            "com.tencent.mobileqq.aio.msglist.holder.component.msgtail.AIOGeneralMsgTailContentComponent", // 消息尾
            "com.tencent.mobileqq.aio.msglist.holder.component.msgstatus.AIOMsgStatusComponent", // 消息状态
            "com.tencent.mobileqq.aio.msglist.holder.component.msgfollow.AIOMsgFollowComponent", // 关注提醒
            "com.tencent.mobileqq.aio.msglist.holder.component.msgaction.AIOMsgRecommendComponent", // 推荐操作
            "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent", // 文图混合消息
            "com.tencent.mobileqq.aio.msglist.holder.component.mask.AIOContentMaskComponent", // 内容遮罩
            "com.tencent.mobileqq.aio.msglist.holder.component.marketface.AIOMarketFaceComponent", // 商品表情
            "com.tencent.mobileqq.aio.msglist.holder.component.markdown.AIORichContentComponent", // Markdown
            "com.tencent.mobileqq.aio.msglist.holder.component.longmsg.AIOLongMsgContentComponent", // 长消息
            "com.tencent.mobileqq.aio.msglist.holder.component.LocationShare.AIOLocationShareComponent", // 位置分享
            "com.tencent.mobileqq.aio.msglist.holder.component.leftswipearea.AIOLeftSwipeAreaComponent", // 左滑区域
            "com.tencent.mobileqq.aio.msglist.holder.component.ickbreak.AIOIceBreakContentComponent", // 开场白消息
            "com.tencent.mobileqq.aio.msglist.holder.component.graptips.revoke.RevokeGrayTipsComponent", // 撤回提示
            "com.tencent.mobileqq.aio.msglist.holder.component.graptips.common.CommonGrayTipsComponent", // 通用提示
            "com.tencent.mobileqq.aio.msglist.holder.component.fold.AIOFoldContentComponent", // 折叠消息
            "com.tencent.mobileqq.aio.msglist.holder.component.flashpic.AIOFlashPicContentComponent", // 闪照
            "com.tencent.mobileqq.aio.msglist.holder.component.filtervideo.AIOLiveVideoContentComponent", // 滤镜视频
            "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOFileContentComponent", // 文件
            "com.tencent.mobileqq.aio.msglist.holder.component.file.AIOOnlineFileContentComponent", // 在线文件
            "com.tencent.mobileqq.aio.msglist.holder.component.facebubble.AIOFaceBubbleContentComponent", // 表情气泡
            "com.tencent.mobileqq.aio.msglist.holder.component.chain.ChainAniStickerContentComponent", // 链式动画表情
            "com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent", // 头像
            "com.tencent.mobileqq.aio.msglist.holder.component.ark.AIOArkContentComponent", // ark卡片消息
            "com.tencent.mobileqq.aio.msglist.holder.component.ark.AIOCenterArkContentComponent", // ark中间卡片消息
            "com.tencent.mobileqq.aio.msglist.holder.component.anisticker.AIOAniStickerContentComponent", // 动画表情
            "com.tencent.mobileqq.aio.qwallet.AIOQWalletComponent", // 红包转账消息
            "com.tencent.mobileqq.aio.shop.AIOShopArkContentComponent", // 商城ark消息
        )

    fun onGetMenuNt(
        msg: Any,
        componentType: String,
        param: MethodHookParam
    )
}
