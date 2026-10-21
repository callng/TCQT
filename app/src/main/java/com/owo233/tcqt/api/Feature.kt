package com.owo233.tcqt.api

import android.app.Application
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.action.ActionSpec
import com.owo233.tcqt.core.action.ActionUiType
import com.owo233.tcqt.core.config.Setting

/**
 * 功能的**唯一入口**。
 *
 * 一个功能 = 一个文件、一行框架 import（`import com.owo233.tcqt.api.*`）。
 * 元数据、配置项声明与 hook 安装都在同一个类里，且配置 key 由
 * [Option.key] 从构造参数派生，不需要任何字符串重复。
 *
 * ```kotlin
 * @RegisterAction
 * object FakePicSize : Feature(
 *     key = "fake_pic_size",
 *     name = "篡改图片显示大小",
 *     desc = "将发送的消息图片以指定的比例显示。",
 *     uiTab = "界面",
 * ) {
 *     private val type by intOption("type", "图片比例", defaultValue = 1,
 *         options = listOf("默认", "最小", "略小", "略大", "最大", "自定义"))
 *
 *     override fun install() {
 *         val mode = type.value
 *         // …hook 安装…
 *     }
 * }
 * ```
 *
 * [ActionSpec] 是 `core` / `ui` 够得着的内部模型，本类实现它；功能作者只继承本类，
 * `features/` 下直接实现 `ActionSpec` 会被 `SingleAuthoringContractTest` 检出。
 *
 * [onInit] 与 [settings] 都是 final：前者由声明式 [Requires] 驱动，后者由
 * `by xxxOption(...)` 声明自动聚合。
 *
 * @param key 功能开关的持久化 key。**一经发布不可改名**。
 * @param name 设置界面显示名。
 * @param desc 设置界面说明。
 * @param uiOrder 分类内排序，越小越靠前。
 * @param processes 要执行该功能的宿主进程。
 * @param priority 启动优先级。
 * @param defaultEnabled 新装用户的默认开关状态。
 * @param uiType 设置界面呈现类型。
 * @param requires 声明式可用性条件。
 */
abstract class Feature(
    final override val key: String,
    final override val name: String,
    final override val desc: String = "",
    final override val uiOrder: Int = 1000,
    final override val processes: Set<ActionProcess> = ActionSpec.DEFAULT_PROCESSES,
    final override val priority: ActionPriority = ActionPriority.DEFERRED,
    defaultEnabled: Boolean = false,
    final override val uiType: ActionUiType = ActionUiType.SWITCH,
    val requires: Requires = Requires.None,
    /**
     * 运行期才能确定的默认开关状态，提供时优先于 [defaultEnabled] 字面量。
     *
     * 例如 `ModuleUpdate` 只在非 Zygisk 且 `apiLevel < 102` 时默认开启。
     */
    private val defaultEnabledProvider: (() -> Boolean)? = null,
) : ActionSpec, PipelineDecorator {

    /** [defaultEnabledProvider] 为 null 时使用的字面量默认值。 */
    private val defaultEnabledLiteral: Boolean = defaultEnabled

    final override val defaultEnabled: Boolean
        get() = defaultEnabledProvider?.invoke() ?: defaultEnabledLiteral

    /** 由 `by xxxOption(...)` 在子类属性初始化时填充。 */
    private val declaredOptions = mutableListOf<Option<*>>()

    /** 声明即注册：把 Option 投影成既有 `Setting` 给注册表与设置界面消费。 */
    final override val settings: List<Setting<*>>
        get() = declaredOptions.map { it.toSetting() }

    /** 由 [Requires] 求值，作者不可覆写。 */
    final override fun onInit(): Boolean = requires.evaluate()

    /**
     * 作为管线装饰器时的可用性，与框架启动路径 [ActionSpec.invoke] 的判据一致。
     *
     * [activate] 保持空实现：注册 Action 的安装由框架走 [install]，管线不重复触发。
     */
    final override fun isAvailable(): Boolean = canRun() && onInit()

    /** 安装 hook。 */
    protected abstract fun install()

    final override fun onRun(app: Application, process: ActionProcess) {
        hostAppRef = app
        processRef = process
        install()
    }

    /**
     * 宿主 `Application`，由框架在调用 [install] 之前注入。
     *
     * 少数功能需要它做进程级注册（例如 `ModuleUpdate` 注册广播接收器）。
     */
    protected val hostApp: Application
        get() = hostAppRef ?: error("宿主 Application 尚未就绪；只能在 install() 及之后访问")

    /**
     * 当前宿主进程，由框架在调用 [install] 之前注入。
     *
     * 少数功能要按进程安装不同的 hook（例如 `SkipQRLoginWait` 在 `MAIN` 与
     * `OPENSDK` 里各 hook 一处）。
     */
    protected val currentProcess: ActionProcess
        get() = processRef ?: error("宿主进程尚未就绪；只能在 install() 及之后访问")

    /** hook 回调之后会在别的线程读取这两个字段，故用 `@Volatile`。 */
    @Volatile
    private var hostAppRef: Application? = null

    @Volatile
    private var processRef: ActionProcess? = null

    // ── 配置项声明工厂 ──────────────────────────────────────────────────────
    //
    // 全部 protected：只能从子类体内调用，且会把生成的 Option 自动注册进
    // declaredOptions，作者不需要（也无法）手写 settings 列表。

    /** 布尔配置项（只持久化，不渲染界面组件）。 */
    protected fun booleanOption(
        settingKey: String,
        name: String,
        defaultValue: Boolean = false,
        desc: String = "",
        isHide: Boolean = false,
    ): BooleanOption = register(
        BooleanOption(key, settingKey, name, defaultValue, desc, isHide)
    )

    /** 单选配置项（RadioButton 组）。 */
    protected fun intOption(
        settingKey: String,
        name: String,
        defaultValue: Int = 0,
        desc: String = "",
        options: List<String>,
        isHide: Boolean = false,
    ): IntOption = register(
        IntOption(key, settingKey, name, defaultValue, desc, options, isHide)
    )

    /** 多选配置项（Checkbox 组，位掩码存储）。 */
    protected fun multiIntOption(
        settingKey: String,
        name: String,
        defaultValue: Int = 0,
        desc: String = "",
        options: List<String>,
        forcedSelections: Map<Int, List<Int>> = emptyMap(),
        isHide: Boolean = false,
    ): MultiIntOption = register(
        MultiIntOption(key, settingKey, name, defaultValue, desc, options, forcedSelections, isHide)
    )

    /** 滑块配置项。 */
    protected fun sliderOption(
        settingKey: String,
        name: String,
        defaultValue: Int = 0,
        desc: String = "",
        min: Int,
        max: Int,
        step: Int = 1,
        suffix: String = "",
        isHide: Boolean = false,
    ): SliderOption = register(
        SliderOption(key, settingKey, name, defaultValue, desc, min, max, step, suffix, isHide)
    )

    /** 字符串配置项（多行文本框）。 */
    protected fun stringOption(
        settingKey: String,
        name: String,
        defaultValue: String = "",
        desc: String = "",
        placeholder: String = "",
        isHide: Boolean = false,
    ): StringOption = register(
        StringOption(key, settingKey, name, defaultValue, desc, placeholder, isHide)
    )

    /** PNG 图标选择器，空值表示使用宿主默认图标。 */
    protected fun pngIconOption(settingKey: String, name: String): StringOption = register(
        StringOption(key, settingKey, name, "", "", "", isPngIcon = true)
    )

    private fun <O : Option<*>> register(option: O): O {
        declaredOptions += option
        return option
    }
}
