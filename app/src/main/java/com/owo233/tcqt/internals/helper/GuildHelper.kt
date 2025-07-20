package com.owo233.tcqt.internals.helper

import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.utils.PlatformTools
import com.tencent.guild.api.util.IGuildUtilApi
import com.tencent.mobileqq.qroute.QRoute
import oicq.wlogin_sdk.tools.MD5

internal object GuildHelper {
    fun getGuidHex(): String {
        val api = QRoute.api(IGuildUtilApi::class.java)
        val guid = api.guid?.toHexString()
        if (guid.isNullOrBlank()) {
            val androidId = PlatformTools.getAndroidID()
            val macAddress = "02:00:00:00:00:00"
            return MD5.toMD5Byte(androidId + macAddress).toHexString()
        } else {
            return guid
        }
    }
}
