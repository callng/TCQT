package com.owo233.tcqt

import android.app.Application
import android.view.Choreographer
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.ModuleScope
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.utils.SyncUtils
import com.owo233.tcqt.utils.dexkit.DexKitFinder
import com.owo233.tcqt.utils.log.Log

/**
 * 启动调度器：把非 [com.owo233.tcqt.ext.ActionPriority.CRITICAL] 的功能安装从
 * `BaseApplicationImpl.onCreate` 的同步路径移出，从而让宿主启动白屏时间
 * 不再随启用功能数量线性增长。
 *
 * 时序：
 * ```
 * onCreate Before ── ActionManager.runCritical()（同步，数量严格受控）
 *        │
 *        ├─ EARLY      onCreate 返回后立刻，后台线程
 *        ├─ DEFERRED   MAIN 等首帧后 / 后台进程立刻，后台分批
 *        ├─ BACKGROUND 最后一批，后台分批
 *        └─ DexKit     缓存缺失时触发查找（仍走 MainFragment.onResume 旧流程）
 * ```
 */
internal object StartupScheduler {

    fun schedule(
        app: Application,
        proc: ActionProcess,
        plan: ActionPlan,
        needDexKitFind: Boolean,
    ) {
        if (plan.isEmpty && !needDexKitFind) return

        // EARLY：onCreate 返回后立刻（主线程 Handler post，随即转入 IO 线程）。
        if (plan.early.isNotEmpty()) {
            SyncUtils.post {
                ModuleScope.launchIO("TCQT-Early") {
                    ActionManager.runEarly(app, proc, plan)
                }
            }
        }

        // DEFERRED + BACKGROUND：
        // MAIN 进程等首帧之后再装，避免与宿主首帧渲染抢 CPU；
        // 后台进程没有 UI（没有帧回调），立刻执行。
        if (plan.deferred.isNotEmpty() || plan.background.isNotEmpty()) {
            val runDeferred = {
                ModuleScope.launchIO("TCQT-Deferred") {
                    ActionManager.runDeferred(app, proc, plan)
                    ActionManager.runBackground(app, proc, plan)
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

        // DexKit 缓存缺失：触发后台查找（自身已在 MainFragment.onResume 之后异步执行），
        // 不再阻塞 onCreate。
        if (needDexKitFind) {
            ModuleScope.launchIO("TCQT-DexKit") {
                DexKitFinder.doFind()
            }
        }
    }
}
