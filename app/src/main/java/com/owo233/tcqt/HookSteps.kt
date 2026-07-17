package com.owo233.tcqt

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Environment
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.resolveDeviceName
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.lifecycle.ParasiticActivity
import com.owo233.tcqt.loader.HybridClassLoader
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.log.Log
import cooperation.qzone.QUA
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

internal object HookSteps {

    fun initHandleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        HookEnv.setProcessName(loadPackageParam.processName)
        HookEnv.setHostAppPackageName(loadPackageParam.packageName)
    }

    fun initHandleLoadPackage(processName: String, packageName: String) {
        HookEnv.setProcessName(processName)
        HookEnv.setHostAppPackageName(packageName)
    }

    fun initModulePath(path: String) {
        HookEnv.setModuleApkPath(path)
    }

    @SuppressLint("SdCardPath")
    fun initContext(app: Application) {
        val appName = TCQTBuild.APP_NAME
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)

        val oldDirPath = app.getExternalFilesDir(null)?.parentFile?.let {
            "${it.absolutePath}/$appName"
        } ?: "${Environment.getExternalStorageDirectory().absolutePath}/Android/data/${app.packageName}/$appName"

        val newDirPath = (app.filesDir?.let { File(it, "5463306EE50FE3AA/$appName") }
            ?: File("/data/user/${Process.myUserHandle().hashCode()}/${app.packageName}/files/5463306EE50FE3AA/$appName"))
            .also { it.mkdirs() }
            .absolutePath

        // 将在后续的几个版本更新中移除迁移逻辑
        if (ProcUtil.isMain) {
            File(oldDirPath).takeIf { it.isDirectory }?.let { oldDir ->
                val newDir = File(newDirPath)
                if (!oldDir.renameTo(newDir)) {
                    oldDir.copyRecursively(newDir, overwrite = true)
                    oldDir.deleteRecursively()
                }
            }
        }

        HookEnv.apply {
            setModuleDataPath(newDirPath)
            setHostAppContext(app)
            setApplication(app)
            setHostApkPath(app.applicationInfo.sourceDir)
            setAppName(app.packageManager.getApplicationLabel(app.applicationInfo).toString())
            setVersionCode(getRealVersionCode(packageInfo))
            setVersionName(packageInfo.versionName ?: "unknown")
        }

        ParasiticActivity.initForStubActivity(app)
        ResourcesUtils.injectResourcesToContext(app.resources)
    }

    fun initHooks(app: Application, missingDexKitKeys: Set<String>? = null) {
        if (ProcUtil.isMain) {
            Log.i(
                """

                    安卓版本: ${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT})
                    系统指纹: ${Build.FINGERPRINT}
                    设备名称: ${resolveDeviceName()}
                    模块版本: ${TCQTBuild.VER_NAME}(${TCQTBuild.VER_CODE}) ${if (TCQTBuild.DEBUG) "debug" else "release"}
                    宿主版本: ${HookEnv.versionName}(${HookEnv.versionCode}) ${PlatformTools.getHostChannel()}

                """.trimIndent()
            )
        }

        ActionManager.runFirst(
            app,
            when {
                ProcUtil.isMain -> ActionProcess.MAIN
                ProcUtil.isMSF -> ActionProcess.MSF
                ProcUtil.isTool -> ActionProcess.TOOL
                ProcUtil.isOpenSdk -> ActionProcess.OPENSDK
                ProcUtil.isQzone -> ActionProcess.QZONE
                ProcUtil.isQQFav -> ActionProcess.QQFAV
                else -> ActionProcess.OTHER
            },
            missingDexKitKeys
        )
    }

    @SuppressLint("DiscouragedPrivateApi")
    fun injectClassLoader(loader: ClassLoader) {
        runCatching {
            val self = HookEnv::class.java.classLoader
            HybridClassLoader.setHostClassLoader(loader)
            HybridClassLoader.inject(self!!)
        }.onFailure {
            Log.e("injectClassLoader failed", it)
        }
    }

    private fun getRealVersionCode(packageInfo: PackageInfo): Long {
        val quaVersion = QUA.getQUA3()
            .split("_")
            .getOrNull(4)
            ?.toLongOrNull() ?: 0L

        val contextVersion = PackageInfoCompat.getLongVersionCode(packageInfo)

        return maxOf(contextVersion, quaVersion)
    }
}
