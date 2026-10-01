package com.owo233.tcqt.features.menu

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.hook.MethodHookParam
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.features.internal.pipeline.OnMenuBuilder
import java.lang.reflect.Method

/**
 * 长按菜单按钮净化。
 *
 * 打开后，在下方按「英文逗号」分隔填写要隐藏的按钮名称，
 * 长按消息菜单里匹配到的按钮就会被隐藏。
 *
 * 注意：**不会隐藏本模块自己添加的功能**（编辑重发 / 复读 +1 / 语音转发等），
 * 这些项的类是 [CustomMenu] 用 DexMaker 现场生成的，类名统一以
 * `com.owo233.tcqt.gen.MenuItem` 开头，靠这个前缀识别。
 */
@RegisterAction
object PurifyMenuButton : Feature(
    key = "purify_menu_button",
    name = "长按菜单按钮净化",
    desc = "隐藏长按消息菜单里指定的按钮，本模块添加的功能除外。",
    // 排在「移除菜单图标」(1000) 的下面
    uiOrder = 1005,
), OnMenuBuilder {

    /**
     * 比所有其它菜单装饰器都早（PttForward=100 / RepeatMessage=200 / EditAndResend=250）。
     *
     * 这样它执行时 `param.result` 里还**只有 QQ 原生的按钮**，
     * 本模块后续加进去的功能天然不会被误伤。
     */
    override val decoratorOrder: Int = 50

    /** 不重写 [targetComponentTypes]，用 [OnMenuBuilder] 的默认值（所有消息组件）。 */

    private val blockedConfig by stringOption(
        settingKey = "purify.blocked_names",
        name = "要隐藏的按钮名称",
        desc = "多个名称用英文逗号隔开，匹配到的长按菜单按钮会被隐藏；本模块添加的功能不会被隐藏",
        placeholder = "例如：转发,收藏,撤回,复制",
    )

    /** 无 hook 需求，纯菜单装饰器。 */
    override fun install() = Unit

    override fun onGetMenuNt(msg: Any, componentType: String, param: MethodHookParam) {
        val keywords = blockedConfig
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (keywords.isEmpty()) return
        val menu = param.result as? List<*> ?: return

        val filtered = menu.filterNot { item ->
            if (item == null) return@filterNot false
            // 本模块添加的功能永远不隐藏
            if (isFromModule(item)) return@filterNot false
            val texts = textsOf(item)
            keywords.any { keyword -> texts.any { it.contains(keyword) } }
        }

        if (filtered.size != menu.size) {
            Log.i("purify_menu_button: 隐藏了 ${menu.size - filtered.size} 个菜单按钮")
            param.result = filtered
        }
    }

    /**
     * 是不是本模块添加的菜单项。
     *
     * [CustomMenu] 用 DexMaker 生成菜单项类，类名形如
     * `com.owo233.tcqt.gen.MenuItem<hex>`，直接认这个前缀。
     */
    private fun isFromModule(item: Any): Boolean =
        item.javaClass.name.startsWith(MODULE_MENU_ITEM_PREFIX)

    /**
     * 收集菜单项上所有「可能的显示文本」。
     *
     * QQ 的菜单抽象类有 **两个** 返回 String 的无参方法：实测其中一个返回内部标识
     * （如 `CopyMenuItem`），另一个才是界面上的中文（如「复制」）。拿不准哪个是哪个，
     * 所以全都收集起来，任意一个匹配到关键字就隐藏。
     * 顺带把 String 类型的字段也扫一遍，防止文本只存在字段里。
     */
    private fun textsOf(item: Any): List<String> {
        val result = LinkedHashSet<String>()

        runCatching {
            item.javaClass.methods
                .filter { it.parameterCount == 0 && it.returnType == String::class.java }
                .forEach { m ->
                    runCatching { m.invoke(item) as? String }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { result += it }
                }
        }

        runCatching {
            var clazz: Class<*>? = item.javaClass
            while (clazz != null && clazz != Any::class.java) {
                clazz.declaredFields
                    .filter { it.type == String::class.java }
                    .forEach { f ->
                        f.isAccessible = true
                        runCatching { f.get(item) as? String }.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { result += it }
                    }
                clazz = clazz.superclass
            }
        }

        return result.toList()
    }

    /** [CustomMenu] 生成的菜单项类名前缀。 */
    private const val MODULE_MENU_ITEM_PREFIX = "com.owo233.tcqt.gen.MenuItem"
}
