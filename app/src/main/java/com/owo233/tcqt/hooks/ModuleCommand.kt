package com.owo233.tcqt.hooks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.MMKVUtils
import mqq.app.MobileQQ

@RegisterAction
class ModuleCommand : AlwaysRunAction() {

    private var registeredReceiver: BroadcastReceiver? = null

    companion object {
        private const val ACTION_MODULE_COMMAND = "com.owo233.tcqt.MODULE_COMMAND"

        fun sendCommand(ctx: Context, command: String) {
            Intent(ACTION_MODULE_COMMAND).apply {
                putExtra("cmd", command)
                setPackage(ctx.packageName)
            }.also {
                ctx.sendBroadcast(it)
            }
        }
    }

    private fun restartApp(ctx: Context) {
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val launchIntent: Intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?: return

        val pendingIntent = PendingIntent.getActivity(
            ctx,
            0,
            launchIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1L,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1L,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1L,
                pendingIntent
            )
        }
    }

    override fun onRun(ctx: Context, process: ActionProcess) {
        val filter = IntentFilter(ACTION_MODULE_COMMAND)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val cmd = intent.getStringExtra("cmd") ?: return
                when (cmd) {
                    "exitApp" -> {
                        restartApp(ctx)
                        MobileQQ.getMobileQQ().otherProcessExit(false)
                        MobileQQ.getMobileQQ().qqProcessExit(true)
                    }
                    "config_clear" -> MMKVUtils.mmkvWithId("TCQT").also { it.clearAll() }
                }
            }
        }

        runCatching {
            val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.RECEIVER_NOT_EXPORTED
            } else {
                ContextCompat.RECEIVER_EXPORTED
            }
            ctx.registerReceiver(receiver, filter, flag)
            registeredReceiver = receiver
        }.onFailure {
            Log.e("registerReceiver error", it)
        }
    }

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
