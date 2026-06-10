/**
 * 代码来自：https://github.com/oneQAQone/QFun
 * 翻译：owo233
 */

package com.owo233.tcqt.hooks.func.theme

import androidx.core.os.BundleCompat
import com.owo233.tcqt.internals.QQInterfaces
import com.tencent.common.app.BaseApplicationImpl
import com.tencent.qphone.base.remote.FromServiceMsg
import com.tencent.qphone.base.remote.ToServiceMsg
import mqq.app.NewIntent
import mqq.app.api.impl.SSOEasyServlet
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

internal object PacketHelper {

    private fun packet(data: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { bos ->
            DataOutputStream(bos).use { dos ->
                dos.writeInt(data.size + 4)
                dos.write(data)
            }
            bos.toByteArray()
        }
    }

    fun sendRequest(serviceCmd: String, rawData: ByteArray, receiver: IReceiver) {
        val intent = NewIntent(
            BaseApplicationImpl.sApplication,
            SSOEasyServlet::class.java
        )
        val toServiceMsg = ToServiceMsg(
            "mobileqq.service",
            QQInterfaces.currentUin,
            serviceCmd
        ).apply {
            putWupBuffer(packet(rawData))
        }

        intent.setObserver { _, isSuccess, bundle ->
            if (isSuccess && bundle != null) {
                val fromMsg = BundleCompat.getParcelable(
                    bundle,
                    "FromServiceMsg",
                    FromServiceMsg::class.java
                )
                receiver.onReceive(fromMsg!!.wupBuffer)
            } else {
                receiver.onReceive(byteArrayOf())
            }
        }

        intent.putExtra("ToServiceMsg", toServiceMsg)
        QQInterfaces.appRuntime.startServlet(intent)
    }
}
