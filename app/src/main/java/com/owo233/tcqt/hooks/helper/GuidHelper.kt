package com.owo233.tcqt.hooks.helper

import android.content.Context
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.hex2ByteArray
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.hookBeforeMethod

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
        clazz.hookAfterMethod(
            "needChangeGuid",
            Context::class.java
        ) {
            it.result = false
        }
    }

    private fun hookGetGuidFromFile(guid: String) {
        clazz.hookAfterMethod(
            "getGuidFromFile",
            Context::class.java
        ) {
            it.result = guid.hex2ByteArray()
        }
    }

    private fun hookSaveGuidToFile(guid: String) {
        clazz.hookBeforeMethod(
            "saveGuidToFile",
            Context::class.java,
            ByteArray::class.java
        ) {
            it.args[1] = guid.hex2ByteArray()
        }
    }

    private fun hookGetLastGuid(guid: String) {
        clazz.hookAfterMethod(
            "get_last_guid",
            Context::class.java
        ) {
            it.result = guid.hex2ByteArray()
        }
    }

    private fun hookGenerateGuid(guid: String) {
        clazz.hookAfterMethod(
            "generateGuid",
            Context::class.java
        ) {
            it.result = guid.hex2ByteArray()
        }
    }

    private fun hookSaveCurGuid(guid: String) {
        clazz.hookBeforeMethod(
            "save_cur_guid",
            Context::class.java,
            ByteArray::class.java
        ) {
            it.args[1] = guid.hex2ByteArray()
        }
    }

    private fun hookSetupBusinessInfo(guid: String) {
        clazz2.hookBeforeMethod(
            "setupBusinessInfo",
            Context::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        ) {
            it.args[2] = guid.hex2ByteArray()
        }
    }
}
