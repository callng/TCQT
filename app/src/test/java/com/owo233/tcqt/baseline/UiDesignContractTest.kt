package com.owo233.tcqt.baseline

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Prevent a partial migration from silently introducing a second visual system. */
class UiDesignContractTest {
    private val root = SourceScanner.mainSourceRoot

    @Test
    fun `module interfaces use Miuix theme and continuous corners`() {
        val files = listOf(File(root, "ui"), File(root, "features"))
            .flatMap { folder -> folder.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        val forbidden = Regex("androidx\\.compose\\.material3|\\bMaterialTheme\\b|\\bRoundedCornerShape\\b|Color\\(0x")
        val violations = files.filter { forbidden.containsMatchIn(SourceScanner.stripComments(SourceScanner.read(it))) }
        assertTrue(violations.isEmpty(), "界面出现第二套主题或形状来源：${violations.map { it.name }}")
    }

    @Test
    fun `host dialogs have one compatible window and use Miuix overlays`() {
        val paths = listOf(
            "features/advanced/GuidEditorDialog.kt", "features/advanced/InfoCardDialog.kt",
            "features/advanced/CopyTicketDialog.kt", "features/chat/MsgDetailDialog.kt",
            "features/menu/troop/TroopManagementDialog.kt", "ui/component/DexKitScanDialog.kt",
        )
        paths.forEach { path ->
            val source = SourceScanner.stripComments(SourceScanner.read(File(root, path)))
            assertTrue("HostDialog(" in source || "OverlayBottomSheet(" in source, "$path 缺少统一的 Miuix 弹出层")
            assertTrue("::dismissImmediately" in source, "$path 缺少动画完成后的窗口释放")
            assertTrue("WindowDialog(" !in source && "AnimatedVisibility(" !in source, "$path 出现重复窗口或手写弹窗外壳")
        }
    }
}
