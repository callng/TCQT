package com.owo233.tcqt.core.action

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Environment
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HostBridge
import com.owo233.tcqt.core.env.HybridClassLoader
import com.owo233.tcqt.core.env.PlatformTools
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.env.ResourcesUtils
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.env.resolveDeviceName
import com.owo233.tcqt.core.log.Log
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

        HostBridge.notifyHostApplicationReady(app)
        ResourcesUtils.injectResourcesToContext(app.resources)
    }

    /**
     * 解析当前进程对应的 [ActionProcess]。
     */
    fun resolveActionProcess(): ActionProcess = when {
        ProcUtil.isMain -> ActionProcess.MAIN
        ProcUtil.isMSF -> ActionProcess.MSF
        ProcUtil.isTool -> ActionProcess.TOOL
        ProcUtil.isOpenSdk -> ActionProcess.OPENSDK
        ProcUtil.isQzone -> ActionProcess.QZONE
        ProcUtil.isQQFav -> ActionProcess.QQFAV
        else -> ActionProcess.OTHER
    }

    /**
     * 启动入口（在 `BaseApplicationImpl.onCreate` 的 Before 回调中调用）。
     *
     * 只**同步**执行 [com.owo233.tcqt.core.action.ActionPriority.CRITICAL] 优先级的功能，其余由
     * [StartupScheduler] 在 onCreate 返回后分批在后台线程安装。
     *
     * @return 本次启动的执行计划，调用方需交给 [StartupScheduler.schedule]
     */
    fun initStartup(
        app: Application,
        proc: ActionProcess,
        missingDexKitKeys: Set<String>? = null
    ): ActionPlan {
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

        val plan = ActionRegistry.buildPlan(proc, missingDexKitKeys)
        ActionRegistry.runCritical(app, proc, plan)
        return plan
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
        val quaVersion = runCatching {
            QUA.getQUA3().split("_").getOrNull(4)?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)

        val contextVersion = PackageInfoCompat.getLongVersionCode(packageInfo)

        return maxOf(contextVersion, quaVersion)
    }
}
