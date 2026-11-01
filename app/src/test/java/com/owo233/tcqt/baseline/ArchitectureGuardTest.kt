package com.owo233.tcqt.baseline

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 分层依赖守卫。
 *
 * **S0：只报告，不失败。** 当前代码里存在多处反向依赖（基线 §5.1），
 * S0 先让它们**可见**；S1a–S1d 逐条消除后，`strict` 已置为 true。
 *
 * 规则表来源：spec §3.1。S0 阶段包名仍是旧的（`hooks` / `utils` / `internals` …），
 * 因此这里用「当前包名 → 目标层」的近似映射来检查。映射是**近似**的：
 * 例如 `hooks.base.load`（类加载辅助，目标在 core）与 `hooks.func.*`
 * （功能，目标在 features）当前同属 `hooks`，会被一并算作 features 层。
 * 这些近似误差正是 S1 移动文件后要消除的东西 —— 所以 S0 只报告。
 */
class ArchitectureGuardTest {

    /** S1 完成后改为 true。 */
    private val strict = true

    /**
     * 当前包名 → 目标层。
     *
     * S1b/S1c 已把文件搬到目标位置，因此这张映射表现在基本是恒等映射；
     * 保留旧条目只为在过渡期仍能识别历史路径。
     */
    private val layerOfPackage = mapOf(
        "" to "core",
        "core" to "core",
        "api" to "api",
        "host" to "host",
        "ui" to "ui",
        "features" to "features",
        "loader" to "loader",
        // ── 以下为 S1b/S1c 前的历史包名，搬完后应为 0 命中 ──
        "activity" to "ui",
        "hooks" to "features",
        "ext" to "core",
        "utils" to "core",
        "internals" to "host",
        "impl" to "host",
        "lifecycle" to "ui",
        "servlet" to "core",
        "data" to "core",
    )

    private val allowed: Map<String, Set<String>> = mapOf(
        "core" to emptySet(),
        "api" to setOf("core"),
        "host" to setOf("core"),
        "ui" to setOf("core", "host"),
        "features" to setOf("core", "host", "api", "ui.component"),
        "loader" to setOf("core", "api", "host", "ui", "features"),
    )

    /**
     * S1b 已修复的 3 处核心反向依赖 —— 必须保持消失（回归断言）。
     *
     * 基线 §5.1 原本记录 4 处；S1b 搬迁后其中 3 处消失：
     *  - `internals/QQInterfaces.kt → hooks`：QQInterfaces 移入 host，改依赖 host.service
     *  - `activity/SettingViewModel.kt → hooks`：AntiRecallConfig 移入 host.service
     *  - `utils/dexkit/DexKitFinder.kt → hooks`：ModuleCommand 的反向依赖被
     *    `core/command/ModuleCommandBus` 打断
     *
     * 第 4 处 `ActionManager → ui` 尚未修复（现在表现为
     * `core/action/ActionManager.kt → ui.settings.*`），属 S1c 的 ActionManager 三分。
     */
    private val resolvedViolations = listOf(
        "host/QQInterfaces.kt -> com.owo233.tcqt.hooks.",
        "ui/settings/SettingViewModel.kt -> com.owo233.tcqt.hooks.",
        "core/dexkit/DexKitFinder.kt -> com.owo233.tcqt.hooks.",
        "internals/QQInterfaces.kt -> com.owo233.tcqt.hooks.",
        "activity/SettingViewModel.kt -> com.owo233.tcqt.hooks.",
        "utils/dexkit/DexKitFinder.kt -> com.owo233.tcqt.hooks.",
        "ActionManager.kt -> com.owo233.tcqt.activity.",
        "core/action/ActionManager.kt -> com.owo233.tcqt.ui.settings.",
    )

    /** 第 4 处（ActionManager → ui）在 S1c 完成三分前必须仍然存在；消失即说明做过了。 */
    private val pendingViolation = "core/action/ActionManager.kt -> com.owo233.tcqt.ui."

    private fun relativePath(file: java.io.File): String =
        file.path.replace('\\', '/')
            .substringAfter("/app/src/main/java/com/owo233/tcqt/")
            .ifEmpty { file.name }

    /**
     * 解析 import 所属的层。对 `ui` 保留二级（`ui.component` / `ui.settings` …），
     * 因为 `features` 被允许依赖 `ui.component` 这个 UI 原子层，但不能依赖
     * `ui.settings` 或 `ui.parasitic`。
     */
    private fun layerOfImport(importPath: String): String? {
        val rel = importPath.removePrefix("com.owo233.tcqt.")
        val parts = rel.split('.')
        val top = parts.firstOrNull().orEmpty()
        val topLayer = layerOfPackage[top] ?: return null
        return if (topLayer == "ui" && parts.size >= 2) "ui.${parts[1]}" else topLayer
    }

    /** `allowed` 中的条目按**前缀**匹配：`ui` 允许 `ui.component` 但不等同于允许 `ui.settings`。 */
    private fun isPermitted(targetLayer: String, permitted: Set<String>): Boolean =
        permitted.any { targetLayer == it || targetLayer.startsWith("$it.") }

