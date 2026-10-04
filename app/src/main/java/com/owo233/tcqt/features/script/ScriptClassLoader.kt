package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.env.HookEnv

/**
 * 脚本的宿主类加载器链：优先宿主（QQ/TIM）与模块自身，最后回落到父加载器。
 *
 * BeanShell 解释器通过 `setClassLoader` 使用它，因此脚本里可以直接
 * `import com.tencent.mobileqq.*`。
 */
internal class ScriptClassLoader(parent: ClassLoader?) : ClassLoader(parent) {

    private val delegates: MutableList<ClassLoader> = buildList {
        add(HookEnv.moduleClassLoader)
        add(HookEnv.hostClassLoader)
        parent?.let { add(it) }
    }.distinct().toMutableList()

    /** 追加脚本自己加载的类库（`loadJar`）。 */
    fun addClassLoader(loader: ClassLoader) {
        if (loader !in delegates) delegates.add(loader)
    }

    override fun findClass(name: String): Class<*> {
        delegates.forEach { loader ->
            runCatching { return loader.loadClass(name) }
        }
        throw ClassNotFoundException(name)
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // 先按双亲委派拿框架 / 模块类，再走宿主加载器链。
        runCatching { return super.loadClass(name, resolve) }
        val clazz = findClass(name)
        if (resolve) resolveClass(clazz)
        return clazz
    }
}
