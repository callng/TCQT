package com.owo233.tcqt.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RegisterAction(
    /**
     * 是否启用，默认为true
     */
    val enabled: Boolean = true
)
