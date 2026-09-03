package com.owo233.tcqt.hooks.func.fekit

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Button
import android.widget.EditText
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionPriority
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.hex2ByteArray
import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.loader.ReceiverRegistry
import com.owo233.tcqt.loader.zygisk.ZygiskNativeLibs
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.SyncUtils
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findField
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.invokeAs
import com.owo233.tcqt.utils.reflect.new
import com.tencent.mobileqq.msf.service.MsfService
import com.tencent.mobileqq.sign.QQSecuritySign
import com.tencent.qphone.base.remote.ToServiceMsg
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod

@RegisterAction
class GetSign : IAction, DexKitTask, InputRootInitCallback {

    private var pendingEditText: EditText? = null
    private var pendingSendBtn: Button? = null
    private var cachedSource32: String? = null

    private val signer by lazy {
        val method = requireMethod("getSign")
        method.declaringClass.new() to method
    }

    override val key: String get() = "get_sign"
    override val name: String get() = "获取测试签名"
    override val desc: String
        get() = "本功能仅用于测试，正常情况下无需启用!!! 用法: 在聊天框随便打个字符然后长按发送按钮即可获取。"
    override val uiTab: String get() = "调试"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.MSF)
    override val priority: ActionPriority get() = ActionPriority.EARLY

    override fun onRun(app: Application, process: ActionProcess) {
        when (process) {
            ActionProcess.MAIN -> initMainProcess(app)
            ActionProcess.MSF -> initMsfProcess(app)
            else -> throw IllegalStateException("Unknown process: $process")
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun initMainProcess(app: Application) {
        val method = if (HookEnv.isTIM()) {
            "com.tencent.tim.aio.inputbar.simpleui.TimAIOInputSimpleUIVBDelegate".toClass.findMethod {
                name = "B"
            }
        } else requireMethod("InputRootInit")

        method.hookAfter { param ->
            val sendBtn = runCatching {
                param.thisObject::class.java
                    .findField { type = Button::class.java }
                    .get(param.thisObject) as? Button
            }.getOrNull() ?: return@hookAfter

            val editText = runCatching {
                param.thisObject::class.java
                    .findField { type = EditText::class.java }
                    .get(param.thisObject) as? EditText
            }.getOrNull() ?: return@hookAfter

            sendBtn.setOnLongClickListener {
                onBtnLongClick(sendBtn, editText)
                return@setOnLongClickListener true
            }
        }

        val resultReceiver = object : BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            override fun onReceive(context: Context, intent: Intent) {
                val error = intent.getStringExtra("error")
                if (error != null) {
                    SyncUtils.runOnUiThread {
                        val target = pendingEditText ?: getAIOEditText()
                        target?.setText("签名获取失败: $error")
                        restoreSendBtn()
                        pendingEditText = null
                    }
                    return
                }

                val sign = intent.getStringExtra("sign") ?: return
                val source32 = intent.getStringExtra("source32")
                if (source32 != null && source32.length == SOURCE32_LENGTH * 2) {
                    cachedSource32 = source32
                }
                SyncUtils.runOnUiThread {
                    val target = pendingEditText ?: getAIOEditText()
                    if (target != null) {
                        val base = "${HookEnv.versionName} $sign"
                        val withSource32 = shouldScanSource32() &&
                            source32 != null &&
                            source32.length == SOURCE32_LENGTH * 2
                        target.setText(if (withSource32) "$base $source32" else base)
                    }
                    restoreSendBtn()
                    pendingEditText = null
                }
            }
        }
        val filter = IntentFilter(ACTION_SIGN_RESULT)
        ReceiverRegistry.register(app, resultReceiver, filter)
    }

    @SuppressLint("SetTextI18n")
    override fun onBtnLongClick(
        sendBtn: Button,
        editText: EditText
    ) {
        pendingEditText = editText

        val userInput = editText.text?.toString()?.trim() ?: ""
        val cmd = if (userInput.length >= 13) userInput else "MessageSvc.PbSendMsg"

        val needSource32 = shouldScanSource32()
        val waitingForScan = needSource32 && cachedSource32 == null
        if (waitingForScan) {
            editText.setText("需稍等片刻...")
            sendBtn.isEnabled = false
            editText.isEnabled = false
            pendingSendBtn = sendBtn
        } else {
            pendingSendBtn = null
        }

        Intent(ACTION_REQUEST_SIGN).apply {
            putExtra("uin", QQInterfaces.currentUin)
            putExtra("cmd", cmd)
            putExtra("needSource32", needSource32)
            setPackage(HookEnv.hostAppPackageName)
        }.also {
            HookEnv.application.sendBroadcast(it)
        }

        // 超时保护：MSF 无结果（如扫描过慢）时恢复按钮，避免长期悬挂
        editText.postDelayed({
            if (pendingEditText === editText) {
                pendingEditText = null
                editText.isEnabled = true
            }
            if (pendingSendBtn === sendBtn) {
                pendingSendBtn = null
                sendBtn.isEnabled = true
            }
        }, 30_000L)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun initMsfProcess(app: Application) {
        val requestReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                    val uin = intent.getStringExtra("uin") ?: "0"
                    val cmd = intent.getStringExtra("cmd")
                        ?.takeIf { it.isNotBlank() }
                        ?: "MessageSvc.PbSendMsg"
                    val buffer = "000000160A08120608D48BCAE5031206080110001800".hex2ByteArray()
                    val seq = MsfService.getCore().nextSeq
                    val toServiceMsg: ToServiceMsg = createToServiceMsg(uin = uin).apply {
                        putWupBuffer(buffer)
                        requestSsoSeq = seq
                    }

                    val sign = if (HookEnv.isQQ()) {
                        val (instance, method) = signer
                        instance.invokeAs<QQSecuritySign.SignResult>(method, toServiceMsg, cmd)
                            .sign
                            .toHexString()
                    } else {
                        com.tencent.mobileqq.msf.core.d0.a.e().a(toServiceMsg, cmd)
                            .sign
                            .toHexString()
                    }

                    if (intent.getBooleanExtra("needSource32", false)) {
                        val cached = cachedSource32
                        if (cached != null) {
                            sendResult(context, sign, cached)
                        } else {
                            Thread {
                                var source32 = cachedSource32
                                if (source32 == null) {
                                    source32 = scanSource32()
                                    if (source32.length == SOURCE32_LENGTH * 2) {
                                        cachedSource32 = source32
                                    }
                                }
                                sendResult(context, sign, source32.takeIf { it.isNotEmpty() })
                            }.start()
                        }
                    } else {
                        sendResult(context, sign, null)
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
        ReceiverRegistry.register(app, requestReceiver, filter)
    }

    private fun sendResult(context: Context, sign: String, source32: String?) {
        Intent(ACTION_SIGN_RESULT).apply {
            putExtra("sign", sign)
            if (source32 != null) putExtra("source32", source32)
            setPackage(context.packageName)
        }.also { context.sendBroadcast(it) }
    }

    private fun restoreSendBtn() {
        val btn = pendingSendBtn ?: return
        pendingSendBtn = null
        btn.isEnabled = true
        val editText = pendingEditText ?: getAIOEditText() ?: return
        editText.isEnabled = true
    }

    override fun getCacheKeys(): Set<String> {
        if (HookEnv.isTIM()) return emptySet()
        return setOf(TASK_INPUT_ROOT_INIT, TASK_GET_SIGN)
    }

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        if (HookEnv.isTIM()) return

        with(bridge) {
            findAndCache(cache, TASK_GET_SIGN) {
                searchPackages("com.tencent.mobileqq.msf.core")
                matcher {
                    usingEqStrings("invoke getSign start", "invoke getSign end")
                }
            }

            val found = findMethod(FindMethod().apply {
                searchPackages("com.tencent.mobileqq.aio.input.simpleui")
                matcher {
                    usingEqStrings(
                        "binding", "inputRoot",
                        "findViewById(...)", "getContext(...)", "sendBtn"
                    )
                }
            }).singleOrNull()?.descriptor
            cache[TASK_INPUT_ROOT_INIT] = found ?: findMethod(FindMethod().apply {
                searchPackages("com.tencent.mobileqq.aio.input.simpleui")
                matcher {
                    usingEqStrings("inputRoot.findViewById(R.id.send_btn)")
                }
            }).singleOrNull()?.descriptor ?: ""
        }
    }

    private fun DexKitBridge.findAndCache(
        cache: MutableMap<String, String>,
        key: String,
        init: FindMethod.() -> Unit
    ) {
        findMethod(FindMethod().apply(init))
            .singleOrNull()?.descriptor
            .let { cache[key] = it ?: "" }
    }

    @SuppressLint("DiscouragedApi")
    private fun getAIOEditText(): EditText? {
        return runCatching {
            QQInterfaces.topActivity.let { activity ->
                val resId = activity.resources.getIdentifier(
                    "input",
                    "id",
                    activity.packageName
                )
                if (resId != 0) {
                    activity.findViewById<EditText>(resId)
                } else null
            }
        }.getOrNull()
    }

    private fun createToServiceMsg(cmd: String = "MessageSvc.PbSendMsg", uin: String): ToServiceMsg {
        return ToServiceMsg("mobileqq.service", uin, cmd)
    }

    private fun shouldScanSource32(): Boolean {
        return HookEnv.isQQ() && HookEnv.requireMinQQVersion(QQVersion.QQ_9_3_50_BETA_40120)
    }

    private external fun nativeScanSource32(): String

    @Synchronized
    private fun scanSource32(): String {
        if (!nativeReady) {
            Log.e("GetSign: libtcqtmem not loaded, source32 unavailable")
            return ""
        }
        return try {
            val result = nativeScanSource32()
            result
        } catch (e: Throwable) {
            Log.e("GetSign: native scan error", e)
            ""
        }
    }

    companion object {
        private const val ACTION_REQUEST_SIGN = "com.owo233.tcqt.GET_SIGN_REQUEST"
        private const val ACTION_SIGN_RESULT = "com.owo233.tcqt.GET_SIGN_RESULT"
        private const val TASK_INPUT_ROOT_INIT = "InputRootInit"
        private const val TASK_GET_SIGN = "getSign"

        private const val SOURCE32_LENGTH = 32

        private val nativeReady: Boolean by lazy {
            val ok = ZygiskNativeLibs.load("tcqtmem")
            if (!ok) Log.e("GetSign: load libtcqtmem failed")
            ok
        }
    }
}

fun interface InputRootInitCallback {
    fun onBtnLongClick(sendBtn: Button, editText: EditText)
}
