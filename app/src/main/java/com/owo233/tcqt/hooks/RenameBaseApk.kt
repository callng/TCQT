package com.owo233.tcqt.hooks

import android.content.Context
import android.os.Build
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.FuzzyClassKit
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.getObjectField
import com.owo233.tcqt.utils.logE
import com.owo233.tcqt.utils.setObjectField
import com.tencent.qqnt.kernel.nativeinterface.FileElement
import de.robv.android.xposed.XposedBridge
import java.io.File

@RegisterAction
@RegisterSetting(key = "rename_base_apk", name = "上传群文件时重命名.apk文件", type = SettingType.BOOLEAN)
class RenameBaseApk: IAction {
    override fun onRun(ctx: Context, process: ActionProcess) {
        FuzzyClassKit.findClassByConstructor(
            prefix = "com.tencent.mobileqq.filemanager.uftwrapper.b",
            isSubClass = true
        ) { _, ctor ->
            ctor.parameterCount == 5 &&
                    ctor.parameterTypes[1] == Long::class.java &&
                    ctor.parameterTypes[3] == Bundle::class.java
        }?.let { targetClass ->

            XposedBridge.hookAllConstructors(targetClass, afterHook { param ->
                val thisObj = param.thisObject

                val item = thisObj::class.java.declaredFields.firstOrNull {
                    it.isAccessible = true
                    it.get(thisObj)?.javaClass?.name?.contains("TroopFileTransferManager\$Item") == true
                }?.get(thisObj) ?: return@afterHook

                val superClass = item.javaClass.superclass ?: return@afterHook

                val fileName = superClass.getDeclaredField("FileName").get(item) as String
                if (!fileName.endsWith(".apk")) return@afterHook

                val localFile = superClass.getDeclaredField("LocalFile").get(item) as String
                val file = File(localFile)
                if (!file.exists()) {
                    logE(msg = "file not exists: $localFile")
                    return@afterHook
                }

                val newFileName = getFormattedFileNameByPath(ctx, localFile)
                superClass.getDeclaredField("FileName").set(item, newFileName)
            })
        }

        // 测试.
        FileElement::class.java.hookMethod("getFileName", beforeHook {
            val fileName = it.thisObject.getObjectField("fileName") as String
            if (fileName.endsWith(".1")) {
                it.thisObject.setObjectField(
                    "fileName",
                    fileName.substring(0, fileName.length - 2)
                )
            }
        })
    }

    fun getFormattedFileNameByPath(
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
