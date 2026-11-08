package com.owo233.tcqt.annotations

/** Process selection shared by source annotations and generated runtime metadata. */
enum class ActionProcess { MSF, MAIN, TOOL, OPENSDK, QZONE, QQFAV, OTHER, ALL }

/**
 * 功能安装阶段，在 `@RegisterAction(priority = ...)` 中声明；默认使用 [DEFERRED]。
 *
 * 这里控制的是 install() 的时机与线程，不是 Hook 回调之间的优先级。
 * EARLY 与 DEFERRED 使用独立的后台任务，不能依靠枚举顺序建立功能间的依赖。
 */
enum class ActionPriority {
    /**
     * 在宿主 BaseApplicationImpl.onCreate 的 Before 回调中，在主线程同步安装。
     * 用于首次读取会在 onCreate 内发生、或启动任务会立即缓存结果的 Hook，例如登录身份。
     * 会直接增加宿主启动耗时，应只安装必要的轻量 Hook，避免扫描 DEX、网络请求等重操作。
     * 这个阶段仍晚于 Application 构造、attachBaseContext 和 ContentProvider 初始化。
     */
    CRITICAL,

    /**
     * 宿主 onCreate 成功返回后，通过主线程投递，随即转到 IO 线程安装。
     * 不等首帧；适合需要尽早安装、但允许错过 onCreate 内调用的功能。
     * 它是异步调度，不能保证早于宿主自身的启动任务或 Activity 创建。
     */
    EARLY,

    /**
     * 默认阶段：MAIN 等实际首帧信号后在后台分批安装，非 MAIN 进程在 onCreate 返回后立即调度。
     * 绘制监听不可用时立即调度，等待 4 秒仍无首帧时也会解除等待；每批在 Default 线程池执行。
     * 适合稍后才会使用的界面、交互 Hook，不能用于首次读取后就固定的启动状态。
     */
    DEFERRED,

    /**
     * 与 DEFERRED 共用启动任务，等该进程的 DEFERRED 全部完成后再在后台分批安装。
     * 适合不影响首屏、对安装时机不敏感的功能；不表示 Hook 回调也会运行在后台线程。
     */
    BACKGROUND,
}
