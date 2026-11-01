package com.owo233.tcqt.core.action

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import com.owo233.tcqt.core.env.ContextUtils
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.SyncUtils
import java.util.IdentityHashMap

/** Main-thread adapter. API 29+ waits for submission; older/software windows wait for traversal. */
internal class FirstFrameAwaiter(
    private val app: Application,
    private val ready: (FirstFrameGate.Completion) -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private val gate = FirstFrameGate()
    private class Watch(val view: View, val observer: ViewTreeObserver) {
        lateinit var draw: ViewTreeObserver.OnDrawListener
        var commit: Runnable? = null
        var commitObserver: ViewTreeObserver? = null
        var drawing = false
    }
    private val watches = IdentityHashMap<Activity, Watch>()
    private val timeout = Runnable { finish(FirstFrameGate.Reason.TIMEOUT) }

    fun start() {
        runCatching {
            app.registerActivityLifecycleCallbacks(this)
            check(SyncUtils.postDelayed(timeout, 4_000))
            // Loading QQ's static Activity helpers before they are ready can poison their class
            // initialization permanently. Observe actual Android Activity instances instead.
            ContextUtils.getCurrentActivity()?.let(::watch)
        }.onFailure {
            Log.w("FirstFrameAwaiter: draw observer unavailable", it)
            finish(FirstFrameGate.Reason.UNAVAILABLE)
        }
    }

    private fun watch(activity: Activity) {
        runCatching { watchWindow(activity) }.onFailure {
            Log.w("FirstFrameAwaiter: window observer unavailable", it)
            finish(FirstFrameGate.Reason.UNAVAILABLE)
        }
    }

    private fun watchWindow(activity: Activity) {
        if (gate.result != null || watches.containsKey(activity) || activity.isDestroyed || activity.isFinishing) return
        val view = activity.window.decorView
        val observer = view.viewTreeObserver
        trace("watch ${activity.javaClass.name}, attached=${view.isAttachedToWindow}, alive=${observer.isAlive}")
        if (!observer.isAlive) return
        val watch = Watch(view, observer)
        watch.draw = ViewTreeObserver.OnDrawListener {
            if (!watch.drawing && gate.result == null) {
                watch.drawing = true
                trace("draw hardware=${view.isHardwareAccelerated}")
                if (Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
                    val commit = Runnable { SyncUtils.post { finish(FirstFrameGate.Reason.FRAME_COMMIT) } }
                    watch.commit = commit
                    // The floating observer is merged/replaced when the window attaches.
                    val attachedObserver = view.viewTreeObserver
                    watch.commitObserver = attachedObserver
                    runCatching { attachedObserver.registerFrameCommitCallback(commit) }.onFailure {
                        Log.w("FirstFrameAwaiter: frame commit callback unavailable", it)
                        SyncUtils.post { finish(FirstFrameGate.Reason.DRAW_TRAVERSAL) }
                    }
                } else {
                    // post runs after the current traversal, rather than during OnDraw.
                    SyncUtils.post { finish(FirstFrameGate.Reason.DRAW_TRAVERSAL) }
                }
            }
        }
        watches[activity] = watch
        observer.addOnDrawListener(watch.draw)
        view.invalidate()
    }

    private fun remove(activity: Activity) {
        val watch = watches.remove(activity) ?: return
        runCatching {
            val current = watch.view.viewTreeObserver
            if (watch.observer.isAlive) watch.observer.removeOnDrawListener(watch.draw)
            if (current !== watch.observer && current.isAlive) current.removeOnDrawListener(watch.draw)
            if (Build.VERSION.SDK_INT >= 29 && watch.commitObserver?.isAlive == true) {
                watch.commit?.let { watch.commitObserver!!.unregisterFrameCommitCallback(it) }
            }
        }.onFailure { Log.w("FirstFrameAwaiter: observer cleanup failed", it) }
    }

    private fun finish(reason: FirstFrameGate.Reason) {
        if (!gate.complete(reason, System.nanoTime())) return
        trace("complete=$reason")
        SyncUtils.removeCallbacks(timeout)
        app.unregisterActivityLifecycleCallbacks(this)
        watches.keys.toList().forEach(::remove)
        ready(requireNotNull(gate.result))
    }

    private fun trace(message: String) {
        if (com.owo233.tcqt.core.env.TCQTBuild.DEBUG) android.util.Log.d("TCQT-Frame", message)
    }

    override fun onActivityResumed(activity: Activity) = watch(activity)
    override fun onActivityDestroyed(activity: Activity) = remove(activity)
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
