package com.owo233.tcqt.features.script

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.InfraTask
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.log.LogUtils
import com.owo233.tcqt.host.QQInterfaces

/**
 * 聊天界面入口：进入会话时挂脚本悬浮菜单，并触发脚本的 `chatInterface` 回调。
 *
 * 宿主入口是真机核对过的 `com.tencent.qqnt.aio.activity.AIODelegate`：
 * ```
 * show()Landroid/view/View;   // 进入 / 切换会话
 * hide()V                     // 离开会话
 * ```
 *
 * 常驻任务（[InfraTask]）：菜单必须随时能挂上，不能挂在带开关的功能后面。
 */
@RegisterAction
object ScriptAioEntry : InfraTask(
    key = "script_aio_entry",
    priority = ActionPriority.DEFERRED,
) {

    private const val DELEGATE_CLASS = "com.tencent.qqnt.aio.activity.AIODelegate"

    @Volatile
    private var menuView: ScriptMenuView? = null

    /** 当前会话；脚本菜单回调与 `chatInterface` 都用它。 */
    @Volatile
    internal var currentContact: ScriptChatContext = ScriptChatContext()
        private set

    override fun install() {
        runCatching {
            val clazz = HookEnv.hostClassLoader.loadClass(DELEGATE_CLASS)

            clazz.hookMethodAfter("show") { param ->
                runCatching { onAioShow(param.thisObject) }
                    .onFailure { LogUtils.androidNoFilter.w("脚本引擎: 进入会话处理失败", it) }
            }

            clazz.hookMethodAfter("hide") {
                runCatching { onAioHide() }
                    .onFailure { LogUtils.androidNoFilter.w("脚本引擎: 离开会话处理失败", it) }
            }

            LogUtils.androidNoFilter.i("脚本引擎: 已挂载 $DELEGATE_CLASS#show/hide")
        }.onFailure {
            LogUtils.androidNoFilter.w("脚本引擎: 挂载 AIO 入口失败", it)
        }
    }

    private fun onAioShow(delegate: Any) {
        val contact = ScriptAioContactParser.fromDelegate(delegate, fallbackUin = fallbackUin())
        if (!contact.isValid) return

        currentContact = contact

        // 会话切换时先撤掉旧球，避免残留在上一个会话的位置上
        dismissMenu()

        // chatInterface：所有运行中脚本都会收到一次
        ScriptEvents.onChatInterface(contact)

        // 只有真的注册了 addItem 才挂球
        if (!ScriptMenus.hasAnyItem()) return

        val activity = QQInterfaces.topActivity
        if (activity == null) {
            LogUtils.androidNoFilter.w("脚本悬浮菜单: 顶层 Activity 不可用，本次不挂载")
            return
        }

        ScriptMenuView(activity).also {
            menuView = it
            it.show()
        }
    }

    private fun onAioHide() {
        currentContact = ScriptChatContext()
        dismissMenu()
    }

    private fun dismissMenu() {
        menuView?.dismiss()
        menuView = null
    }

    /** 群聊拿不到 UIN 时的兜底：从宿主 Activity 的 Intent 里取。 */
    private fun fallbackUin(): String =
        runCatching { QQInterfaces.topActivity?.intent?.getStringExtra("key_peerUin").orEmpty() }
            .getOrDefault("")
}
