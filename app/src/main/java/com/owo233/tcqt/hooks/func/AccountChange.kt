package com.owo233.tcqt.hooks.func

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.log.Log

@RegisterAction
class AccountChange : IAction, BroadcastReceiver() {

    override fun onRun(ctx: Context, process: ActionProcess) {
        val intentFilter = IntentFilter().apply {
            getActions().forEach(::addAction)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            HookEnv.getTargetSdkVersion() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            ctx.registerReceiver(this, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(this, intentFilter)
        }
    }

    override fun canRun(): Boolean {
        return true
    }

    override val key: String
        get() = "AccountChange"

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MSF)

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: run {
            Log.e("AccountReceiver onReceive null action broadcast")
            return
        }

        // Log.d("AccountReceiver onReceive action = $action")

        getActions()
            .firstOrNull { it == action }
            ?.let { handleAction(it, intent) }
    }

    private fun handleAction(action: String, intent: Intent) {
        when (action) {
            ACTION_LOGOUT -> {
                Log.d("AccountReceiver onReceive -> 账号已退出登录")
            }
            ACTION_ACCOUNT_CHANGED -> {
                // 这里的登录行为不包括自动登录(比如: QQ冷启动上线账号行为)，而是主动登录或切换账号
                val account = intent.getStringExtra("account")
                Log.d("AccountReceiver onReceive -> 登录(切换)账号 = $account")
            }
        }
    }

    private fun getActions(): List<String> =
        listOf(ACTION_ACCOUNT_CHANGED, ACTION_LOGOUT)

    companion object {
        private const val ACTION_ACCOUNT_CHANGED = "mqq.intent.action.ACCOUNT_CHANGED"
        private const val ACTION_LOGOUT = "mqq.intent.action.LOGOUT"
    }
}
