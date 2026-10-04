package com.owo233.tcqt.baseline

import bsh.Interpreter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * BeanShell 语言能力清单。
 *
 * `QFun_Plugin_API.md` 的「开发环境说明」与「Lambda 表达式支持」两章承诺了一批语言特性。
 * 这里用当前 vendored 的解释器**实际执行**并断言，结论只信执行结果。
 *
 * ⚠️ 本测试跑在 JVM 上，而脚本实际运行在 Android/ART 上，两者能力**不完全相同**：
 * - `?.` / `?:` 由 BeanShell 自己实现，JVM 与 Android 一致；
 * - Lambda 需要运行期把生成的字节码转成 dex，JVM 上没有 `dalvik.system`，
 *   因此 Lambda / 方法引用在 JVM 上会失败、在 Android 上是**可用**的
 *   （真机实测：`(x,y)->…`、`Math::abs`、`Stream+Lambda` 全部可用）。
 *
 * 所以这里只断言与平台无关的部分；Lambda 是否可用以真机结果为准，
 * 见 `docs/script-api.md` 的语言能力表。
 */
class BeanShellCapabilityTest {

    private fun evalToString(script: String): String =
        (Interpreter().eval(script))?.toString().orEmpty()

    @Test
    fun `支持 Kotlin 空安全调用运算符`() {
        assertEquals("null", evalToString("String s = null; return \"\" + (s?.length());"))
    }

    @Test
    fun `支持 Kotlin Elvis 运算符`() {
        assertEquals("fallback", evalToString("String s = null; return s ?: \"fallback\";"))
    }

    @Test
    fun `Elvis 在非空时不取默认值`() {
        assertEquals("real", evalToString("String s = \"real\"; return s ?: \"fallback\";"))
    }

    @Test
    fun `支持普通 Java 语法与集合`() {
        assertEquals(
            "2",
            evalToString(
                """
                java.util.List l = new java.util.ArrayList();
                l.add("a"); l.add("b");
                return l.size() + "";
                """.trimIndent(),
            ),
        )
    }
}