@file:JvmName("HostInfo")
package com.owo233.tcqt.utils

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat

const val PACKAGE_NAME_QQ = "com.tencent.mobileqq"
const val PACKAGE_NAME_TIM = "com.tencent.tim"
const val PACKAGE_NAME_SELF = "com.owo233.tcqt"

lateinit var hostInfo: HostInfoImpl

fun initHostInfo(appCtx: Application) {
    if (::hostInfo.isInitialized)  return

    val packageInfo = getHostInfo(appCtx)
    val packageName = appCtx.packageName

    hostInfo = HostInfoImpl(
        application = appCtx,
        packageName = packageName,
        hostName = appCtx.applicationInfo.loadLabel(appCtx.packageManager).toString(),
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        versionCode32 = PackageInfoCompat.getLongVersionCode(packageInfo).toInt(),
        versionName = packageInfo.versionName ?: "",
        hostSpecies = when (packageName) {
            PACKAGE_NAME_QQ -> HostSpecies.QQ
            PACKAGE_NAME_TIM -> HostSpecies.TIM
            PACKAGE_NAME_SELF -> HostSpecies.TCQT
            else -> HostSpecies.UNKNOWN
        }
    )
}

private fun getHostInfo(ctx: Context): PackageInfo {
    return try {
        ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_META_DATA)
    } catch (e: PackageManager.NameNotFoundException) {
        logE("HostInfo", "Can not get PackageInfo", e)
        throw e
    }
}

data class HostInfoImpl(
    val application: Application,
    val packageName: String,
    val hostName: String,
    val versionCode: Long,
    val versionCode32: Int,
    val versionName: String,
    val hostSpecies: HostSpecies
)

enum class HostSpecies {
    QQ,
    TIM,
    TCQT,
    UNKNOWN
}
