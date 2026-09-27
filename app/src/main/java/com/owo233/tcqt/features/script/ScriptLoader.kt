package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.LogUtils
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 脚本外部类库加载：把 dex / jar / apk 交给 [DexClassLoader]，脚本随后可 `import`。
 *
 * Android 只能加载 dex 格式，因此传进来的 jar 必须已经 dex 化（或直接传 apk）。
 * 加载过的加载器缓存在脚本缓存目录，重复调用不会累积。
 */
internal object ScriptLoader {

    private val cacheDir: File by lazy {
        File(HookEnv.moduleDataPath, "script/.cache/dex").apply { mkdirs() }
    }

    fun loadJar(path: String): ClassLoader = load(path, "jar")

    fun loadDex(path: String): ClassLoader = load(path, "dex")

    private fun load(path: String, kind: String): ClassLoader {
        val file = File(path)
        require(file.exists()) { "文件不存在: $path" }

        val optimized = File(cacheDir, kind).apply { mkdirs() }
        return DexClassLoader(file.absolutePath, optimized.absolutePath, null, HookEnv.hostClassLoader)
    }
}

/** 兼容旧名字：脚本层不再直接引用它，保留给可能的外部调用。 */
@Deprecated("Use ScriptLoader", ReplaceWith("ScriptLoader"))
internal object ScriptJarLoader {

    fun load(path: String, parent: ClassLoader = HookEnv.hostClassLoader): ClassLoader {
        LogUtils.androidNoFilter.w("ScriptJarLoader 已废弃，改用 ScriptLoader")
        return ScriptLoader.loadJar(path)
    }
}
