package com.owo233.tcqt.hooks.func.test

import android.app.Application
import com.owo233.tcqt.HookEnv.requireMinQQVersion
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.proto2json.ProtoUtils
import com.owo233.tcqt.utils.proto2json.asUtf8String
import com.owo233.tcqt.utils.reflect.findMethod
import com.tencent.mobileqq.channel.ChannelProxyExt
import com.tencent.mobileqq.fe.EventCallback
import com.tencent.mobileqq.sign.QQSecuritySign
import java.lang.reflect.Proxy

@RegisterAction
@RegisterSetting(
    key = "tcqt_debug",
    name = "FEKit打印调用内容",
    type = SettingType.BOOLEAN,
    desc = "向框架日志中输出指定内容，本功能仅做调试使用，正常使用模块请勿启用本功能。",
    uiTab = "调试"
)
class TCQTDeBug : IAction {

    override val key: String
        get() = GeneratedSettingList.TCQT_DEBUG

    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MSF)

    override fun onRun(app: Application, process: ActionProcess) {
        hookSend()
        hookEvent()
    }

    private fun hookSend() {
        val method = "sendMessageInner".takeIf {
            requireMinQQVersion(QQVersion.QQ_9_2_60_BETA_ONE)
        } ?: "sendMessage"

        ChannelProxyExt::class.java.findMethod {
            name = method
            paramTypes = arrayOf(string, byteArr, long)
        }.hookBefore { param ->
            val cmd = param.args[0] as String
            val body = param.args[1] as ByteArray
            val callbackId = param.args[2] as Long
            val bcmd = ProtoUtils.decodeFromByteArray(body)[1].asUtf8String

            Log.i("sendMessageInner Log Start\ncmd: $cmd\nbcmd: $bcmd\ncallbackId: $callbackId\nbody: ${body.toHexString()}\nsendMessageInner Log End")
        }
    }

    private fun hookEvent() {
        QQSecuritySign::class.java.findMethod {
            name = "dispatchEvent"
            paramTypes = arrayOf(string, string, EventCallback::class.java)
        }.hookBefore { param ->
            val eventName = param.args[0] as String
            val eventData = param.args[1] as String
            val originalCallback = param.args[2] as? EventCallback ?: return@hookBefore

            val proxy = Proxy.newProxyInstance(
                originalCallback.javaClass.classLoader,
                arrayOf(EventCallback::class.java)
            ) { _, method, args ->
                if (method.name == "onResult" && args.size == 2) {
                    val code = args[0] as Int
                    val result = (args[1] as ByteArray).toString(Charsets.UTF_8)

                    if (!result.isEmpty()) {
                        Log.i("dispatchEvent Log Start\neventName: $eventName\neventData: $eventData\ncode: $code\nresult: $result\ndispatchEvent Log End")
                    }
                }

                method.invoke(originalCallback, *args)
            }

            param.args[2] = proxy
        }
    }
}
