package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.beforeHook
import com.owo233.tcqt.ext.hex2ByteArray
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.Log

object GuidHelper {

    private val clazz by lazy { XpClassLoader.load("oicq.wlogin_sdk.tools.util")!! }
    private val clazz2 by lazy { XpClassLoader.load("com.tencent.mobileqq.qsec.qsecurity.QSecConfig")!! }

    fun hookGuid(guid: String) {
        runCatching {
            hookNeedChangeGuid()
            hookGetGuidFromFile(guid)
            hookSaveGuidToFile(guid)
            hookGetLastGuid(guid)
            hookGenerateGuid(guid)
            hookSaveCurGuid(guid)
            hookSetupBusinessInfo(guid)
        }.onFailure {
            Log.e("Hook Guid Failure", it)
        }
    }

    private fun hookNeedChangeGuid() {
        clazz.hookMethod("needChangeGuid", afterHook {
            it.result = false
        })
    }

    private fun hookGetGuidFromFile(guid: String) {
        clazz.hookMethod("getGuidFromFile", afterHook {
            it.result = guid.hex2ByteArray()
        })
    }

    private fun hookSaveGuidToFile(guid: String) {
        clazz.hookMethod("saveGuidToFile", beforeHook {
            it.args[1] = guid.hex2ByteArray()
        })
    }

    private fun hookGetLastGuid(guid: String) {
        clazz.hookMethod("get_last_guid", afterHook {
            it.result = guid.hex2ByteArray()
        })
    }

    private fun hookGenerateGuid(guid: String) {
        clazz.hookMethod("generateGuid", afterHook {
            it.result = guid.hex2ByteArray()
        })
    }

    private fun hookSaveCurGuid(guid: String) {
        clazz.hookMethod("save_cur_guid", beforeHook {
            it.args[1] = guid.hex2ByteArray()
        })
    }

    private fun hookSetupBusinessInfo(guid: String) {
        clazz2.hookMethod("setupBusinessInfo", beforeHook {
            it.args[2] = guid.hex2ByteArray()
        })
    }
}
