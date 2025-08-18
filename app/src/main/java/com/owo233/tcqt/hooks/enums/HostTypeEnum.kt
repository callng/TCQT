package com.owo233.tcqt.hooks.enums

import com.owo233.tcqt.hooks.base.PACKAGE_NAME_QQ
import com.owo233.tcqt.hooks.base.PACKAGE_NAME_TIM

enum class HostTypeEnum(
    val packageName: String,
    val appName: String
) {
    QQ(PACKAGE_NAME_QQ, "QQ"),
    TIM(PACKAGE_NAME_TIM, "TIM");

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
}

class UnSupportHostTypeException: RuntimeException {
    constructor(): super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
