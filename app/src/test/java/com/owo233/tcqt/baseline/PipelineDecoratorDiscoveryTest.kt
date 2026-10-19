package com.owo233.tcqt.baseline

import com.owo233.tcqt.features.internal.pipeline.OnAIOSendMsgBefore
import com.owo233.tcqt.features.internal.pipeline.OnAIOViewUpdate
import com.owo233.tcqt.features.internal.pipeline.OnMenuBuilder
import com.owo233.tcqt.features.internal.pipeline.PipelineDecorators
import com.owo233.tcqt.features.message.RecallHeaderTip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 管线装饰器的**能力发现 + 装配顺序**。
 *
 * ## 为什么顺序必须被钉住
 *
 * 改能力发现之前，三条管线各自写着 `arrayOf(具体类)`，顺序是**显式**的：
 * `ShowMsgInfo → RecallHeaderTip → AitChameleon`、`PttForward → RepeatMessage`。
 *
 * 改成从 `ActionRegistry` 枚举之后，返回顺序会变成 KSP 的**类名字典序** ——
 * `AitChameleon` 会跑到 `ShowMsgInfo` 前面，加号菜单项与视图条的先后跟着变。
 * 这是最难查的一类回归：编译过、测试绿、只是顺序悄悄变了。
 *
 * 因此 `PipelineDecorator` 有了显式的 `decoratorOrder`，本测试把三条管线的
 * 最终顺序**逐项钉死**。
 *
 * 本测试只碰发现与排序：`all()` 不调用 `isAvailable()`，因此不会触到
 * `TCQTSetting` / 宿主类。
 */
class PipelineDecoratorDiscoveryTest {

    private fun names(type: Class<out com.owo233.tcqt.api.PipelineDecorator>): List<String> =
        PipelineDecorators.all(type).map { it.javaClass.simpleName }

    @Test
    fun `AIO 视图装饰器按 decoratorOrder 装配，ShowMsgInfo 在 AitChameleon 之前`() {
        // RecallHeaderTip 是非注册装饰器，需由 loader 显式登记；
        // 这里只断言注册 Action 的发现结果（迁移前的顺序也是先 ShowMsgInfo）。
        assertEquals(
            listOf("MsgAntiRecall", "ShowMsgInfo", "AitChameleon"),
            names(OnAIOViewUpdate::class.java),
            "AIO 视图装饰器的装配顺序变了 —— 视图条的先后会跟着变",
        )
    }

    @Test
    fun `登记非注册装饰器后 RecallHeaderTip 落在两者之间`() {
        PipelineDecorators.register(RecallHeaderTip())

        assertEquals(
            listOf("MsgAntiRecall", "ShowMsgInfo", "RecallHeaderTip", "AitChameleon"),
            names(OnAIOViewUpdate::class.java),
            "RecallHeaderTip 的装配位置变了（它应排在顺序 200）",
        )
    }

    @Test
    fun `菜单净化先执行，PttForward 与 RepeatMessage 保持顺序，编辑重发最后装配`() {
        assertEquals(
            listOf("PurifyMenuButton", "PttForward", "RepeatMessage", "EditAndResend"),
            names(OnMenuBuilder::class.java),
            "加号菜单项的先后变了",
        )
    }

    @Test
    fun `发送前管线目前只有 RenameBaseApk`() {
        assertEquals(
            listOf("RenameBaseApk"),
            names(OnAIOSendMsgBefore::class.java),
            "发送前管线的装饰器集合变了",
        )
    }

    @Test
    fun `能力发现真的发现了注册 Action（防止断言被空集悄悄通过）`() {
        val total = names(OnAIOViewUpdate::class.java).size +
                names(OnMenuBuilder::class.java).size +
                names(OnAIOSendMsgBefore::class.java).size

        assertTrue(
            total >= 5,
            "三条管线总共只发现 $total 个装饰器 —— 能力发现断链了，" +
                    "而不是装饰器真的没了",
        )
    }
}
