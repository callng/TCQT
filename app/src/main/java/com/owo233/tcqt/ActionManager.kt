package com.owo233.tcqt

import android.content.Context
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.BrowserRestrictMitigation
import com.owo233.tcqt.hooks.FakeMultiWindowStatus
import com.owo233.tcqt.hooks.FetchService
import com.owo233.tcqt.hooks.LoginCheckBoxDefault
import com.owo233.tcqt.hooks.ModuleUpdate
import com.owo233.tcqt.hooks.OneClickLikes
import com.owo233.tcqt.hooks.PokeNoCoolDown
import com.owo233.tcqt.hooks.RemoveQRLoginCheck
import com.owo233.tcqt.hooks.SkipQRLoginWait

object ActionManager {

    private val FIRST_ACTION = arrayOf(
        FetchService::class.java, // 防止群和好友消息撤回
        BrowserRestrictMitigation::class.java, // 移除内置浏览器访问限制
        LoginCheckBoxDefault::class.java, // 自动勾选登录页用户协议复选框
        RemoveQRLoginCheck::class.java, // 移除长按或相册扫码登录限制
        FakeMultiWindowStatus::class.java, // 伪装非多窗口模式
        SkipQRLoginWait::class.java, // 跳过扫码登录等待
        OneClickLikes::class.java, // 一键点赞
        ModuleUpdate::class.java, // 模块更新干掉宿主
        PokeNoCoolDown::class.java, // 禁用戳一戳10秒冷却
    )

    private val instanceMap = hashMapOf<Class<*>, IAction>()

    private fun instanceOf(cls: Class<*>): IAction = instanceMap.getOrPut(cls) {
        cls.getConstructor().newInstance() as IAction
    }

    fun runFirst(ctx: Context, proc: ActionProcess) {
        val baseProcs = setOf(ActionProcess.MSF, ActionProcess.MAIN, ActionProcess.TOOL)

        FIRST_ACTION.forEach {
            val action = instanceOf(it)

            val shouldRun = ActionProcess.ALL in action.processes
                    || proc in action.processes
                    || (ActionProcess.OTHER in action.processes && proc !in baseProcs)

            if (shouldRun) {
                action(ctx)
            }
        }
    }
}
