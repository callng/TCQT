package com.owo233.tcqt.hooks.func.basic

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.reflect.getObject
import com.owo233.tcqt.utils.reflect.setObject
import com.tencent.qqnt.kernel.nativeinterface.FileElement

@RegisterAction
@RegisterSetting(
    key = "rename_base_apk",
    name = "重命名.apk文件",
    type = SettingType.BOOLEAN,
    desc = "发送文件时自动重命名 .apk 文件，防止添加.1。",
)
class RenameBaseApk : IAction {

    override val key: String get() = GeneratedSettingList.RENAME_BASE_APK

    override fun onRun(ctx: Context, process: ActionProcess) {
        hookC2C()
        hookTroop()
        hookFile()
    }

    private fun hookC2C() {
        val clazz = loadOrThrow("com.tencent.mobileqq.filemanager.nt.NTFileManageBridger")
        val method = clazz.declaredMethods.first { m ->
            val types = m.parameterTypes
            m.returnType == Void.TYPE &&
                    types.any { it.name.contains("FileManagerEntity") } &&
                    types.any { it == Runnable::class.java } &&
                    types.any { it == String::class.java }
        }

        val entityIdx = method.parameterTypes.indexOfFirst { it.name.contains("FileManagerEntity") }
        val nameIdx = method.parameterTypes.indexOfFirst { it == String::class.java }

        method.hookBefore { param ->
            val fileManagerEntity = param.args[entityIdx]!!
            val fileName = fileManagerEntity.getObject("fileName") as String
            val localPath = fileManagerEntity.getObject("strFilePath") as String
            val meetHitConditions = meetHitConditions(fileName, localPath)
            if (meetHitConditions) {
                getFormattedFileNameByPath(localPath).also {
                    fileManagerEntity.setObject("fileName", it)
                    param.args[nameIdx] = it
                }
            }
        }
    }

    private fun hookTroop() {
        val clazz = loadOrThrow("com.tencent.mobileqq.troop.filemanager.TroopFileTransferMgr")
        val method = clazz.declaredMethods.first { m ->
            m.returnType == Void.TYPE &&
                    m.parameterTypes.size in 1..2 &&
                    m.parameterTypes.any { it.name.contains("Item") }
        }

        method.hookBefore { param ->
            val item = param.args.first { it!!.javaClass.name.contains("Item") }!!
            val fileName = item.getObject("FileName") as String
            val localPath = item.getObject("LocalFile") as String
            if (meetHitConditions(fileName, localPath)) {
                getFormattedFileNameByPath(localPath).also {
                    item.setObject("FileName", it)
                }
            }
        }
    }

    private fun hookFile() {
        FileElement::class.java.hookMethodBefore("getFileName") { param ->
            val fileName = param.thisObject.getObject("fileName") as String
            if (fileName.endsWith(".1")) {
                param.thisObject.setObject("fileName", fileName.substringBeforeLast(".1"))
            }
        }
    }

    private fun meetHitConditions(fileName: String, filePath: String): Boolean {
        if (fileName.matches("^base(\\([0-9]+\\))?.apk$".toRegex())) {
            return true
        }

        // 后缀匹配命中
        val index = fileName.lastIndexOf(".")
        if (index == -1) return false

        val fileExtension = fileName.substring(index)
        // 不区分大小写的匹配.apk
        if (fileExtension.equals(".apk", ignoreCase = true)) {
            // 无后缀文件名 去除.apk 这四个字
            val fileNameWithoutSuffix = fileName.substring(0, index)
            val applicationInfo = getAppInfoByFilePath(filePath) ?: return false

            // 包名命中
            if (fileNameWithoutSuffix == applicationInfo.packageName) return true

            // 应用主app context命中
            if (fileNameWithoutSuffix == applicationInfo.name) return true

            // 应用名命中
            if (fileNameWithoutSuffix == applicationInfo.loadLabel(
                    HookEnv.hostAppContext.packageManager
            ).toString()) return true
        }

        return false
    }

    private fun getAppInfoByFilePath(filePath: String?): ApplicationInfo? {
        try {
            val packageManager: PackageManager = HookEnv.hostAppContext.packageManager
            val packageArchiveInfo = packageManager.getPackageArchiveInfo(
                filePath!!,
                1
            )
            return packageArchiveInfo!!.applicationInfo
        } catch (_: Exception) {
            return null
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
