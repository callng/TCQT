package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.env.HookEnv
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 脚本外部库加载：把 jar / apk 交给 [DexClassLoader]，脚本随后可直接 `import` 其中的类。
 *
 * Android 只能加载 dex 格式，因此传入的 jar 必须是已 dex 化的（或直接传 apk）。
 */
internal object ScriptJarLoader {

    private val cacheDir: File by lazy {
        File(HookEnv.moduleDataPath, "script/.cache").apply { mkdirs() }
    }

    fun load(path: String, parent: ClassLoader = HookEnv.hostClassLoader): ClassLoader {
        val file = File(path)
        require(file.exists()) { "文件不存在: $path" }
        return DexClassLoader(file.absolutePath, cacheDir.absolutePath, null, parent)
    }
}
