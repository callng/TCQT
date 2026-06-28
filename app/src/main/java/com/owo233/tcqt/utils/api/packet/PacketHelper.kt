package com.owo233.tcqt.utils.api.packet

import androidx.core.os.BundleCompat
import com.owo233.tcqt.internals.QQInterfaces
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

    fun sendRequest(serviceCmd: String, rawData: ByteArray, receiver: IReceiver? = null) {
        NewIntent(
            QQInterfaces.appRuntime.app,
            SSOEasyServlet::class.java
        ).apply {
            putExtra("ToServiceMsg", ToServiceMsg(
                "mobileqq.service",
                QQInterfaces.currentUin,
                serviceCmd
            ).apply {
                putWupBuffer(packet(rawData))
                if (receiver == null) isNeedCallback = false
            })
            receiver?.let {
                setObserver { _, isSuccess, bundle ->
                    if (isSuccess && bundle != null) {
                        val fromMsg = BundleCompat.getParcelable(
                            bundle,
                            FromServiceMsg::class.java.simpleName,
                            FromServiceMsg::class.java
                        )
                        it.onReceive(fromMsg!!.wupBuffer.drop(4).toByteArray())
                    } else {
                        it.onReceive(byteArrayOf())
                    }
                }
            }
        }.also {
            QQInterfaces.appRuntime.startServlet(it)
        }
    }
}
