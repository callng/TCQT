package com.owo233.tcqt.servlet

import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.internals.QQInterfaces
import com.tencent.common.app.AppInterface
import com.tencent.qphone.base.remote.ToServiceMsg

internal object PacketTool {

    lateinit var app: AppInterface

    private val initLock = Any()

    private fun getOrUpdateService() : AppInterface {
        val currentApp = fetchCurrentApp()

        if (app != currentApp) {
            synchronized(initLock) {
                val reCheckApp = fetchCurrentApp()
                if (app != reCheckApp) {
                    app = reCheckApp
                }
            }
        }

        return app
    }

    private fun send(toServiceMsg: ToServiceMsg) {
        getOrUpdateService()
        app.sendToService(toServiceMsg)
    }

    private fun sendPbReq(toServiceMsg: ToServiceMsg) {
        getOrUpdateService()
        toServiceMsg.addAttribute("req_pb_protocol_flag", true)
        app.sendToService(toServiceMsg)
    }

    private fun createToServiceMsg(cmd: String): ToServiceMsg {
        getOrUpdateService()
        return ToServiceMsg("mobileqq.service", app.currentAccountUin, cmd)
    }

    private fun fetchCurrentApp(): AppInterface {
        if (!ProcUtil.isMain) throw IllegalStateException("Critical operation must run in main process")
        return QQInterfaces.appRuntime as AppInterface
    }

    fun sendBuffer(cmd: String, isPb: Boolean, buffer: ByteArray) {
        createToServiceMsg(cmd).apply {
            putWupBuffer(buffer)
        }.also { toServiceMsg ->
            if (isPb) sendPbReq(toServiceMsg) else send(toServiceMsg)
        }
    }
}
