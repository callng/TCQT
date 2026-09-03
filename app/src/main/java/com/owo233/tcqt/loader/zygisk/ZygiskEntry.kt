package com.owo233.tcqt.loader.zygisk

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.loader.InjectionGuard
import com.owo233.tcqt.loader.ModuleLoader
import com.owo233.tcqt.loader.api.HookEngineManager
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@Keep
object ZygiskEntry {

    private const val TAG = "ZygiskEntry"
    private const val QQ_PACKAGE = "com.tencent.mobileqq"
    private const val TIM_PACKAGE = "com.tencent.tim"

    @JvmStatic
    private external fun nativeArtInit(): Boolean

    @JvmStatic
    private external fun nativeLog(tag: String, msg: String)

    @JvmStatic
    external fun isCompatMode(): Boolean

    @JvmStatic
    external fun nativeIsCompatMode(): Boolean

    @JvmStatic
    @Keep
    fun init(processName: String, dataDir: String, apkPath: String) {
        val pkg = processName.substringBefore(':')
        if (pkg != QQ_PACKAGE && pkg != TIM_PACKAGE) return

        if (!InjectionGuard.tryAcquire(InjectionGuard.MODE_ZYGISK)) {
            nativeLog(TAG, "init blocked: ${InjectionGuard.activeMode()} already active")
            Log.w(TAG, "Zygisk init blocked, another injection mode is already active")
            return
        }

        try {
            nativeLog(TAG, "init: $processName (zygisk mode claimed, compat=${isCompatMode()})")

            val nativeDir = prepareNativeLibs(apkPath, dataDir)
            ZygiskNativeLibs.register(nativeDir)

            maybePreloadGraphicsPath()

            // 初始化 ART hook 引擎（布局探测 + 符号解析 + trampoline 池）
            if (!nativeArtInit()) {
                Log.e(TAG, "nativeArtInit failed, abort")
                nativeLog(TAG, "nativeArtInit failed, abort")
                return
            }

            if (HookEngineManager.isInitialized) return
            HookEngineManager.engine = ZygiskHookEngine()

            // 等待宿主 ClassLoader 就绪后启动模块
            installHostBootstrap(pkg, apkPath, processName)
            Log.i(TAG, "ZygiskEntry.init: $processName bootstrap installed (apk=$apkPath)")
            nativeLog(TAG, "init done: $processName")
        } catch (t: Throwable) {
            Log.e(TAG, "ZygiskEntry.init failed", t)
            nativeLog(TAG, "init failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun prepareNativeLibs(apkPath: String, dataDir: String): File {
        val outDir = File(dataDir, "files/.tcqt").apply {
            if (!exists() && !mkdirs()) {
                error("failed to create native library directory: $absolutePath")
            }

            if (!isDirectory) {
                error("native library path is not a directory: $absolutePath")
            }
        }

        val apkFingerprint = readTrimmed(File(outDir, "main.apk.sha256"))
        val libsFingerprint = readTrimmed(File(outDir, "libs.sha256"))
        val libsUpToDate = apkFingerprint != null && apkFingerprint == libsFingerprint

        ZipFile(apkPath).use { zip ->
            val soEntries = zip.entries().asSequence()
                .filter { entry ->
                    !entry.isDirectory &&
                            entry.name.startsWith("lib/arm64-v8a/") &&
                            entry.name.endsWith(".so") &&
                            !entry.name.endsWith("/libtcqtzygisk.so")
                }
                .toList()

            var allOk = true
            soEntries.forEach { entry ->
                val fileName = entry.name.substringAfterLast('/')

                // 防止 ZIP entry 中出现奇怪的路径
                if (fileName.isEmpty() || fileName == "." || fileName == "..") {
                    Log.w(TAG, "skip invalid native library entry: ${entry.name}")
                    return@forEach
                }

                val outFile = File(outDir, fileName)

                try {
                    if (!libsUpToDate || !outFile.isFile) {
                        extractNativeLibrary(zip, entry, outFile)

                        // Android 17+:
                        // native libraries loaded through System.load() must be read-only.
                        ensureReadOnly(outFile)
                    }
                } catch (t: Throwable) {
                    allOk = false
                    Log.e(
                        TAG,
                        "failed to prepare native library: ${outFile.name}",
                        t
                    )
                }
            }

            if (!libsUpToDate) {
                if (allOk && apkFingerprint != null) {
                    File(outDir, "libs.sha256").writeText(apkFingerprint)
                }

                val wanted = soEntries.mapTo(mutableSetOf()) { it.name.substringAfterLast('/') }
                outDir.listFiles()?.forEach { f ->
                    if (f.isFile && f.extension == "so" && f.name !in wanted) {
                        f.delete()
                    }
                }
            }
        }

        return outDir
    }

    private fun maybePreloadGraphicsPath() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (ProcUtil.isMain) ZygiskNativeLibs.load("androidx.graphics.path")
    }

    private fun readTrimmed(file: File): String? =
        if (file.isFile) file.readText().trim().takeIf { it.isNotEmpty() } else null

    private fun extractNativeLibrary(
        zip: ZipFile,
        entry: ZipEntry,
        outFile: File
    ) {
        if (outFile.isFile && outFile.length() == entry.size) {
            return
        }

        val tmpFile = File(
            outFile.parentFile,
            "${outFile.name}.${Process.myPid()}.tmp"
        )

        try {
            tmpFile.delete()

            zip.getInputStream(entry).use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            if (entry.size >= 0 && tmpFile.length() != entry.size) {
                error(
                    "extracted size mismatch for ${entry.name}: " +
                            "expected=${entry.size}, actual=${tmpFile.length()}"
                )
            }

            if (outFile.exists() && !outFile.delete()) {
                error("failed to remove stale native library: ${outFile.absolutePath}")
            }

            if (!tmpFile.renameTo(outFile)) {
                error(
                    "failed to replace native library: ${outFile.absolutePath}"
                )
            }
        } finally {
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
        }
    }

    private fun ensureReadOnly(file: File) {
        check(file.isFile) {
            "native library does not exist: ${file.absolutePath}"
        }

        check(file.setReadOnly()) {
            "failed to make native library read-only: ${file.absolutePath}"
        }

        check(!file.canWrite()) {
            "native library is still writable: ${file.absolutePath}"
        }
    }

    @SuppressLint("SoonBlockedPrivateApi", "PrivateApi")
    private fun installHostBootstrap(pkg: String, apkPath: String, processName: String) {
        if (!isCompatMode()) {
            installInNormalMode(pkg, apkPath, processName)
        } else {
            installInCompatMode(pkg, apkPath, processName)
        }
    }

    @SuppressLint("PrivateApi", "SoonBlockedPrivateApi")
    private fun installInNormalMode(pkg: String, apkPath: String, processName: String) {
        val loadedApkClass = Class.forName("android.app.LoadedApk")
        val createAppFactory = loadedApkClass.getDeclaredMethod(
            "createAppFactory",
            ApplicationInfo::class.java,
            ClassLoader::class.java
        )
        createAppFactory.hookAfter { param ->
            if (param.throwable != null) return@hookAfter
            val appInfo = param.args.getOrNull(0) as? ApplicationInfo ?: return@hookAfter
            if (appInfo.packageName != pkg) return@hookAfter
            val factory = param.result ?: return@hookAfter

            val instantiate = factory.javaClass.getMethod(
                "instantiateClassLoader",
                ClassLoader::class.java,
                ApplicationInfo::class.java
            )
            instantiate.hookAfter { p2 ->
                if (p2.throwable != null) return@hookAfter
                val hostLoader = p2.result as? ClassLoader ?: return@hookAfter
                ModuleLoader.initialize(
                    hostLoader,
                    apkPath,
                    pkg,
                    processName
                )
            }
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun installInCompatMode(pkg: String, apkPath: String, processName: String) {
        Instrumentation::class.java.getDeclaredMethod(
            "callApplicationOnCreate",
            Application::class.java
        ).hookBefore { param ->
            if (param.throwable != null) return@hookBefore
            val app = param.args.getOrNull(0) as? Application ?: return@hookBefore
            val hostLoader = app.baseContext?.classLoader ?: app.classLoader ?: return@hookBefore
            if (hostLoader === javaClass.classLoader) return@hookBefore

            ModuleLoader.reload(
                mapOf(
                    "moduleApkPath" to apkPath,
                    "hostProcessName" to processName,
                    "hostAppPackageName" to pkg,
                    "hostClassLoader" to hostLoader,
                    "hostApplication" to app
                )
            )
        }
    }
}
