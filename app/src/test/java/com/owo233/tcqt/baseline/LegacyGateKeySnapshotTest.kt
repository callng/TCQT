package com.owo233.tcqt.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 特征化快照：**历史**开关 key 的兼容基线（`baseline/gate-keys.txt`，83 项）。
 *
 * 为什么这是"不能变"的：`ActionSpec.key` 是开关状态在 FastKV 里的持久化键。
 * 改名 = 用户已保存的开关状态全部丢失。
 *
 * ## 新增功能需要改这里吗？—— **不需要**
 *
 * 这份文件是**冻结的历史兼容基线**，记录"重构完成时已存在、因此必须永远可读"的
 * 那批 key。它要守的是「**已发布的 key 不许改名/消失**」，而**不是**「不许新增 key」——
 * 新增功能的 key 没有任何历史包袱，也就没有任何用户配置需要保护。
 *
 * 早先这里断言的是**集合精确相等**：结果是每加一个功能都要改 `gate-keys.txt`
 * 外加两处硬编码计数。那比本测试自己声明的意图（"改名才会丢配置"）**更强**，
 * 与本项目"新增功能应当零框架摩擦"的目标直接冲突 —— 已改为**子集语义**：
 * 历史条目必须逐项仍在且语义不变；新增条目一律放行。
 *
 * 若你确实想冻结一个新 key（例如它已成为对外承诺），再手工往
 * `gate-keys.txt` 追加一行即可。
 */
class LegacyGateKeySnapshotTest {

    private fun loadGolden(): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("baseline/gate-keys.txt")) {
            "缺少 app/src/test/resources/baseline/gate-keys.txt"
        }.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * 历史条目必须逐项仍在，且 **key 与 uiType 一字不变**。
     *
     * 只按 `simpleName` 对齐，不要求数量相等 —— 新增功能因此**零改动**。
     */
    @Test
    fun `历史开关 key 必须全部保留，且 key 与类型不变`() {
        val actual = RegisteredAction.load()
            .filterNot { it.hidden }
            .associateBy { it.simpleName }

        val problems = mutableListOf<String>()
        loadGolden().forEach { line ->
            val parts = line.split('|')
            if (parts.size < 4) {
                problems += "基线格式错误：$line"
                return@forEach
            }
            val name = parts[0]
            val key = parts[1]
            val uiType = parts[3]

            val now = actual[name]
            when {
                now == null ->
                    problems += "$name：历史功能消失了（用户的开关状态会读不到）"

                now.key != key ->
                    problems += "$name：开关 key 改了 [$key] → [${now.key}]（用户开关状态会丢失）"

                now.uiType != uiType ->
                    problems += "$name：uiType 改了 [$uiType] → [${now.uiType}]"
            }
        }

        assertTrue(
            problems.isEmpty(),
            "历史开关 key 的兼容性被破坏 —— 用户已保存的配置会读不到：\n" +
                problems.joinToString("\n"),
        )
    }

    /**
     * 新增功能**不需要**改基线，但它们的 key 必须唯一且非空。
     *
     * 这一条才是"新增功能"真正该守的东西：key 撞车会让两个功能共用同一份用户配置，
     * 且**编译与其它测试都不会报**。
     */
    @Test
    fun `所有注册功能的开关 key 必须唯一且非空`() {
        val all = RegisteredAction.load()

        val blank = all.filter { it.key.isBlank() }.map { it.simpleName }
        assertTrue(blank.isEmpty(), "以下功能的开关 key 为空：$blank")

        val duplicated = all.groupBy { it.key }.filterValues { it.size > 1 }
        assertTrue(
            duplicated.isEmpty(),
            "开关 key 撞车 —— 两个功能会共用同一份用户配置：\n" +
                duplicated.entries.joinToString("\n") {
                    "  ${it.key} <- ${it.value.map { a -> a.simpleName }}"
                },
        )
    }

    /**
     * 隐藏基础设施的数量是**架构不变量**（3 个 `internal/` InfraTask +
     * 3 个 `internal/pipeline/` 管线 + 脚本引擎内核 + 内核消息桥 + 脚本 AIO 入口），
     * 不是"功能计数" —— 有意保留为需要显式确认的门槛：增减它意味着架构变了。
     *
     * 三个脚本相关项都是**必须常驻**的：
     * - `ScriptCore`：`ActionUiType.ENTRY` 会让 `ActionSpec.canRun()` 恒为 false、
     *   `install()` 永不执行，所以引擎内核只能是始终运行的 `InfraTask`；
     * - `KernelMessageBridge`：消息入口（`onRecvMsg`）与消息源必须全进程常驻，
     *   不能挂在任何带开关的功能上；
     * - `ScriptAioEntry`：悬浮菜单要随会话出现/消失，不能受某个开关控制；
     * - `GroupEventBridge`：群事件是能力，同样不该挂在开关后面。
     *
     * 原先这里还断言了"注册项总数为 89"，那条会随任何一个新功能失效，
     * 已删除（总数不是不变量）。
     */
    @Test
    fun `隐藏基础设施为 10 个`() {
        val hidden = RegisteredAction.load().filter { it.hidden }
        assertEquals(
            10,
            hidden.size,
            "隐藏基础设施数量变了（3 InfraTask + 3 管线 + 引擎内核 + 内核消息桥 + 脚本 AIO 入口 + 群事件桥）—— 若这是设计变更请同步本断言",
        )
    }

    @Test
    fun `隐藏项 key 不得为空`() {
        val blank = RegisteredAction.load().filter { it.hidden && it.key.isEmpty() }
        assertTrue(
            blank.isEmpty(),
            "隐藏项 key 为空会共用哨兵 'not_empty'（基线 D4）：${blank.map { it.fqn }}"
        )
    }
}
