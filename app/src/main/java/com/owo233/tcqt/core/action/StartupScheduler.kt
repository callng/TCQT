package com.owo233.tcqt.core.action

import android.app.Application
import android.view.Choreographer
import com.owo233.tcqt.core.dexkit.DexKitFinder
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ProcUtil
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.core.sync.SyncUtils

/**
 * 启动调度器：把非 [com.owo233.tcqt.core.action.ActionPriority.CRITICAL] 的功能安装从
 * `BaseApplicationImpl.onCreate` 的同步路径移出，避免宿主启动白屏时间随启用功能数量
 * 线性增长。
 *
 * 时序：
 * ```
 * onCreate Before ── ActionRegistry.runCritical()（同步，数量严格受控）
 *        │
 *        ├─ EARLY      onCreate 返回后立刻，后台线程
 *        ├─ DEFERRED   MAIN 等首帧后 / 后台进程立刻，后台分批
 *        ├─ BACKGROUND 最后一批，后台分批
 *        └─ DexKit     缓存缺失或主进程热更新时触发查找
 * ```
 */
internal object StartupScheduler {

    fun schedule(
        app: Application,
        proc: ActionProcess,
        plan: ActionPlan,
        needDexKitFind: Boolean,
        dexKitClassLoader: ClassLoader = HookEnv.hostClassLoader,
        forceFullDexKitFind: Boolean = false,
    ) {
        if (plan.isEmpty && !needDexKitFind) return

        // EARLY：onCreate 返回后立刻（主线程 Handler post，随即转入 IO 线程）。
        if (plan.early.isNotEmpty()) {
            SyncUtils.post {
                ModuleScope.launchIO("TCQT-Early") {
                    ActionRegistry.runEarly(app, proc, plan)
                }
            }
        }

        // DEFERRED + BACKGROUND：
        // MAIN 进程等首帧之后再装，避免与宿主首帧渲染抢 CPU；
        // 后台进程没有 UI（没有帧回调），立刻执行。
        if (plan.deferred.isNotEmpty() || plan.background.isNotEmpty()) {
            val runDeferred = {
                ModuleScope.launchIO("TCQT-Deferred") {
                    ActionRegistry.runDeferred(app, proc, plan)
                    ActionRegistry.runBackground(app, proc, plan)
                }
            }
            if (ProcUtil.isMain) {
                SyncUtils.runOnUiThread {
                    runCatching {
                        Choreographer.getInstance().postFrameCallback { runDeferred() }
                    }.onFailure {
                        Log.w("StartupScheduler: 首帧回调不可用，改为立即执行 Deferred", it)
                        runDeferred()
                    }
                }
            } else {
                runDeferred()
            }
        }

        // DexKit 缓存缺失或主进程检测到热补丁：触发后台查找，不阻塞 onCreate。
        if (needDexKitFind) {
            SyncUtils.post {
                ModuleScope.launchIO("TCQT-DexKit") {
                    DexKitFinder.doFind(dexKitClassLoader, forceFullDexKitFind)
                }
            }
        }
    }
}