    private fun collectViolations(): List<String> {
        val violations = mutableListOf<String>()
        for (file in SourceScanner.mainKotlinFiles()) {
            val rel = relativePath(file)
            val topPackage = if ('/' in rel) rel.substringBefore('/') else ""
            val ownLayer = layerOfPackage[topPackage] ?: continue
            val permitted = allowed[ownLayer] ?: continue

            val imports = SourceScanner.stripComments(SourceScanner.read(file))
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("import com.owo233.tcqt.") }
            for (line in imports) {
                val importPath = line.removePrefix("import ").trim()
                val targetLayer = layerOfImport(importPath) ?: continue
                // 同一顶层内的子包互访不算跨层（ui.settings ↔ ui.component ↔ ui.theme）
                val targetTop = targetLayer.substringBefore('.')
                if (targetLayer == ownLayer || targetTop == ownLayer) continue
                if (!isPermitted(targetLayer, permitted)) {
                    violations += "$rel -> $importPath ($ownLayer 不得依赖 $targetLayer)"
                }
            }
        }
        return violations.sorted()
    }

    @Test
    fun `分层依赖方向检查`() {
        val violations = collectViolations()

        val regressions = violations.filter { v -> resolvedViolations.any { v.startsWith(it) } }
        val pendingHit = violations.any { it.startsWith(pendingViolation) }

        println("[ArchitectureGuard] 违规合计 ${violations.size} 处")
        println("[ArchitectureGuard]   S1b 已修复的 3 处复发：${regressions.size} 处（应为 0）")
        println(
            "[ArchitectureGuard]   S1c 待办 ActionManager → ui：" +
                    if (pendingHit) "仍在（预期）" else "已消失（说明 S1c 已做）"
        )
        violations.take(60).forEach { println("[ArchitectureGuard]       $it") }

        assertTrue(
            regressions.isEmpty(),
            "S1b 已修复的核心反向依赖复发了：$regressions"
        )
        if (strict) {
            assertTrue(violations.isEmpty(), "存在未授权依赖：${violations.take(20)}")
        } else {
            println("[ArchitectureGuard] 报告形态：其余条目只记录不失败，由 S1c/S2 继续收敛。")
        }
    }

    /**
     * 每个源文件的 `package` 声明必须与其所在目录一致。
     *
     * Kotlin **不强制**这一点，所以它不会导致编译失败 —— 但目录一旦与包名脱节，
     * 分层守卫就会把文件算到"目录暗示的层"而不是"它实际所属的层"，
     * 于是违规被静默放过。S1c 曾有 6 个文件（首行是注释、包名重写正则未加
     * MULTILINE 标志）保留了旧包 `hooks.func.*`，却躺在 `features` 目录下，
     * 守卫因此报 0 违规 —— 本条断言就是为了让这类问题不再漏。
     */
    @Test
    fun `每个源文件的 package 与其目录一致`() {
        val javaRoot = SourceScanner.javaSourceRoot.path.replace('\\', '/')
        val mismatches = SourceScanner.allJavaKotlinFiles().mapNotNull { file ->
            val rel = file.path.replace('\\', '/').removePrefix("$javaRoot/")
            val dir = rel.substringBeforeLast('/', "")
            val expected = if (dir.isEmpty()) "<默认包>" else dir.replace('/', '.')
            val actual = Regex("(?m)^package\\s+([\\w.]+)")
                .find(SourceScanner.read(file))?.groupValues?.get(1)
                ?: return@mapNotNull "$rel: 缺少 package 声明"
            if (actual != expected) "$rel: 声明=$actual 期望=$expected" else null
        }.sorted()

        println("[ArchitectureGuard] package/目录 不一致：${mismatches.size} 处")
        mismatches.take(20).forEach { println("[ArchitectureGuard]   $it") }

        assertTrue(
            mismatches.isEmpty(),
            "package 与目录不一致（会误导分层守卫）：${mismatches.take(10)}"
        )
    }

    @Test
    fun `core 层不得依赖 Compose 或 Miuix`() {
        val coreFiles = SourceScanner.mainKotlinFiles().filter { file ->
            val rel = relativePath(file)
            val topPackage = if ('/' in rel) rel.substringBefore('/') else ""
            layerOfPackage[topPackage] == "core"
        }
        // S0 阶段 activity/ 被映射为 ui，不受此断言影响；这里只统计"目标属 core"的文件。
        val offenders = coreFiles.filter { file ->
            val text = SourceScanner.stripComments(SourceScanner.read(file))
            text.contains("import androidx.compose.") || text.contains("import top.yukonga.miuix.")
        }.map { it.name }.sorted()

        println("[ArchitectureGuard] core 层中仍有 Compose/Miuix 引用的文件：$offenders")
        if (strict) {
            assertTrue(offenders.isEmpty(), "core 层不得依赖 Compose/Miuix：$offenders")
        }
    }

    @Test
    fun `core 不得静态绑定 QQ 或 Hook 框架类型`() {
        val forbidden = Regex("\\b(?:com\\.tencent\\.|mqq\\.|cooperation\\.|de\\.robv\\.android\\.xposed\\.|io\\.github\\.libxposed\\.)")
        // Runtime class-name strings belong to the generic class-loader policy; type references do not.
        val strings = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"")
        val offenders = SourceScanner.allJavaKotlinFiles().filter { relativePath(it).startsWith("core/") }
            .mapNotNull { file ->
                val code = SourceScanner.stripComments(SourceScanner.read(file)).replace(strings, "\"\"")
                forbidden.find(code)?.let { "${relativePath(file)}: ${it.value}" }
            }
        assertTrue(offenders.isEmpty(), "core 静态绑定宿主/框架：$offenders")
    }
}
