package com.owo233.tcqt.loader.zygisk

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.annotation.Keep
import com.owo233.tcqt.loader.ModuleLoader
import com.owo233.tcqt.loader.api.HookEngineManager
import com.owo233.tcqt.utils.hook.hookAfter
import java.io.File
import java.util.zip.ZipFile

@Keep
object ZygiskEntry {

    private const val TAG = "ZygiskEntry"
    private const val QQ_PACKAGE = "com.tencent.mobileqq"
    private const val TIM_PACKAGE = "com.tencent.tim"

    @JvmStatic
    private external fun nativeArtInit(): Boolean

    @JvmStatic
    @Keep
    fun init(processName: String, dataDir: String, apkPath: String) {
        val pkg = processName.substringBefore(':')
        if (pkg != QQ_PACKAGE && pkg != TIM_PACKAGE) return

        try {
            // 1. 加载模块自带的 native 库（dexkit 需要，注入进程无法 System.loadLibrary）。
            loadNativeLibs(apkPath, dataDir)

            // 2. 初始化 ART hook 引擎（布局探测 + 符号解析 + trampoline 池）。
            if (!nativeArtInit()) {
                Log.e(TAG, "nativeArtInit failed, abort")
                return
            }

            if (HookEngineManager.isInitialized) return
            HookEngineManager.engine = ZygiskHookEngine()

            // 3. 等待宿主 ClassLoader 就绪后启动模块。
            installHostBootstrap(pkg, apkPath, processName)
            Log.i(TAG, "ZygiskEntry.init: $processName bootstrap installed (apk=$apkPath)")
        } catch (t: Throwable) {
            Log.e(TAG, "ZygiskEntry.init failed", t)
        }
    }

    /** 从注入 APK 的 lib/arm64-v8a 解压并 System.load 所有 so。 */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadNativeLibs(apkPath: String, dataDir: String) {
        val outDir = File(dataDir, "files/.tcqt").apply { mkdirs() }
        runCatching {
            ZipFile(apkPath).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("lib/arm64-v8a/") && it.name.endsWith(".so") }
                    .forEach { entry ->
                        val outFile = File(outDir, entry.name.substringAfterLast('/'))

                        if (!outFile.exists() || outFile.length() != entry.size) {
                            val tmp = File(outDir, outFile.name + ".tmp")
                            zip.getInputStream(entry).use { input ->
                                tmp.outputStream().use { output -> input.copyTo(output) }
                            }
                            if (outFile.exists() && !outFile.delete()) {
                                tmp.delete()
                                error("failed to remove stale ${outFile.name}")
                            }
                            if (!tmp.renameTo(outFile)) {
                                tmp.delete()
                                error("failed to replace ${outFile.name}")
                            }
                        }
                        runCatching { System.load(outFile.absolutePath) }.onFailure {
                            Log.e(TAG, "failed to load ${outFile.name}", it)
                        }
                    }
            }
        }.onFailure {
            Log.e(TAG, "failed to extract native libs from $apkPath", it)
        }
    }

    @SuppressLint("SoonBlockedPrivateApi", "PrivateApi")
    private fun installHostBootstrap(pkg: String, apkPath: String, processName: String) {
        val loadedApkClass = Class.forName("android.app.LoadedApk")
        val createAppFactory = loadedApkClass.getDeclaredMethod(
            "createAppFactory", ApplicationInfo::class.java, ClassLoader::class.java
        )
        createAppFactory.hookAfter { param ->
            if (param.throwable != null) return@hookAfter
            val appInfo = param.args.getOrNull(0) as? ApplicationInfo ?: return@hookAfter
            if (appInfo.packageName != pkg) return@hookAfter
            val factory = param.result ?: return@hookAfter

            val instantiate = factory.javaClass.getMethod(
                "instantiateClassLoader", ClassLoader::class.java, ApplicationInfo::class.java
            )
            instantiate.hookAfter { p2 ->
                if (p2.throwable != null) return@hookAfter
                val hostLoader = p2.result as? ClassLoader ?: return@hookAfter
                runCatching {
                    ModuleLoader.initialize(hostLoader, apkPath, pkg, processName)
                }.onFailure {
                    Log.e(TAG, "ModuleLoader.initialize failed", it)
                }
            }
        }
    }
}
