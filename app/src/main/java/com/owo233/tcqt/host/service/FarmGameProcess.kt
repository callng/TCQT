package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.reflect.findMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** Use QQ's process registry rather than terminating every mini process. */
internal class FarmGameProcess {

    private val infoClass = loadOrThrow("com.tencent.qqmini.sdk.launcher.model.MiniAppInfo")
    private val baseInfoClass = loadOrThrow("com.tencent.qqmini.sdk.launcher.model.MiniAppBaseInfo")
    private val launcherClass = loadOrThrow("com.tencent.qqmini.sdk.server.LaunchManagerService")
    private val strategyClass = loadOrThrow("com.tencent.qqmini.sdk.server.launch.GameLaunchStrategy")
    private val processInfoClass = loadOrThrow(
        $$"com.tencent.qqmini.sdk.server.launch.GameLaunchStrategy$RunningProcessInfo",
    )
    private val identityClass = loadOrThrow("com.tencent.qqmini.sdk.server.launch.AppIdentity")
    private val companion = strategyClass.getField("Companion").get(null)
    private val toIdentity = companion.javaClass.declaredMethods.single {
        it.name.startsWith("toId$") && it.parameterTypes.contentEquals(arrayOf(baseInfoClass))
    }
    private val strategyFor = launcherClass.findMethod {
        name = "getLaunchStrategy"; paramTypes(baseInfoClass)
    }
    private val inspect = strategyClass.declaredMethods.single {
        it.name.startsWith("getInspectRunningProcess$") && it.parameterCount == 0
    }
    private val exited = strategyClass.declaredMethods.single {
        it.name.startsWith("onProcessExited$") && it.parameterTypes.contentEquals(arrayOf(processInfoClass))
    }
    private val hasIdentity = processInfoClass.getMethod("has", identityClass)
    private val getPid = processInfoClass.getMethod("getPid")
    private val getCount = processInfoClass.getMethod("getRunningAppCount")
    private val kill = strategyClass.getMethod("killMiniAppProcess", infoClass)

    suspend fun reset(launcher: Any, info: Any) {
        val strategy = checkNotNull(strategyFor.invoke(launcher, info)) { "QQ 农场启动服务尚未就绪" }
        check(strategyClass.isInstance(strategy)) { "此农场启动方式暂不支持独立账号" }
        val identity = toIdentity.invoke(companion, info)
        val records = synchronized(strategy) { (inspect.invoke(strategy) as List<*>).toList() }
        val record = records.firstOrNull { hasIdentity.invoke(it, identity) == true } ?: return
        check((getCount.invoke(record) as Int) <= 1) { "请先关闭与农场共用进程的其他小游戏" }
        val pid = getPid.invoke(record) as Int
        if (pid > 0) {
            kill.invoke(strategy, info)
            // Wait for actual exit; a new account must never reuse the old game's JS session.
            withTimeout(3_000.milliseconds) {
                while (File("/proc/$pid").exists()) delay(50.milliseconds)
            }
        }
        // QQ's process-death callback can arrive later. Removal is idempotent in 9.3.85.
        exited.invoke(strategy, record)
    }
}
