package com.owo233.tcqt.core.action

import com.owo233.tcqt.core.action.FeatureCategories.LABELS


/**
 * **目录即分类**：功能在设置界面里的归属，只由它所在的包决定。
 *
 * 表放在 `core.action`，因为读它的是 `ui.settings.FeatureCatalog`，而 `ui` 只能
 * 依赖 `core` 与 `host`。
 *
 * 运行期取包名用的是 `javaClass.name`（见 [ActionSpec.uiTab]），所以 **R8 不能做包
 * 扁平化** —— 不能加 `-repackageclasses` / `-flattenpackagehierarchy`：那会让功能的
 * `javaClass.name` 丢掉 `features/<分类>/` 这一段，**全部静默掉进 [FALLBACK]**，
 * 设置界面只剩一个分类。
 */
object FeatureCategories {

    /** 包的最后一段 → 设置界面分类标签；键与 `features/<key>/` 目录一一对应，顺序即 [ORDER]。 */
    private val LABELS: Map<String, String> = linkedMapOf(
        "general" to "基础",
        "chat" to "聊天",
        "message" to "消息操作",
        "menu" to "菜单与入口",
        "appearance" to "外观",
        "cleanup" to "净化",
        "advanced" to "高级",
        "notification" to "通知",
        "misc" to "杂项",
        "debug" to "调试",
        "script" to "脚本",
    )

    /** 设置界面根列表的展示顺序（= [LABELS] 的声明顺序）。 */
    val ORDER: List<String> = LABELS.values.toList()

    /**
     * 包不在表内时的兜底标签，只应出现在不进设置界面的类上（例如
     * `features/internal` 下的管线与基础设施任务）。可见功能走到这里，
     * `CategoryConsistencyTest` 会报错。
     */
    const val FALLBACK = "基础"

    /** 取全限定类名所在包的最后一段（即分类键）。 */
    fun segmentOf(className: String): String =
        className.substringBeforeLast('.', "").substringAfterLast('.')

    /** 全限定类名 → 分类标签；包不在表内返回 null。 */
    fun labelOf(className: String): String? = LABELS[segmentOf(className)]

    /** 包是否已在分类表里登记。 */
    fun isKnown(className: String): Boolean = labelOf(className) != null
}
