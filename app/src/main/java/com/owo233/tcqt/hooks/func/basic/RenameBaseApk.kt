package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.helper.OnAIOSendMsgBefore
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.reflect.getObject
import com.owo233.tcqt.utils.reflect.setObject
import com.tencent.qqnt.kernel.nativeinterface.FileElement
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import java.io.File

@RegisterAction
@RegisterSetting(
    key = "rename_base_apk",
    name = "重命名.apk文件",
    type = SettingType.BOOLEAN,
    desc = "发送文件时自动重命名 .apk 文件，防止添加.1。",
    uiTab = "基础"
)
@RegisterSetting(
    key = "rename_base_apk.type",
    name = "可选项",
    type = SettingType.INT_MULTI,
    defaultValue = "1", // 默认启用 "更新文件名前缀"
    options = "更新文件名前缀|忽略后缀大小写"
)
class RenameBaseApk : IAction, OnAIOSendMsgBefore {

    private val options: Int by lazy {
        GeneratedSettingList.getInt(GeneratedSettingList.RENAME_BASE_APK_TYPE)
    }

    override val key: String get() = GeneratedSettingList.RENAME_BASE_APK

    override fun onRun(app: Application, process: ActionProcess) {
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
