package com.owo233.tcqt.hooks.func.test

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hex2ByteArray
import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.SyncUtils
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findField
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.mobileqq.msf.core.c0.a as QSign
import com.tencent.mobileqq.msf.core.d0.a as TSign
import com.tencent.mobileqq.msf.service.MsfService
import com.tencent.qphone.base.remote.ToServiceMsg
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class GetSign : IAction, DexKitTask, InputRootInitCallback {

    override val key: String
        get() = "get_sign"

    override val name: String
        get() = "获取测试签名"

    override val desc: String
        get() = "本功能仅用于调试，正常情况下无需启用。"

    override val uiTab: String
        get() = "调试"

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)

    override fun onRun(app: Application, process: ActionProcess) {
        when (process) {
            ActionProcess.MAIN -> initMainProcess(app)
            ActionProcess.MSF -> initMsfProcess(app)
            else -> throw IllegalStateException("Unknown process: $process")
        }
    }

    private var pendingEditText: EditText? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun initMainProcess(app: Application) {
        val method = if (HookEnv.isTim()) {
            "com.tencent.tim.aio.inputbar.simpleui.TimAIOInputSimpleUIVBDelegate".toClass.findMethod {
                name = "B"
            }
        } else requireMethod("InputRootInit")

        method.hookAfter { param ->
            val sendBtn = runCatching {
                param.thisObject::class.java.findField { type = Button::class.java }.get(param.thisObject) as? Button
            }.getOrNull() ?: return@hookAfter

            val editText = runCatching {
                param.thisObject::class.java.findField { type = EditText::class.java }.get(param.thisObject) as? EditText
            }.getOrNull() ?: return@hookAfter

            val inputRoot = runCatching {
                param.thisObject::class.java.declaredFields
                    .filter { ViewGroup::class.java.isAssignableFrom(it.type) }
                    .firstNotNullOfOrNull { field ->
                        runCatching {
                            field.isAccessible = true
                            field.get(param.thisObject) as? ViewGroup
                        }.getOrNull()
                    }
            }.getOrNull() ?: return@hookAfter

            sendBtn.setOnLongClickListener { _ ->
                onBtnLongClick(editText.text.toString(), sendBtn, editText, inputRoot)
                return@setOnLongClickListener true
            }
        }

        val resultReceiver = object : BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            override fun onReceive(context: Context, intent: Intent) {
                val sign = intent.getStringExtra("sign") ?: return
                val error = intent.getStringExtra("error")
                SyncUtils.runOnUiThread {
                    if (error != null) {
                        pendingEditText?.setText("签名获取失败: $error")
                    } else {
                        pendingEditText?.setText("${HookEnv.versionName} $sign")
                    }
                    pendingEditText = null
                }
            }
        }
        val filter = IntentFilter(ACTION_SIGN_RESULT)
        SyncUtils.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(resultReceiver, filter)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBtnLongClick(
        text: String,
        sendBtn: Button,
        editText: EditText,
        inputRoot: ViewGroup
    ) {
        pendingEditText = editText

        Intent(ACTION_REQUEST_SIGN).apply {
            putExtra("uin", QQInterfaces.currentUin)
            setPackage(HookEnv.hostAppPackageName)
        }.also {
            HookEnv.application.sendBroadcast(it)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun initMsfProcess(app: Application) {
        val requestReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                    val uin = intent.getStringExtra("uin") ?: "0"
                    val cmd = "MessageSvc.PbSendMsg"
                    val buffer = "000000160A08120608D48BCAE5031206080110001800".hex2ByteArray()
                    val seq = MsfService.getCore().nextSeq
                    val toServiceMsg: ToServiceMsg = createToServiceMsg(uin = uin).apply {
                        putWupBuffer(buffer)
                        requestSsoSeq = seq
                    }
                    val sign: String = if (HookEnv.isQQ()) {
                        QSign.c().a(toServiceMsg, cmd).sign.toHexString()
                    } else TSign.e().a(toServiceMsg, cmd).sign.toHexString()

                    Intent(ACTION_SIGN_RESULT).apply {
                        putExtra("sign", sign)
                        setPackage(context.packageName)
                    }.also {
                        context.sendBroadcast(it)
                    }
                }.onFailure { e ->
                    Log.e("", e)
                    Intent(ACTION_SIGN_RESULT).apply {
                        putExtra("error", e.message ?: "unknown error")
                        setPackage(context.packageName)
                    }.also {
                        context.sendBroadcast(it)
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_REQUEST_SIGN)
        SyncUtils.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(requestReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(requestReceiver, filter)
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "InputRootInit" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.input")
            matcher {
                usingEqStrings("binding", "inputRoot",
                    "findViewById(...)", "getContext(...)", "sendBtn"
                )
            }
        }
    )

    private fun createToServiceMsg(cmd: String = "MessageSvc.PbSendMsg", uin: String): ToServiceMsg {
        return ToServiceMsg("mobileqq.service", uin, cmd)
    }

    companion object {
        private const val ACTION_REQUEST_SIGN = "com.owo233.tcqt.GET_SIGN_REQUEST"
        private const val ACTION_SIGN_RESULT = "com.owo233.tcqt.GET_SIGN_RESULT"
    }
}

fun interface InputRootInitCallback {
    fun onBtnLongClick(text: String, sendBtn: Button, editText: EditText, inputRoot: ViewGroup)
}
