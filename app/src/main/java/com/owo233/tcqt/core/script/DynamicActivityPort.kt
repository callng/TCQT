package com.owo233.tcqt.core.script

/**
 * 「动态 Activity 注册」进 core 的窄端口。
 *
 * `features` 不允许依赖 `ui.parasitic`（见 `ArchitectureGuardTest`），而脚本的
 * `registerActivity` 需要用到它。这里按 `HostBridge` 的既有做法：core 只放注册点，
 * 实现由 `loader` 在装配阶段注入。
 */
object DynamicActivityPort {

    @Volatile
    private var registerImpl: ((Class<*>) -> Unit)? = null

    @Volatile
    private var unregisterImpl: ((String) -> Unit)? = null

    /** 由 `loader` 注入 `DynamicActivityRegistry` 的读写实现。 */
    fun install(
        register: (Class<*>) -> Unit,
        unregister: (String) -> Unit,
    ) {
        registerImpl = register
        unregisterImpl = unregister
    }

    /** 端口是否已就绪；未就绪时脚本侧会收到 false 并记日志。 */
    val isReady: Boolean get() = registerImpl != null

    /** @return 是否注册成功（端口未就绪时为 false）。 */
    fun register(activityClass: Class<*>): Boolean {
        val impl = registerImpl ?: return false
        impl(activityClass)
        return true
    }

    fun unregister(className: String) {
        unregisterImpl?.invoke(className)
    }
}
