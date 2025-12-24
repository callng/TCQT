package com.owo233.tcqt.hooks.func.test

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.proto2json.ProtoUtils
import com.owo233.tcqt.utils.proto2json.asUtf8String
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

    override fun onRun(ctx: Context, process: ActionProcess) {
        QQSecuritySign::class.java.hookBeforeMethod(
            "dispatchEvent",
            String::class.java,
            String::class.java,
            EventCallback::class.java
        ) { param ->
            val eventName = param.args[0] as String
            val eventData = param.args[1] as String

            (param.args[2] as? EventCallback)?.let { originalCallback ->
                val proxy = Proxy.newProxyInstance(
                    originalCallback.javaClass.classLoader,
                    arrayOf(EventCallback::class.java)
                ) { _, method, args ->
                    if (method.name == "onResult" && args != null && args.size == 2) {
                        val code = args[0] as Int
                        val result = (args[1] as ByteArray).toString(Charsets.UTF_8)

                        if (!result.isEmpty()) {
                            Log.e("dispatchEvent Log Start\neventName: $eventName\neventData: $eventData\ncode: $code\nresult: $result\ndispatchEvent Log End")
                        }
                    }

                    method.invoke(originalCallback, *(args ?: emptyArray()))
                }

                param.args[2] = proxy
            }
        }

        ChannelProxyExt::class.java.hookBeforeMethod(
            "sendMessage",
            String::class.java,
            ByteArray::class.java,
            Long::class.javaPrimitiveType
        ) { param ->
            val cmd = param.args[0] as String
            val body = param.args[1] as ByteArray
            val callbackId = param.args[2] as Long
            val bcmd = ProtoUtils.decodeFromByteArray(body)[1].asUtf8String

            Log.i("sendMessage Log Start\ncmd: $cmd\nbcmd: $bcmd\ncallbackId: $callbackId\nbody: ${body.toHexString()}\nsendMessage Log End")
        }
    }

    override val key: String = GeneratedSettingList.TCQT_DEBUG

    override val processes: Set<ActionProcess> = setOf(ActionProcess.MSF)
}
