package com.owo233.tcqt.loader

internal object InjectionGuard {

    private const val KEY = "tcqt.injection.mode"

    const val MODE_ZYGISK = "zygisk"
    const val MODE_XPOSED = "xposed"

    /**
     * 尝试成为本进程唯一注入方
     *
     * @return true 表示当前 mode 可以继续初始化；false 表示已有其他 mode 接管
     */
    fun tryAcquire(mode: String): Boolean {
        synchronized(KEY.intern()) {
            val current = System.getProperty(KEY)
            if (current == null) {
                System.setProperty(KEY, mode)
                return true
            }
            return current == mode
        }
    }

    fun isActive(mode: String): Boolean = System.getProperty(KEY) == mode

    fun activeMode(): String? = System.getProperty(KEY)
}
