package com.owo233.tcqt.loader

/** 判断当前 loader 链是否实际包含补丁代码，不依赖补丁文件现在是否存在。 */
internal object HostCodeMode {

    private val patchDexPath = Regex("[/\\\\]tinker(?:[/\\\\]|[-_])", RegexOption.IGNORE_CASE)

    fun isHotPatched(classLoader: ClassLoader): Boolean {
        return generateSequence(classLoader) { it.parent }.any { loader ->
            generateSequence<Class<*>>(loader.javaClass) { it.superclass }.any {
                it.simpleName == "TinkerClassLoader"
            } || patchDexPath.containsMatchIn(loader.toString())
        }
    }
}
