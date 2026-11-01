package com.owo233.tcqt.baseline

import java.io.File

/**
 * 源码文本扫描辅助。
 *
 * 为什么不用类加载：`libs:qqinterface` 与 `libs.androidx.constraintlayout` 都是
 * `compileOnly`，QQ 类不在单元测试 classpath 上，任何加载 Action 类的测试都会
 * `NoClassDefFoundError`。因此基线测试一律读文本，不加载类。
 */
internal object SourceScanner {

    /** Gradle 单元测试的 workdir 是 app/，向上找到含 settings.gradle.kts 的仓库根。 */
    val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return@lazy dir
            dir = dir.parentFile
        }
        error("找不到仓库根含settings.gradle.kts）；cwd=${File("").absolutePath}")
    }

    val mainSourceRoot: File by lazy {
        File(repoRoot, "app/src/main/java/com/owo233/tcqt")
    }

    /** KSP 生成的注册清单；若不存在则给出可执行的修复指引，而不是让断言空转。 */
    val generatedActionList: File by lazy {
        val f = File(
            repoRoot,
            "app/build/generated/ksp/debug/kotlin/com/owo233/tcqt/generated/GeneratedActionList.kt"
        )
        if (!f.isFile) {
            error(
                "缺少 KSP 生成物 ${f.path}。先执行 `./gradlew :app:kspDebugKotlin` 再跑单元测试" +
                        "含本测试以生成物为权威注册来源，见基线 §5.1 计数口径）。"
            )
        }
        f
    }

    fun read(file: File): String = file.readText(Charsets.UTF_8)

    /** 去除块注释与行注释，避免把 KDoc 里的示例当成真实声明。 */
    fun stripComments(text: String): String {
        val noBlock = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
        return noBlock.lineSequence()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
    }

    /** 递归列出 main 源集下的全部 .kt 文件，按路径排序。 */
    fun mainKotlinFiles(): List<File> =
        mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
            .sortedBy { it.path }

    /** 整个 app/src/main/java 源根（含 top 等本模块之外的包）。 */
    val javaSourceRoot: File by lazy { File(repoRoot, "app/src/main/java") }

    /** Kotlin 和 Java 类型引用都参与宿主边界与包名检查。 */
    fun allJavaKotlinFiles(): List<File> =
        javaSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .toList()
            .sortedBy { it.path }
}
