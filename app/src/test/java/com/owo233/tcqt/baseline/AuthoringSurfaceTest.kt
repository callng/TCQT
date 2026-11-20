package com.owo233.tcqt.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **G1'：作者接入面**（ADR-007）。
 *
 * Spec §2.1 的 G1 原本要求"新增功能只需 1 行框架 import"。S3(a) 实施前实测发现它
 * **在层次约束下不可达**：`features/` 需要 95 个不同的 `core` 符号与 15 个 `host`
 * 符号，而 spec §3.1 禁止 `api` 依赖 `host` —— 宿主能力无法经门面转发。
 *
 * 于是 G1 被改写为可达且可验收的 G1'（详见 ADR-007）：
 *
 * > 作者**必须只需 `api.*` 表达功能契约**；其余能力**按需**引入。
 * > 不得出现"为了接进框架而必须认识的类"。
 *
 * 本测试把这条判据变成机械断言。
 */
class AuthoringSurfaceTest {

    private fun featureImports(): List<Pair<String, String>> =
        SourceScanner.mainKotlinFiles()
            .filter { it.path.replace('\\', '/').contains("/com/owo233/tcqt/features/") }
            .flatMap { file ->
                Regex("^\\s*import\\s+([\\w.]+)", RegexOption.MULTILINE)
                    .findAll(SourceScanner.stripComments(SourceScanner.read(file)))
                    .map { file.name to it.groupValues[1] }
            }

    /**
     * 功能**绝不**需要认识的框架内部件。
     *
     * 全部是 `core` 内部的零件 —— 也就是说 **`ArchitectureGuardTest` 看不见它们**
     * （`core → core` 天然同层放行）。这正是本测试存在的理由。
     *
     * `ui` 只列禁止的子包：`ui.component` 是 spec §3.1 明确允许的**原子层例外**
     * （ADR-005），实测被 15 处正当引用；`ui.settings` / `ui.parasitic` / `ui.theme`
     * 实测均为 0 处，属于"功能不该认识的界面表现模型"。
     */
    private val FORBIDDEN = listOf(
        "com.owo233.tcqt.core.action.StartupScheduler",
        "com.owo233.tcqt.core.action.HookSteps",
        "com.owo233.tcqt.core.config.SettingsRegistry",
        "com.owo233.tcqt.ui.settings",
        "com.owo233.tcqt.ui.parasitic",
        "com.owo233.tcqt.ui.theme",
    )

    @Test
    fun `功能不得 import 框架内部件`() {
        val offenders = featureImports()
            .filter { (_, imp) -> FORBIDDEN.any { imp == it || imp.startsWith(it) } }
            .map { (file, imp) -> "$file -> $imp" }

        assertTrue(
            offenders.isEmpty(),
            "功能不该认识这些内部件（注册表/调度器/配置注册/设置界面表现模型）——" +
                    "它们由框架按契约自动接线（ADR-007）：\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * `ActionRegistry` 只允许出现在**能力发现**里。
     *
     * 功能不该自己去注册表里翻别人的实例；唯一的正当用途是
     * `PipelineDecorators` 把实现了某装饰器接口的功能找出来（spec §3.6 要求的
     * "经 Registry 能力发现"）。
     */
    @Test
    fun `ActionRegistry 只允许出现在能力发现里`() {
        val allowed = setOf("PipelineDecorators.kt")

        val offenders = featureImports()
            .filter { (_, imp) -> imp == "com.owo233.tcqt.core.action.ActionRegistry" }
            .filterNot { (file, _) -> file in allowed }
            .map { (file, _) -> file }

        assertTrue(
            offenders.isEmpty(),
            "只有 PipelineDecorators 可以碰 ActionRegistry；其余功能请改为实现" +
                    "装饰器接口由能力发现驱动：\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * `TCQTSetting` 是**有意允许的例外**，但用途必须被钉住。
     *
     * 实测 6 处，全部正当：存储 owner（`FloatingBottomBarConfig` / `QQTabLocator`）、
     * 模块命令（`ModuleCommand` 清空配置）、读功能开关自身（`AddModuleEntrance`）、
     * 旧 key 兼容垫片（`RepeatMessage`）。
     *
     * 钉住数量是为了逼出"新增用途先想清楚"：它是存储 owner，还是又一个绕过
     * `Option` 的裸 key？后者应该被 `DerivedKeyCompatibilityTest` 的字面量检查拦住。
     */
    @Test
    fun `TCQTSetting 的使用点数量被钉住（防止裸 key 回潮）`() {
        val users = featureImports()
            .filter { (_, imp) -> imp == "com.owo233.tcqt.core.config.TCQTSetting" }
            .map { (file, _) -> file }
            .sorted()

        assertEquals(
            listOf(
                "AddModuleEntrance.kt",
                "FloatingBottomBarConfig.kt",
                "GroupCheckIn.kt",
                "InjectConsole.kt",
                "ModuleCommand.kt",
                "QQTabLocator.kt",
                "RepeatMessage.kt",
            ),
            users,
            "TCQTSetting 的使用点变了。新增用途前请先确认它不是" +
                    "「绕过 Option 的手写 key」（ADR-007 判据 4 的例外清单）",
        )
    }

    /**
     * 让禁止清单**自证可证伪**：匹配必须真的能命中，否则上面两条会永远绿。
     */
    @Test
    fun `禁止清单的判定可证伪`() {
        val matches: (String) -> Boolean = { imp ->
            FORBIDDEN.any { imp == it || imp.startsWith("$it.") }
        }

        assertTrue(matches("com.owo233.tcqt.core.action.StartupScheduler"), "精确匹配失效")
        assertTrue(matches("com.owo233.tcqt.ui.settings.SettingModels"), "子包匹配失效")
        assertTrue(
            !matches("com.owo233.tcqt.ui.component.CompatibleComposeDialog"),
            "ui.component 是允许的原子层，不该被判成违规",
        )
        assertTrue(
            !matches("com.owo233.tcqt.core.action.ActionProcess"),
            "core.action 下的普通类型不该被判成违规",
        )
    }
}
