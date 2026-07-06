package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.hooks.helper.OnAIOSendMsgBefore
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.proto2json.ProtoUtils
import com.owo233.tcqt.utils.proto2json.asUtf8String
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.getObject
import com.owo233.tcqt.utils.reflect.setObject
import com.tencent.qqnt.kernel.nativeinterface.FileElement
import com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession
import com.tencent.qqnt.kernel.nativeinterface.MsfRspInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import java.io.File

@RegisterAction
class RenameBaseApk : IAction, OnAIOSendMsgBefore {

    override val name: String get() = "重命名.apk文件"
    override val desc: String get() = "发送文件时自动重命名 .apk 文件，防止添加.1。"
    override val uiTab: String get() = "基础"
    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                "rename_base_apk.type",
                "可选项",
                1,
                "",
                listOf("更新文件名前缀", "忽略后缀大小写")
            ),
        )

    private val options: Int by lazy {
        TCQTSetting.getInt("rename_base_apk.type")
    }

    override val key: String get() = "rename_base_apk"

    override fun onRun(app: Application, process: ActionProcess) {
        hookC2CSendFile()
        hookFile()
    }

    override fun onSend(elements: ArrayList<MsgElement>) {
        elements.mapNotNull { it.fileElement }
            .filter { it.fileName?.endsWith(".apk", options.isFlagEnabled(1)) == true }
            .forEach { fileElement ->
                val filePath = fileElement.filePath

                if (!filePath.isNullOrEmpty() && File(filePath).exists()) {
                    fileElement.fileName = if (options.isFlagEnabled(0)) {
                        getFormattedFileNameByPath(filePath)
                    } else {
                        replaceApkExtension(fileElement.fileName)
                    }
                }
            }
    }

    private fun hookC2CSendFile() {
        IQQNTWrapperSession.CppProxy::class.java.findMethod {
            name = "onSendSSOReply"
            paramTypes(long, string, int, string, MsfRspInfo::class.java)
        }.hookBefore { param ->
            val cmd = param.args[1] as String
            val result = param.args[2] as Int
            if (cmd == "OidbSvcTrpcTcp.0xe37_800" && result == 0) {
                val msfRspInfo = param.args[4] as MsfRspInfo
                try {
                    val protoMap = ProtoUtils.decodeFromByteArray(msfRspInfo.pbBuffer)
                    if (protoMap.has(4, 10, 40, 1, 5)) {
                        val oldFileName = protoMap[4, 10, 40, 1, 5].asUtf8String
                        val newFileName = fixSuffix(oldFileName)
                        if (newFileName != oldFileName) {
                            protoMap[4, 10, 40, 1, 5] = newFileName
                            msfRspInfo.pbBuffer = ProtoUtils.encodeToByteArray(protoMap)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("RenameBaseApk hookC2CSendFile error", e)
                }
            }
        }
    }

    private fun fixSuffix(str: String): String {
        val base = str.removeSuffix(".1")
        return if (base.endsWith(".apk", ignoreCase = true)) {
            base.dropLast(4) + ".APK"
        } else {
            base
        }
    }

    private fun hookFile() {
        FileElement::class.java.hookMethodBefore("getFileName") { param ->
            val fileName = param.thisObject.getObject("fileName") as String
            if (fileName.endsWith(".1")) {
                param.thisObject.setObject(
                    "fileName",
                    fileName.substring(0, fileName.length - 2)
                )
            }
        }
    }

    private fun replaceApkExtension(input: String): String {
        return if (input.endsWith(".apk")) {
            input.substring(0, input.length - 4) + ".APK"
        } else {
            input
        }
    }

    private fun getFormattedFileNameByPath(apkPath: String): String {
        try {
            val packageManager: PackageManager = HookEnv.hostAppContext.packageManager
            val packageArchiveInfo =
                packageManager.getPackageArchiveInfo(apkPath, 1)

            val applicationInfo = packageArchiveInfo!!.applicationInfo
            applicationInfo!!.sourceDir = apkPath
            applicationInfo.publicSourceDir = apkPath

            val currentBaseApkFormat = "%n_%v.APK"

            @Suppress("DEPRECATION")
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageArchiveInfo.longVersionCode.toString()
            } else {
                packageArchiveInfo.versionCode.toString()
            }

            return currentBaseApkFormat.replace(
                "%n",
                applicationInfo.loadLabel(packageManager).toString()
            ).replace("%p", applicationInfo.packageName).replace(
                "%v",
                packageArchiveInfo.versionName!!
            ).replace("%c", vCode)
        } catch (_: Exception) {
            return "base.APK"
        }
    }
}
