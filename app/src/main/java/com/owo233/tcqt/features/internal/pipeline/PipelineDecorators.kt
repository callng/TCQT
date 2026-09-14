package com.owo233.tcqt.features.internal.pipeline

import com.owo233.tcqt.api.PipelineDecorator
import com.owo233.tcqt.core.action.ActionRegistry

/**
 * 管线装饰器的能力发现注册表：管线只认 `OnXxx` 接口，装饰器来自两处 ——
 * [ActionRegistry] 中实现了该接口的功能，以及经 [register] 显式登记的非 Action
 * （如 `RecallHeaderTip`，它没有自己的开关，只是 `MsgAntiRecall` 的渲染器）。
 *
 * 显式登记发生在 `loader/ModuleLoader`，早于任何管线 `install()`。
 * 结果按 [PipelineDecorator.decoratorOrder] 稳定排序。
 */
internal object PipelineDecorators {

    private val registered = mutableListOf<PipelineDecorator>()

    /** 登记非注册装饰器。由 `loader` 在启动早期调用，重复登记会被忽略。 */
    fun register(vararg decorators: PipelineDecorator) {
        decorators.forEach { if (it !in registered) registered += it }
    }

    /** 发现全部实现了 [type] 的装饰器，按 `decoratorOrder` 稳定排序。 */
    @Suppress("UNCHECKED_CAST")
    fun <T : PipelineDecorator> all(type: Class<T>): List<T> {
        val fromRegistry = ActionRegistry.allActionClasses()
            .mapNotNull(ActionRegistry::instanceOf)

        return (fromRegistry + registered)
            .filterIsInstance<PipelineDecorator>()
            .filter { type.isInstance(it) }
            .sortedBy { it.decoratorOrder }
            .map { it as T }
    }
}
