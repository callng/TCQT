package com.owo233.tcqt.hooks.func

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.utils.log.Log
import mqq.app.MobileQQ

@RegisterAction
class AccountChange : AlwaysRunAction() {

    override val processes: Set<ActionProcess> = setOf(ActionProcess.MSF)

    private val accountReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (getActions().contains(action)) {
                handleAction(action, intent)
            }
        }
    }

    override fun onRun(ctx: Context, process: ActionProcess) {
        val intentFilter = IntentFilter().apply {
            getActions().forEach(::addAction)
        }

        MobileQQ.getMobileQQ().registerReceiver(accountReceiver, intentFilter)
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
