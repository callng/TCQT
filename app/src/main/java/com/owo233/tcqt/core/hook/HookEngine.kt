package com.owo233.tcqt.core.hook

object HookEngineManager {

    lateinit var engine: IHookEngine

    val isInitialized: Boolean
        get() = this::engine.isInitialized
}
