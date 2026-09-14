package com.owo233.tcqt.features.debug

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Button
import android.widget.EditText
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.NativeLibs
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.hex2ByteArray
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.env.toHexString
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.invokeAs
import com.owo233.tcqt.core.reflect.new
import com.owo233.tcqt.core.sync.ReceiverRegistry
import com.owo233.tcqt.core.sync.SyncUtils
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.mobileqq.msf.service.MsfService
import com.tencent.mobileqq.sign.QQSecuritySign
import com.tencent.qphone.base.remote.ToServiceMsg
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import java.lang.ref.WeakReference

@RegisterAction
object GetSign : Feature(
    key = "get_sign",
    name = "获取测试签名",
    desc = "本功能仅用于测试，正常情况下无需启用!!! 用法: 在聊天框随便打个字符然后长按发送按钮即可获取。",
    processes = setOf(ActionProcess.MAIN, ActionProcess.MSF),
    priority = ActionPriority.EARLY,
), DexKitTask {

    private var pendingEditText: WeakReference<EditText>? = null
    private var pendingSendBtn: WeakReference<Button>? = null

    private var cachedSource32: String? = null
    private var backupString: String? = null

    private val signer by lazy {
        val method = requireMethod("getSign")
        method.declaringClass.new() to method
    }

    override fun install() {
        when (currentProcess) {
            ActionProcess.MAIN -> initMainProcess(hostApp)
            ActionProcess.MSF -> initMsfProcess(hostApp)
            else -> throw IllegalStateException("Unknown process: $currentProcess")
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun initMainProcess(app: Application) {
        val method = if (HookEnv.isTIM()) {
            "com.tencent.tim.aio.inputbar.simpleui.TimAIOInputSimpleUIVBDelegate"
                .toClass
                .findMethod {
                    name = "B"
                }
        } else {
            requireMethod("InputRootInit")
        }

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
                true
            }
        }

        val resultReceiver = object : BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val error = intent.getStringExtra("error")

                if (error != null) {
                    SyncUtils.runOnUiThread {
                        getTargetEditText()?.setText("签名获取失败: $error")
                        restoreSendBtn()
                        clearPendingViews()
                    }
                    return
                }

                val sign = intent.getStringExtra("sign") ?: return
                val source32 = intent.getStringExtra("source32")

                if (source32 != null && source32.length == SOURCE32_LENGTH * 2) {
                    cachedSource32 = source32
                }

                SyncUtils.runOnUiThread {
                    val target = getTargetEditText()

                    if (target != null) {
                        val base = "${HookEnv.versionName} $sign"
                        val withSource32 =
                            source32 != null &&
                                    source32.length == SOURCE32_LENGTH * 2

                        if (shouldScanSource32()) {
                            target.setText(
                                if (withSource32) {
                                    "$base $source32"
                                } else {
                                    backupString
                                }
                            )
                        } else {
                            target.setText(base)
                        }
                    }

                    restoreSendBtn()
                    clearPendingViews()
                }
            }
        }

        val filter = IntentFilter(ACTION_SIGN_RESULT)
        ReceiverRegistry.register(app, resultReceiver, filter)
    }

    private fun getTargetEditText(): EditText? {
        return pendingEditText?.get() ?: getAIOEditText()
    }

    @SuppressLint("SetTextI18n")
    private fun onBtnLongClick(
        sendBtn: Button,
        editText: EditText,
    ) {
        pendingEditText = WeakReference(editText)

        val userInput = (editText.text?.toString() ?: "").also {
            backupString = it
        }

        val cmd = if (userInput.trim().isNotEmpty()) {
            userInput
        } else {
            "MessageSvc.PbSendMsg"
        }

        val needSource32 = shouldScanSource32()
        val waitingForScan = needSource32 && cachedSource32 == null

        if (waitingForScan) {
            editText.setText("需稍等片刻...")
            sendBtn.isEnabled = false
            editText.isEnabled = false

            pendingSendBtn = WeakReference(sendBtn)
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

        editText.postDelayed({
            val pendingEdit = pendingEditText?.get()
            if (pendingEdit === editText) {
                pendingEditText = null
                editText.isEnabled = true
            }

            val pendingButton = pendingSendBtn?.get()
            if (pendingButton === sendBtn) {
                pendingSendBtn = null
                sendBtn.isEnabled = true
            }
        }, REQUEST_TIMEOUT_MS)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun initMsfProcess(app: Application) {
        val requestReceiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                runCatching {
                    val uin = intent.getStringExtra("uin") ?: "0"
                    val cmd = intent.getStringExtra("cmd")

                    val buffer =
                        "000000160A08120608D48BCAE5031206080110001800"
                            .hex2ByteArray()

                    val seq = MsfService.getCore().nextSeq

                    val toServiceMsg: ToServiceMsg =
                        createToServiceMsg(uin = uin).apply {
                            putWupBuffer(buffer)
                            requestSsoSeq = seq
                        }

                    val sign = if (HookEnv.isQQ()) {
                        val (instance, method) = signer

                        instance
                            .invokeAs<QQSecuritySign.SignResult>(
                                method,
                                toServiceMsg,
                                cmd,
                            )
                            .sign
                            .toHexString()
                    } else {
                        com.tencent.mobileqq.msf.core.d0.a.e()
                            .a(toServiceMsg, cmd)
                            .sign
                            .toHexString()
                    }

                    if (intent.getBooleanExtra("needSource32", false)) {
                        val cached = cachedSource32

                        if (cached != null) {
                            sendResult(
                                context = context,
                                sign = sign,
                                source32 = cached,
                            )
                        } else {
                            Thread {
                                var source32 = cachedSource32

                                if (source32 == null) {
                                    source32 = scanSource32()

                                    if (source32.length == SOURCE32_LENGTH * 2) {
                                        cachedSource32 = source32
                                    }
                                }

                                sendResult(
                                    context = context,
                                    sign = sign,
                                    source32 = source32.takeIf { it.isNotEmpty() },
                                )
                            }.start()
                        }
                    } else {
                        sendResult(
                            context = context,
                            sign = sign,
                            source32 = null,
                        )
                    }
                }.onFailure { e ->
                    Log.e("", e)

                    Intent(ACTION_SIGN_RESULT).apply {
                        putExtra(
                            "error",
                            e.message ?: "unknown error",
                        )
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

    private fun sendResult(
        context: Context,
        sign: String,
        source32: String?,
    ) {
        Intent(ACTION_SIGN_RESULT).apply {
            putExtra("sign", sign)

            if (source32 != null) {
                putExtra("source32", source32)
            }

            setPackage(context.packageName)
        }.also {
            context.sendBroadcast(it)
        }
    }

    private fun restoreSendBtn() {
        pendingSendBtn?.get()?.let {
            it.isEnabled = true
        }

        getTargetEditText()?.isEnabled = true
    }

    private fun clearPendingViews() {
        pendingEditText = null
        pendingSendBtn = null
    }

    override fun getCacheKeys(): Set<String> {
        if (HookEnv.isTIM()) {
            return emptySet()
        }

        return setOf(
            TASK_INPUT_ROOT_INIT,
            TASK_GET_SIGN,
        )
    }

    override fun execute(
        bridge: DexKitBridge,
        cache: MutableMap<String, String>,
    ) {
        if (HookEnv.isTIM()) {
            return
        }

        with(bridge) {
            findAndCache(cache, TASK_GET_SIGN) {
                searchPackages("com.tencent.mobileqq.msf.core")

                matcher {
                    usingEqStrings(
                        "invoke getSign start",
                        "invoke getSign end",
                    )
                }
            }

            val found = findMethod(
                FindMethod().apply {
                    searchPackages(
                        "com.tencent.mobileqq.aio.input.simpleui",
                    )

                    matcher {
                        usingEqStrings(
                            "binding",
                            "inputRoot",
                            "findViewById(...)",
                            "getContext(...)",
                            "sendBtn",
                        )
                    }
                },
            ).singleOrNull()?.descriptor

            cache[TASK_INPUT_ROOT_INIT] = found
                ?: findMethod(
                    FindMethod().apply {
                        searchPackages(
                            "com.tencent.mobileqq.aio.input.simpleui",
                        )

                        matcher {
                            usingEqStrings(
                                "inputRoot.findViewById(R.id.send_btn)",
                            )
                        }
                    },
                ).singleOrNull()?.descriptor
                        ?: ""
        }
    }

    private fun DexKitBridge.findAndCache(
        cache: MutableMap<String, String>,
        key: String,
        init: FindMethod.() -> Unit,
    ) {
        findMethod(
            FindMethod().apply(init),
        )
            .singleOrNull()
            ?.descriptor
            .let {
                cache[key] = it ?: ""
            }
    }

    @SuppressLint("DiscouragedApi")
    private fun getAIOEditText(): EditText? {
        return runCatching {
            QQInterfaces.topActivity.let { activity ->
                val resId = activity.resources.getIdentifier(
                    "input",
                    "id",
                    activity.packageName,
                )

                if (resId != 0) {
                    activity.findViewById<EditText>(resId)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun createToServiceMsg(
        cmd: String = "MessageSvc.PbSendMsg",
        uin: String,
    ): ToServiceMsg {
        return ToServiceMsg(
            "mobileqq.service",
            uin,
            cmd,
        )
    }

    private fun shouldScanSource32(): Boolean {
        return HookEnv.isQQ() &&
                HookEnv.requireMinQQVersion(
                    QQVersion.QQ_9_3_50_BETA_40120,
                )
    }

    private external fun nativeScanSource32(): String

    @Synchronized
    private fun scanSource32(): String {
        if (!nativeReady) {
            Log.e(
                "GetSign: libtcqtmem not loaded, source32 unavailable",
            )
            return ""
        }

        return try {
            nativeScanSource32()
        } catch (e: Throwable) {
            Log.e(
                "GetSign: native scan error",
                e,
            )
            ""
        }
    }

    private const val ACTION_REQUEST_SIGN = "com.owo233.tcqt.GET_SIGN_REQUEST"

    private const val ACTION_SIGN_RESULT = "com.owo233.tcqt.GET_SIGN_RESULT"

    private const val TASK_INPUT_ROOT_INIT = "InputRootInit"

    private const val TASK_GET_SIGN = "getSign"

    private const val SOURCE32_LENGTH = 32

    private const val REQUEST_TIMEOUT_MS = 30_000L

    private val nativeReady: Boolean by lazy {
        val ok = NativeLibs.load("tcqtmem")
        if (!ok) Log.e("GetSign: load libtcqtmem failed")
        ok
    }
}
