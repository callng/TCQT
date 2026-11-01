package com.owo233.tcqt.core.action

/** Class literals do not initialize feature singletons. */
internal data class ActionDescriptor(
    val actionClass: Class<out ActionSpec>,
    val processes: Set<ActionProcess>,
    val priority: ActionPriority,
) {
    fun matches(proc: ActionProcess): Boolean =
        ActionProcess.ALL in processes || proc in processes ||
            (ActionProcess.OTHER in processes && proc == ActionProcess.OTHER)
}
