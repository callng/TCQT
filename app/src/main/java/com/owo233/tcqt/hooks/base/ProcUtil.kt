package com.owo233.tcqt.hooks.base

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.ext.runRetry
import java.io.File

object ProcUtil {
    const val UNKNOW = 0
    const val MAIN = 1
    const val MSF = 1 shl 1
    const val PEAK = 1 shl 2
    const val TOOL = 1 shl 3
    const val QZONE = 1 shl 4
    const val VIDEO = 1 shl 5
    const val MINI = 1 shl 6
    const val PLUGIN = 1 shl 7
    const val QQFAV = 1 shl 8
    const val TROOP = 1 shl 9
    const val UNITY = 1 shl 10
    const val OPENSDK = 1 shl 11
    const val OTHER = 1 shl 31

    val mPid: Int by lazy { Process.myPid() }

    val procName: String by lazy {
        val nameFromFile = getProcessNameFromFile()
        if (nameFromFile != "unknown") {
            return@lazy nameFromFile
        }

        val activityManager = HookEnv.application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        runRetry(3) {
            activityManager.runningAppProcesses?.forEach {
                if (it.pid == mPid) {
                    return@runRetry it.processName
                }
            }
            null
        } ?: "unknown"
    }

    val procSuffix: String by lazy {
        val parts = procName.split(":")
        if (parts.size == 1) {
            return@lazy "MAIN"
        } else {
            return@lazy parts.last().uppercase()
        }
    }

    val procType: Int by lazy {
        val parts = procName.split(":")
        if (parts.size == 1) {
            if (procName == "com.tencent.ilink.ServiceProcess") {
                return@lazy OTHER
            } else if (parts.last() == "unknown") {
                return@lazy UNKNOW
            } else return@lazy MAIN
        }

        val tail = parts.last()
        return@lazy when {
            tail == "MSF" -> MSF
            tail == "peak" -> PEAK
            tail == "tool" -> TOOL
            tail.startsWith("qzone") -> QZONE
            tail == "video" -> VIDEO
            tail.startsWith("mini") -> MINI
            tail.startsWith("plugin") -> PLUGIN
            tail.startsWith("troop") -> TROOP
            tail.startsWith("unity") -> UNITY
            tail.startsWith("qqfav") -> QQFAV
            tail == "openSdk" -> OPENSDK
            else -> OTHER
        }
    }

    fun inProcess(flag: Int): Boolean = (procType and flag) != 0

    private fun getProcessNameFromFile(): String {
        return runCatching {
            File("/proc/self/cmdline").inputStream().use { input ->
                val b = ByteArray(256)
                val len = input.read(b)
                if (len > 0) {
                    var end = 0
                    while (end < len && b[end].toInt() != 0) {
                        end++
                    }
                    String(b, 0, end, Charsets.UTF_8).trim()
                } else {
                    "unknown"
                }
            }
        }.getOrElse { "unknown" }
    }

    val isMain get() = inProcess(MAIN)
    val isMsf get() = inProcess(MSF)
    val isPeak get() = inProcess(PEAK)
    val isTool get() = inProcess(TOOL)
    val isQzone get() = inProcess(QZONE)
    val isVideo get() = inProcess(VIDEO)
    val isMini get() = inProcess(MINI)
    val isPlugin get() = inProcess(PLUGIN)
    val isQQFav get() = inProcess(QQFAV)
    val isTroop get() = inProcess(TROOP)
    val isUnity get() = inProcess(UNITY)
    val isOpenSdk get() = inProcess(OPENSDK)
}
