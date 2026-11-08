package com.owo233.tcqt.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RegisterAction(
    /**
     * 是否启用，默认为true
     */
    val enabled: Boolean = true,
    val processes: Array<ActionProcess> = [ActionProcess.MAIN],
    /** 安装阶段及线程，见 [ActionPriority]；普通功能保留默认 DEFERRED。 */
    val priority: ActionPriority = ActionPriority.DEFERRED,
)
