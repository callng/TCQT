package com.owo233.tcqt.features.hooks.enums

import com.owo233.tcqt.bootstrap.HookEnv.QQ_PACKAGE
import com.owo233.tcqt.bootstrap.HookEnv.TIM_PACKAGE

enum class HostTypeEnum(
    val packageName: String,
    val appName: String
) {
    QQ(QQ_PACKAGE, "QQ"),
    TIM(TIM_PACKAGE, "TIM");

    companion object {
        fun valueOfPackage(packageName: String): HostTypeEnum {
            val hostTypeEnums = entries.toTypedArray()
            for (hostTypeEnum in hostTypeEnums) {
                if (hostTypeEnum.packageName == packageName) {
                    return hostTypeEnum
                }
            }
            throw UnSupportHostTypeException("UnSupport HostType: $packageName")
        }

        fun contain(packageName: String): Boolean {
            val hostTypeEnums = entries.toTypedArray()
            for (hostTypeEnum in hostTypeEnums) {
                if (hostTypeEnum.packageName == packageName) {
                    return true
                }
            }
            return false
        }
    }

    class UnSupportHostTypeException: RuntimeException {
        constructor(): super()
        constructor(message: String?) : super(message)
        constructor(message: String?, cause: Throwable?) : super(message, cause)
        constructor(cause: Throwable?) : super(cause)
    }
}
