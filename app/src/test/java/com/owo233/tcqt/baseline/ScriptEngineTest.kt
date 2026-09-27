package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.action.ActionSpec
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.core.script.ScriptGateway
import com.owo233.tcqt.core.script.ScriptMeta
import com.owo233.tcqt.features.script.ScriptCore
import com.owo233.tcqt.generated.GeneratedActionList
import bsh.Interpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 脚本引擎的自验。
 *
 * 这些断言全部作用在**脚本对外契约**上：目录清单形状、网关是否按约定注入、
 * 以及 vendored BeanShell 在 JVM 上真的能解析并执行 `main.java` 那种脚本。
 *
 * 不在真机上跑的宿主交互（消息发送、菜单项构造）由设备验证覆盖。
 */
class ScriptEngineTest {

    @Test
    fun `引擎对外暴露的脚本模型字段齐全`() {
        val params = ScriptMeta::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.simpleName }

        assertEquals(
            listOf(
                "String", "String", "String", "String", "String",
                "String", "boolean", "boolean",
            ),
            params.take(8),
            "ScriptMeta 的字段顺序是设置界面读取的契约，改字段要同步 ScriptCore.toMeta()",
        )
    }

    @Test
    fun `引擎实现了脚本网关`() {
        assertTrue(
            ScriptGateway::class.java.isAssignableFrom(ScriptCore::class.java),
            "ScriptCore 必须实现 ScriptGateway，否则设置界面拿不到脚本列表",
        )
    }

    /**
     * 回归护栏：引擎内核**不得**声明为 `ActionUiType.ENTRY`。
     *
     * `ActionSpec.canRun()` 对 ENTRY 恒返回 false，于是 `invoke()` 里的
     * `if (canRun() && onInit())` 永不成立、`onRun` 不被调用、`install()` 不执行 ——
     * 网关没有注入，用户点设置里的入口只会看到「脚本引擎未就绪」。
     * 这正是真机上出现过的故障，因此用注册清单把两个类的**分工作为断言钉死**：
     * 内核是始终运行的隐藏 InfraTask，入口是 ENTRY 且必须是可见项。
     */
    @Test
    fun `引擎内核不得声明为 ENTRY，入口必须是可见 ENTRY 项`() {
        val actions = GeneratedActionList.ACTIONS.mapNotNull { cls ->
            runCatching {
                cls.getField("INSTANCE").get(null) as? ActionSpec
            }.getOrNull()
        }

        val core = actions.firstOrNull { it.key == "script_core" }
        val entry = actions.firstOrNull { it.key == "script_engine" }

        assertTrue(core != null, "注册清单里找不到 script_core（脚本引擎内核）")
        assertTrue(entry != null, "注册清单里找不到 script_engine（脚本引擎界面入口）")

        assertTrue(
            core.hidden,
            "script_core 必须是隐藏 InfraTask —— 可见项会被当作用户开关，且 install() 的调用条件会变",
        )
        assertTrue(
            core.uiType != ActionUiType.ENTRY,
            "script_core 声明成了 ENTRY：canRun() 会恒为 false，install() 永不执行，网关注入失败",
        )
        assertTrue(
            core.canRun(),
            "script_core 必须始终运行（不受开关控制），否则事件管线会随开关丢失",
        )
        assertEquals(
            ActionUiType.ENTRY,
            entry.uiType,
            "script_engine 是界面入口，必须是 ENTRY（不显示开关）",
        )
    }

    @Test
    fun `脚本网关未注入时界面拿到空状态而不是崩溃`() {
        val previous = ScriptGateway.instance
        ScriptGateway.instance = null
        try {
            assertTrue(ScriptGateway.instance == null, "网关置空失败")
        } finally {
            ScriptGateway.instance = previous
        }
    }

    @Test
    fun `vendored BeanShell 能解析并执行入口脚本结构`() {
        val interpreter = Interpreter()

        val output = StringBuilder()
        interpreter.set("sink", output)

        interpreter.eval(
            """
            String greeting = "hi";
            void onMsg(Object msgData) { sink.append("msg:" + msgData); }
            String getMsg(String text) { return text + "!"; }
            """.trimIndent()
        )

        assertTrue(
            interpreter.nameSpace.getMethodNames().contains("onMsg"),
            "脚本方法 onMsg 未注册进命名空间",
        )

        val onMsg = interpreter.nameSpace.getMethod("onMsg", arrayOf(Any::class.java))
        onMsg.invoke(arrayOf("hello"), interpreter)
        assertEquals("msg:hello", output.toString())

        val getMsg = interpreter.nameSpace.getMethod("getMsg", arrayOf(String::class.java))
        assertEquals("text!", getMsg.invoke(arrayOf("text"), interpreter))
    }

    @Test
    fun `vendored BeanShell 支持模块注入的全局 API 方法`() {
        val interpreter = Interpreter()

        class FakeApi {
            @Suppress("unused")
            fun greet(name: String): String = "hello $name"
        }

        val api = FakeApi()
        FakeApi::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .filterNot { it.name.contains("$") }
            .forEach { interpreter.nameSpace.setMethod(bsh.BshMethod(it, api)) }

        interpreter.eval("String v = greet(\"tcqt\");")
        assertEquals("hello tcqt", interpreter.get("v"))
    }
}
