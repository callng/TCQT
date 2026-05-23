package com.owo233.tcqt.hooks.helper

import android.content.Context
import com.owo233.tcqt.ext.hex2ByteArray
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.log.Log
import java.io.File
import java.io.FileOutputStream

object GuidHelper {

    private val clazz by lazy { "oicq.wlogin_sdk.tools.util".toClass }
    private val clazz2 by lazy { "com.tencent.mobileqq.qsec.qsecurity.QSecConfig".toClass }

    fun hookGuid(guid: String) {
        runCatching {
            hookNeedChangeGuid()
            hookGetGuidFromFile(guid)
            hookSaveGuidToFile(guid)
            hookSaveAndroidId(guid)
            hookGetSavedAndroidId(guid)
            hookGetLastGuid(guid)
            hookGenerateGuid(guid)
            hookSaveCurGuid(guid)
            hookSetupBusinessInfo(guid)
        }.onFailure {
            Log.e("Hook Guid Failure", it)
        }
    }

    private fun hookNeedChangeGuid() {
        clazz.hookMethodBefore(
            "needChangeGuid",
            Context::class.java
        ) {
            it.result = false
        }
    }

    private fun hookGetGuidFromFile(guid: String) {
        clazz.hookMethodBefore(
            "getGuidFromFile",
            Context::class.java
        ) { param ->
            val context = param.args[0] as Context
            val file = File(context.filesDir, "wlogin_device.dat")
            FileOutputStream(file, false).use { it.write(guid.hex2ByteArray()) }
            param.result = guid.hex2ByteArray()
        }
    }

    private fun hookSaveGuidToFile(guid: String) {
        clazz.hookMethodBefore(
            "saveGuidToFile",
            Context::class.java,
            ByteArray::class.java
        ) {
            it.args[1] = guid.hex2ByteArray()
        }
    }

    private fun hookSaveAndroidId(guid: String) {
        runCatching {
            clazz.hookMethodBefore(
                "save_android_id",
                Context::class.java,
                String::class.java
            ) {
                it.args[1] = guid.hex2ByteArray()
            }
        }.onFailure {
            throw object : IllegalStateException(
                "这个异常不需要报告!!! 错误是故意制造并抛出的，因为指定的方法签名不存在，请忽略这个错误。"
            ) {
                override fun fillInStackTrace() = this
            }
        }
    }

    private fun hookGetSavedAndroidId(guid: String) {
        clazz.hookMethodBefore(
            "get_saved_android_id",
            Context::class.java
        ) {
            it.result = guid.hex2ByteArray()
        }
    }

    private fun hookGetLastGuid(guid: String) {
        clazz.hookMethodBefore(
            "get_last_guid",
            Context::class.java
        ) {
            it.result = guid.hex2ByteArray()
        }
    }

    private fun hookGenerateGuid(guid: String) {
        clazz.hookMethodBefore(
            "generateGuid",
            Context::class.java
        ) {
            it.result = guid.hex2ByteArray()
        }
    }

    private fun hookSaveCurGuid(guid: String) {
        clazz.hookMethodBefore(
            "save_cur_guid",
            Context::class.java,
            ByteArray::class.java
        ) {
            it.args[1] = guid.hex2ByteArray()
        }
    }

    private fun hookSetupBusinessInfo(guid: String) {
        clazz2.hookMethodBefore(
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
