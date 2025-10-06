package com.owo233.tcqt.hooks

import android.content.Context
import android.os.Build
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.getObjectField
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.paramCount
import com.owo233.tcqt.utils.setObjectField
import com.tencent.qqnt.kernel.nativeinterface.FileElement
import java.io.File

@RegisterAction
@RegisterSetting(
    key = "rename_base_apk",
    name = "重命名.apk文件",
    type = SettingType.BOOLEAN,
    desc = "发送文件时自动重命名 .apk 文件，防止添加.1。",
    uiOrder = 24
)
class RenameBaseApk : IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        removeSuffix()
        renameGroupUploadApk(ctx)
        renameFriendUploadApk(ctx)
    }

    private fun removeSuffix() {
        FileElement::class.java.hookBeforeMethod("getFileName") {
            val fileName = it.thisObject.getObjectField("fileName") as String
            if (fileName.endsWith(".1")) {
                it.thisObject.setObjectField(
                    "fileName",
                    fileName.substring(0, fileName.length - 2)
                )
            }
        }
    }

    private fun renameGroupUploadApk(ctx: Context) {
        val clz = XpClassLoader.load("com.tencent.mobileqq.troop.filemanager.TroopFileTransferMgr")
            ?: error("renameGroupUploadApk: 找不到TroopFileTransferMgr类!!!")
        val method = clz.declaredMethods.firstOrNull {
            it.returnType == Void.TYPE && it.paramCount == 2 &&
                    it.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    it.parameterTypes[1].name.contains("TroopFileTransferManager\$Item")
        } ?: error("renameGroupUploadApk: 没有找到合适的方法!!!")

        method.hookBeforeMethod { param ->
            val item = param.args[1]
            val fileName = item.getObjectField("FileName") as String
            val localFile = item.getObjectField("LocalFile") as String

            if (!fileName.endsWith(".apk")) return@hookBeforeMethod
            File(localFile).also {
                if (!it.exists()) {
                    Log.e("renameGroupUploadApk: File not exists: $localFile")
                    return@hookBeforeMethod
                }
            }

            val newFileName = getFormattedFileNameByPath(ctx, localFile)
            item.setObjectField("FileName", newFileName)
        }
    }

    private fun renameFriendUploadApk(ctx: Context) {
        val clz = XpClassLoader.load("com.tencent.mobileqq.filemanager.nt.NTFileManageBridger")
            ?: error("renameFriendUploadApk: 找不到NTFileManageBridger类!!!")
        val method = clz.declaredMethods.firstOrNull {
            it.returnType == Void.TYPE && it.paramCount == 4 &&
                    it.parameterTypes[0].name.contains("FileManagerEntity") &&
                    it.parameterTypes[1] == Runnable::class.java &&
                    it.parameterTypes[2] == String::class.java &&
                    it.parameterTypes[3] == String::class.java
        } ?: error("renameFriendUploadApk: 没有找到合适方法!!!")

        method.hookBeforeMethod { param ->
            val fileManagerEntity = param.args[0]
            val fileName = fileManagerEntity.getObjectField("fileName") as String
            val localFile = fileManagerEntity.getObjectField("strFilePath") as String

            if (!fileName.endsWith(".apk")) return@hookBeforeMethod
            File(localFile).also {
                if (!it.exists()) {
                    Log.e("renameFriendUploadApk: File not exists: $localFile")
                    return@hookBeforeMethod
                }
            }

            val newFileName = getFormattedFileNameByPath(ctx, localFile)
            param.args[2] = newFileName
        }
    }

    private fun getFormattedFileNameByPath(
        ctx: Context,
        apkPath: String,
        format: String = "%n_%v(%c).APK"
    ): String {
        val pm = ctx.packageManager

        val pkgInfo = pm.getPackageArchiveInfo(apkPath, 0)
            ?: throw IllegalArgumentException("无法解析 APK：$apkPath")

        val appInfo = pkgInfo.applicationInfo
            ?: throw IllegalStateException("APK 中未包含 ApplicationInfo")

        appInfo.sourceDir = apkPath
        appInfo.publicSourceDir = apkPath

        val appName = pm.getApplicationLabel(appInfo).toString()
        val versionName = pkgInfo.versionName ?: "114514.null"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        return format
            .replace("%n", appName)
            .replace("%p", appInfo.packageName)
            .replace("%v", versionName)
            .replace("%c", versionCode.toString())
    }

    override val key: String get() = GeneratedSettingList.RENAME_BASE_APK

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)
}
