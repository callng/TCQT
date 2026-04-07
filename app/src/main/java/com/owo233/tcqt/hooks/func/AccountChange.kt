package com.owo233.tcqt.hooks.func

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.utils.log.Log

@RegisterAction
class AccountChange : AlwaysRunAction() {

    override val processes: Set<ActionProcess> = setOf(ActionProcess.MSF)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (getActions().contains(action)) {
                handleAction(action, intent)
            }
        }
    }

    override fun onRun(app: Application, process: ActionProcess) {
        val filter = IntentFilter().apply {
            getActions().forEach(::addAction)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(receiver, filter)
        }
    }

    private fun handleAction(action: String, intent: Intent) {
        when (action) {
            ACTION_LOGOUT -> Log.d("AccountChange -> 账号已退出登录")
            ACTION_ACCOUNT_CHANGED -> {
                val account = intent.getStringExtra("account")
                Log.d("AccountChange -> 登录/切换账号: $account")
            }
        }
    }

    private fun getActions(): List<String> = listOf(ACTION_ACCOUNT_CHANGED, ACTION_LOGOUT)

    companion object {
        private const val ACTION_ACCOUNT_CHANGED = "mqq.intent.action.ACCOUNT_CHANGED"
        private const val ACTION_LOGOUT = "mqq.intent.action.LOGOUT"
    }
}
