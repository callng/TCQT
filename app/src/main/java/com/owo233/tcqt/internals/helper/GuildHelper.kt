package com.owo233.tcqt.internals.helper

import com.owo233.tcqt.ext.toHexString
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.PlatformTools
import com.tencent.guild.api.util.IGuildUtilApi
import com.tencent.mobileqq.msf.sdk.MSFSharedPreUtils
import com.tencent.mobileqq.qroute.QRoute
import oicq.wlogin_sdk.tools.MD5

object GuildHelper {

    fun getGuid(): String {
        return runCatching {
            MSFSharedPreUtils.getGuid()
                ?.takeIf { it.isNotEmpty() }
                ?: QRoute.api(IGuildUtilApi::class.java)
                    .guid
                    .toHexString()
                    .takeIf { it.isNotEmpty() }
        }.getOrNull()
            ?: getGuidByAndroidID()
    }

    private fun getGuidByAndroidID(): String {
        Log.w("getGuid 触发保底行为!")
        val aid = PlatformTools.getAndroidID() ?: return ""
        val mac = "02:00:00:00:00:00"
        return MD5.toMD5Byte(aid + mac).toHexString()
    }
}
