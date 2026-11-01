package com.owo233.tcqt.features.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.InfraTask
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ReceiverRegistry

@RegisterAction(processes = [ActionProcess.MSF])
object AccountChange : InfraTask(
    key = "AccountChange",
) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (getActions().contains(action)) {
                handleAction(action, intent)
            }
        }
    }

    override fun install() {
        val filter = IntentFilter().apply {
            getActions().forEach(::addAction)
        }
        ReceiverRegistry.register(hostApp, receiver, filter)
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

    private const val ACTION_ACCOUNT_CHANGED = "mqq.intent.action.ACCOUNT_CHANGED"
    private const val ACTION_LOGOUT = "mqq.intent.action.LOGOUT"
}
