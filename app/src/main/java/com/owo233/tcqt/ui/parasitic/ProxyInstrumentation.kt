package com.owo233.tcqt.ui.parasitic

import android.app.Activity
import android.app.Application
import android.app.ComponentCaller
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PersistableBundle
import android.os.UserHandle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ResourcesUtils
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class ProxyInstrumentation(val base: Instrumentation) : Instrumentation(), TCQTProxyMarker {
    init {
        // SDK 未公开/未来新增的实例方法仍可能由 framework 调用；保留它们所需的内部状态。
        // 只复制 Instrumentation 自身字段，不扫描宿主 Instrumentation 的对象图。
        Instrumentation::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true
                field.set(this, field.get(base))
            }
    }

    override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
        val target = intent.component
        if (target?.packageName == HookEnv.hostAppPackageName && target.className == className) {
            ActivityProxyIntents.activityClass(className)?.let {
                // 委托原 Instrumentation，保留 AppComponentFactory 等宿主创建流程。
                return try {
                    base.newActivity(it.classLoader, className, intent)
                } catch (_: ClassNotFoundException) {
                    // 某些宿主工厂忽略传入的 loader；只对已确认的模块类回退。
                    it.getDeclaredConstructor().newInstance()
                }
            }
        }
        // 宿主自身的构造异常必须原样传播，不能用模块 ClassLoader 重试掩盖异常。
        return base.newActivity(cl, className, intent)
    }

    override fun newActivity(
        clazz: Class<*>, context: Context, token: IBinder?, application: Application?,
        intent: Intent, info: ActivityInfo, title: CharSequence?, parent: Activity?,
        id: String?, lastNonConfigurationInstance: Any?,
    ): Activity = base.newActivity(
        clazz, context, token, application, intent, info, title, parent, id, lastNonConfigurationInstance,
    )

    private fun prepare(activity: Activity, vararg states: Bundle?) {
        if (ActivityProxyIntents.activityClass(activity.javaClass.name) == null) return
        ResourcesUtils.injectResourcesToContext(activity.resources)
        activity.intent?.setExtrasClassLoader(activity.classLoader)
        states.forEach { state ->
            state?.classLoader = activity.classLoader
            state?.getBundle("android:viewHierarchyState")?.classLoader = activity.classLoader
        }
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        prepare(activity, icicle)
        base.callActivityOnCreate(activity, icicle)
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?, persistentState: PersistableBundle?) {
        prepare(activity, icicle)
        base.callActivityOnCreate(activity, icicle, persistentState)
    }

    override fun callActivityOnRestoreInstanceState(activity: Activity, savedInstanceState: Bundle) {
        prepare(activity, savedInstanceState)
        base.callActivityOnRestoreInstanceState(activity, savedInstanceState)
    }

    override fun callActivityOnRestoreInstanceState(
        activity: Activity, savedInstanceState: Bundle?, persistentState: PersistableBundle?,
    ) {
        prepare(activity, savedInstanceState)
        base.callActivityOnRestoreInstanceState(activity, savedInstanceState, persistentState)
    }

    override fun callActivityOnPostCreate(activity: Activity, savedInstanceState: Bundle?) {
        prepare(activity, savedInstanceState)
        base.callActivityOnPostCreate(activity, savedInstanceState)
    }

    override fun callActivityOnPostCreate(
        activity: Activity, savedInstanceState: Bundle?, persistentState: PersistableBundle?,
    ) {
        prepare(activity, savedInstanceState)
        base.callActivityOnPostCreate(activity, savedInstanceState, persistentState)
    }

    override fun callActivityOnNewIntent(activity: Activity, intent: Intent) {
        prepare(activity)
        if (ActivityProxyIntents.activityClass(activity.javaClass.name) != null) {
            intent.setExtrasClassLoader(activity.classLoader)
        }
        base.callActivityOnNewIntent(activity, intent)
    }

    @RequiresApi(35)
    override fun callActivityOnNewIntent(activity: Activity, intent: Intent, caller: ComponentCaller) {
        prepare(activity)
        if (ActivityProxyIntents.activityClass(activity.javaClass.name) != null) {
            intent.setExtrasClassLoader(activity.classLoader)
        }
        base.callActivityOnNewIntent(activity, intent, caller)
    }

    override fun callActivityOnSaveInstanceState(activity: Activity, outState: Bundle) {
        base.callActivityOnSaveInstanceState(activity, outState)
    }

    override fun callActivityOnSaveInstanceState(activity: Activity, outState: Bundle, outPersistentState: PersistableBundle) {
        base.callActivityOnSaveInstanceState(activity, outState, outPersistentState)
    }

    override fun callActivityOnStart(activity: Activity) = base.callActivityOnStart(activity)
    override fun callActivityOnRestart(activity: Activity) = base.callActivityOnRestart(activity)
    override fun callActivityOnResume(activity: Activity) = base.callActivityOnResume(activity)
    override fun callActivityOnPause(activity: Activity) = base.callActivityOnPause(activity)
    override fun callActivityOnStop(activity: Activity) = base.callActivityOnStop(activity)
    override fun callActivityOnDestroy(activity: Activity) = base.callActivityOnDestroy(activity)
    override fun callActivityOnUserLeaving(activity: Activity) = base.callActivityOnUserLeaving(activity)
    @RequiresApi(30)
    override fun callActivityOnPictureInPictureRequested(activity: Activity) = base.callActivityOnPictureInPictureRequested(activity)
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        base.newApplication(cl, className, context)
    override fun callApplicationOnCreate(app: Application) = base.callApplicationOnCreate(app)

    override fun onCreate(arguments: Bundle?) = base.onCreate(arguments)
    override fun start() = base.start()
    override fun onStart() = base.onStart()
    override fun onException(obj: Any?, e: Throwable) = base.onException(obj, e)
    override fun sendStatus(resultCode: Int, results: Bundle?) = base.sendStatus(resultCode, results)
    override fun addResults(results: Bundle?) = base.addResults(results)
    override fun finish(resultCode: Int, results: Bundle?) = base.finish(resultCode, results)
    override fun setAutomaticPerformanceSnapshots() = base.setAutomaticPerformanceSnapshots()
    override fun startPerformanceSnapshot() = base.startPerformanceSnapshot()
    override fun endPerformanceSnapshot() = base.endPerformanceSnapshot()
    override fun onDestroy() = base.onDestroy()
    override fun getContext(): Context = base.context
    override fun getComponentName() = base.componentName
    override fun getTargetContext(): Context = base.targetContext
    override fun getProcessName(): String = base.processName
    override fun isProfiling() = base.isProfiling
    override fun startProfiling() = base.startProfiling()
    override fun stopProfiling() = base.stopProfiling()
    override fun setInTouchMode(inTouch: Boolean) = base.setInTouchMode(inTouch)
    @RequiresApi(33)
    override fun resetInTouchMode() = base.resetInTouchMode()
    override fun waitForIdle(recipient: Runnable?) = base.waitForIdle(recipient)
    override fun waitForIdleSync() = base.waitForIdleSync()
    override fun runOnMainSync(runner: Runnable) = base.runOnMainSync(runner)
    override fun startActivitySync(intent: Intent): Activity = base.startActivitySync(intent)
    @RequiresApi(28)
    override fun startActivitySync(intent: Intent, options: Bundle?): Activity = base.startActivitySync(intent, options)
    override fun addMonitor(monitor: ActivityMonitor) = base.addMonitor(monitor)
    override fun addMonitor(cls: String, result: ActivityResult?, block: Boolean): ActivityMonitor = base.addMonitor(cls, result, block)
    override fun addMonitor(filter: IntentFilter, result: ActivityResult?, block: Boolean): ActivityMonitor = base.addMonitor(filter, result, block)
    override fun checkMonitorHit(monitor: ActivityMonitor, minHits: Int) = base.checkMonitorHit(monitor, minHits)
    override fun waitForMonitor(monitor: ActivityMonitor): Activity = base.waitForMonitor(monitor)
    override fun waitForMonitorWithTimeout(monitor: ActivityMonitor, timeOut: Long): Activity = base.waitForMonitorWithTimeout(monitor, timeOut)
    override fun removeMonitor(monitor: ActivityMonitor) = base.removeMonitor(monitor)
    override fun invokeContextMenuAction(targetActivity: Activity, id: Int, flag: Int) = base.invokeContextMenuAction(targetActivity, id, flag)
    override fun invokeMenuActionSync(targetActivity: Activity, id: Int, flag: Int) = base.invokeMenuActionSync(targetActivity, id, flag)
    override fun sendCharacterSync(keyCode: Int) = base.sendCharacterSync(keyCode)
    override fun sendKeyDownUpSync(key: Int) = base.sendKeyDownUpSync(key)
    override fun sendKeySync(event: KeyEvent) = base.sendKeySync(event)
    override fun sendPointerSync(event: MotionEvent) = base.sendPointerSync(event)
    override fun sendStringSync(text: String) = base.sendStringSync(text)
    override fun sendTrackballEventSync(event: MotionEvent) = base.sendTrackballEventSync(event)
    override fun startAllocCounting() = base.startAllocCounting()
    override fun stopAllocCounting() = base.stopAllocCounting()
    override fun getAllocCounts(): Bundle = base.allocCounts
    override fun getBinderCounts(): Bundle = base.binderCounts
    override fun getUiAutomation() = base.uiAutomation
    override fun getUiAutomation(flags: Int) = base.getUiAutomation(flags)
    override fun acquireLooperManager(looper: Looper) = base.acquireLooperManager(looper)

    // SDK 隐藏了这些方法，不能使用 override 关键字；在 ART 上按原签名覆盖并委托。
    // Activity.startActivityForResult / startActivities 会直接调用它们。
    fun execStartActivity(who: Context, contextThread: IBinder?, token: IBinder?, target: Activity?, intent: Intent, requestCode: Int, options: Bundle?): ActivityResult? =
        execStartActivity(Activity::class.java, who, contextThread, token, target, intent, requestCode, options)

    fun execStartActivity(who: Context, contextThread: IBinder?, token: IBinder?, target: String?, intent: Intent, requestCode: Int, options: Bundle?): ActivityResult? =
        execStartActivity(String::class.java, who, contextThread, token, target, intent, requestCode, options)

    fun execStartActivity(who: Context, contextThread: IBinder?, token: IBinder?, target: String?, intent: Intent, requestCode: Int, options: Bundle?, user: UserHandle): ActivityResult? =
        invokeBase(
            "execStartActivity",
            arrayOf(Context::class.java, IBinder::class.java, IBinder::class.java, String::class.java,
                Intent::class.java, Int::class.javaPrimitiveType!!, Bundle::class.java, UserHandle::class.java),
            who, contextThread, token, target, intent, requestCode, options, user,
        ) as ActivityResult?

    private fun execStartActivity(targetType: Class<*>, who: Context, contextThread: IBinder?, token: IBinder?, target: Any?, intent: Intent, requestCode: Int, options: Bundle?): ActivityResult? =
        invokeBase(
            "execStartActivity",
            arrayOf(Context::class.java, IBinder::class.java, IBinder::class.java, targetType,
                Intent::class.java, Int::class.javaPrimitiveType!!, Bundle::class.java),
            who, contextThread, token, target, intent, requestCode, options,
        ) as ActivityResult?

    fun execStartActivities(who: Context, contextThread: IBinder?, token: IBinder?, target: Activity?, intents: Array<Intent>, options: Bundle?) {
        invokeBase(
            "execStartActivities",
            arrayOf(Context::class.java, IBinder::class.java, IBinder::class.java, Activity::class.java,
                Array<Intent>::class.java, Bundle::class.java),
            who, contextThread, token, target, intents, options,
        )
    }

    fun execStartActivitiesAsUser(who: Context, contextThread: IBinder?, token: IBinder?, target: Activity?, intents: Array<Intent>, options: Bundle?, userId: Int): Int =
        invokeBase(
            "execStartActivitiesAsUser",
            arrayOf(Context::class.java, IBinder::class.java, IBinder::class.java, Activity::class.java,
                Array<Intent>::class.java, Bundle::class.java, Int::class.javaPrimitiveType!!),
            who, contextThread, token, target, intents, options, userId,
        ) as Int

    private fun invokeBase(name: String, types: Array<Class<*>>, vararg args: Any?): Any? = try {
        // 精确签名避免 target=null 时错选 Activity/String 重载。
        base.javaClass.getMethod(name, *types).apply { isAccessible = true }.invoke(base, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
