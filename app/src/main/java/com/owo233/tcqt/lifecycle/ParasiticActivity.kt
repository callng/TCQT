package com.owo233.tcqt.lifecycle

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.PersistableBundle
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.activity.BaseComposeActivity
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.utils.ResourcesUtils
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.callMethod
import com.owo233.tcqt.utils.reflect.callStaticMethod
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.getObject
import com.owo233.tcqt.utils.reflect.getObjectOrNull
import com.owo233.tcqt.utils.reflect.getStaticObject
import com.owo233.tcqt.utils.reflect.setObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
@Suppress("DEPRECATION")
object ParasiticActivity {

    private const val STUB_DEFAULT_ACTIVITY =
        "com.tencent.mobileqq.activity.photo.CameraPreviewActivity"

    private const val ACTIVITY_PROXY_INTENT = "ACTIVITY_PROXY_INTENT"

    private const val MSG_LAUNCH_ACTIVITY = 100
    private const val MSG_EXECUTE_TRANSACTION = 159

    private val moduleLoader: ClassLoader
        get() = HookEnv.moduleClassLoader

    private val hostLoader: ClassLoader
        get() = HookEnv.hostClassLoader

    private val sdkInt: Int
        get() = Build.VERSION.SDK_INT

    private val isAtLeastQ: Boolean
        get() = sdkInt >= Build.VERSION_CODES.Q

    private val isAtLeastS: Boolean
        get() = sdkInt >= Build.VERSION_CODES.S

    fun initForStubActivity(ctx: Context) {
        currentActivityThread()?.also {
            hookInstrumentation(it)
            hookMainHandler(it)
            hookIActivityManager()
            hookIPackageManager(ctx, it)
        }
    }

    private fun currentActivityThread(): Any? {
        return runCatching {
            "android.app.ActivityThread"
                .toHostClass()
                .callStaticMethod("currentActivityThread")
        }.getOrNull()
    }

    private fun hookInstrumentation(activityThread: Any) {
        var base = activityThread.getObject("mInstrumentation") as? Instrumentation ?: return
        while (isModuleClass(base.javaClass)) {
            val field = runCatching { base.javaClass.getDeclaredField("base") }.getOrNull()
                ?: base.javaClass.declaredFields.firstOrNull { it.type == Instrumentation::class.java }
            if (field != null) {
                field.isAccessible = true
                base = field.get(base) as? Instrumentation ?: break
            } else {
                break
            }
        }
        activityThread.setObject("mInstrumentation", ProxyInstrumentation(base))
    }

    private class ProxyCallback(val base: Handler.Callback?) : Handler.Callback {
        override fun handleMessage(msg: android.os.Message): Boolean {
            when (msg.what) {
                MSG_LAUNCH_ACTIVITY,
                MSG_EXECUTE_TRANSACTION -> {
                    msg.obj?.let { handleLaunchMessage(it, msg.what) }
                }
            }
            return base?.handleMessage(msg) ?: false
        }
    }

    private fun hookMainHandler(activityThread: Any) {
        val handler = activityThread.getObject("mH") as? Handler ?: return

        var oldCallback = runCatching {
            FieldUtils.create(handler)
                .typed(Handler.Callback::class.java)
                .inParent(Handler::class.java)
                .getValue() as? Handler.Callback
        }.getOrNull()

        while (oldCallback != null && isModuleClass(oldCallback.javaClass)) {
            val field = runCatching { oldCallback.javaClass.getDeclaredField("base") }.getOrNull()
                ?: oldCallback.javaClass.declaredFields.firstOrNull { it.type == Handler.Callback::class.java }
            if (field != null) {
                field.isAccessible = true
                oldCallback = field.get(oldCallback) as? Handler.Callback
            } else {
                break
            }
        }

        FieldUtils.create(handler)
            .named("mCallback")
            .inParent(Handler::class.java)
            .getField()
            ?.set(handler, ProxyCallback(oldCallback))
    }

    private class ActivityManagerInvocationHandler(val base: Any) : java.lang.reflect.InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            if (method.name == "startActivity") {
                rewriteStartActivityIntent(args)
            }
            return invokeOriginal(base, method, args)
        }
    }

    private fun hookIActivityManager() {
        val singleton = resolveActivityManagerSingleton() ?: return
        val singletonClass = "android.util.Singleton".toHostClass()

        var base = FieldUtils.create(singleton)
            .named("mInstance")
            .inParent(singletonClass)
            .getValue() ?: return

        while (Proxy.isProxyClass(base.javaClass)) {
            val ih = Proxy.getInvocationHandler(base)
            if (isModuleClass(ih.javaClass)) {
                val field = runCatching { ih.javaClass.getDeclaredField("base") }.getOrNull()
                    ?: ih.javaClass.declaredFields.firstOrNull { it.type == Any::class.java }
                if (field != null) {
                    field.isAccessible = true
                    base = field.get(ih) ?: break
                } else {
                    break
                }
            } else {
                break
            }
        }

        val interfaceClass = if (isAtLeastQ) {
            "android.app.IActivityTaskManager".toHostClass()
        } else {
            "android.app.IActivityManager".toHostClass()
        }

        val proxy = Proxy.newProxyInstance(
            interfaceClass.classLoader,
            arrayOf(interfaceClass),
            ActivityManagerInvocationHandler(base)
        )

        FieldUtils.create(singleton)
            .named("mInstance")
            .inParent(singletonClass)
            .getField()
            ?.set(singleton, proxy)
    }

    private fun rewriteStartActivityIntent(args: Array<Any?>?) {
        if (args.isNullOrEmpty()) return
        val index = args.indexOfFirst { it is Intent }
        if (index < 0) return

        val raw = args[index] as? Intent ?: return
        val component = raw.component ?: return

        if (component.packageName != HookEnv.hostAppPackageName) return
        if (!isTargetActivity(component.className)) return

        args[index] = Intent().apply {
            setClassName(component.packageName, STUB_DEFAULT_ACTIVITY)
            putExtra(ACTIVITY_PROXY_INTENT, raw)
            flags = raw.flags
        }
    }

    private class PackageManagerInvocationHandler(val base: Any) : java.lang.reflect.InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
            if (method.name == "getActivityInfo" && !args.isNullOrEmpty()) {
                val fake = maybeFakeActivityInfo(args)
                if (fake != null) {
                    return fake
                }
            }
            return invokeOriginal(base, method, args)
        }
    }

    private fun hookIPackageManager(ctx: Context, activityThread: Any) {
        var base = activityThread.getObjectOrNull("sPackageManager") ?: return
        while (Proxy.isProxyClass(base.javaClass)) {
            val ih = Proxy.getInvocationHandler(base)
            if (isModuleClass(ih.javaClass)) {
                val field = runCatching { ih.javaClass.getDeclaredField("base") }.getOrNull()
                    ?: ih.javaClass.declaredFields.firstOrNull { it.type == Any::class.java }
                if (field != null) {
                    field.isAccessible = true
                    base = field.get(ih) ?: break
                } else {
                    break
                }
            } else {
                break
            }
        }

        val interfaceClass = "android.content.pm.IPackageManager".toHostClass()
        val proxy = Proxy.newProxyInstance(
            interfaceClass.classLoader,
            arrayOf(interfaceClass),
            PackageManagerInvocationHandler(base)
        )

        activityThread.setObject("sPackageManager", proxy)
        runCatching {
            ctx.packageManager.setObject("mPM", proxy)
        }
    }

    private fun maybeFakeActivityInfo(args: Array<Any?>): Any? {
        val component = args.getOrNull(0) as? ComponentName ?: return null
        if (component.packageName != HookEnv.hostAppPackageName) return null
        if (!isTargetActivity(component.className)) return null

        val flags = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
        return CounterfeitActivityInfoFactory.makeProxyActivityInfo(
            component.className,
            flags
        )
    }

    private fun resolveActivityManagerSingleton(): Any? {
        return when {
            isAtLeastQ -> {
                runCatching {
                    val atmClass = "android.app.ActivityTaskManager".toHostClass()
                    val singleton = atmClass.getStaticObject("IActivityTaskManagerSingleton")
                    "android.util.Singleton".toHostClass()
                        .findMethod { name = "get" }
                        .invoke(singleton)
                    singleton
                }.getOrNull()
            }

            sdkInt >= Build.VERSION_CODES.O -> {
                runCatching {
                    "android.app.ActivityManager"
                        .toHostClass()
                        .getStaticObject("IActivityManagerSingleton")
                }.getOrNull()
            }

            else -> {
                runCatching {
                    "android.app.ActivityManagerNative"
                        .toHostClass()
                        .getStaticObject("gDefault")
                }.getOrNull()
            }
        }
    }

    private fun handleLaunchMessage(recordOrTransaction: Any, what: Int) {
        when (what) {
            MSG_LAUNCH_ACTIVITY -> {
                val stubIntent = recordOrTransaction.getObject("intent") as? Intent ?: return
                val originalIntent = unwrapIntent(stubIntent) ?: return
                recordOrTransaction.setObject("intent", originalIntent)
            }

            MSG_EXECUTE_TRANSACTION -> {
                val callbacks = runCatching {
                    recordOrTransaction.callMethod("getCallbacks") as? List<*>
                }.getOrNull() ?: return

                for (item in callbacks) {
                    if (item == null) continue
                    if (!item.javaClass.name.contains("LaunchActivityItem")) continue

                    val stubIntent = item.getObject("mIntent") as? Intent ?: continue
                    val originalIntent = unwrapIntent(stubIntent) ?: continue

                    item.setObject("mIntent", originalIntent)

                    if (isAtLeastS) {
                        fixActivityClientRecordForApi31(recordOrTransaction, originalIntent)
                    }
                    break
                }
            }
        }
    }

    private fun fixActivityClientRecordForApi31(transaction: Any, originalIntent: Intent) {
        runCatching {
            val token = transaction.callMethod("getActivityToken") as? IBinder ?: return
            val activityThread = currentActivityThread() ?: return
            val record = activityThread.callMethod("getLaunchingActivity", token) ?: return
            record.setObject("intent", originalIntent)
        }
    }

    private fun unwrapIntent(intent: Intent): Intent? {
        return runCatching {
            val clone = intent.clone() as Intent
            clone.extras?.classLoader = hostLoader

            if (!clone.hasExtra(ACTIVITY_PROXY_INTENT)) {
                return@runCatching null
            }

            clone.getParcelableExtra<Intent>(ACTIVITY_PROXY_INTENT)?.apply {
                extras?.classLoader = moduleLoader
            }
        }.getOrNull()
    }

    private fun isTargetActivity(className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        if (DynamicActivityRegistry.contains(className)) return true
        if (!className.startsWith(TCQTBuild.APP_ID)) return false

        return runCatching {
            val clazz = moduleLoader.loadClass(className)
            BaseComposeActivity::class.java.isAssignableFrom(clazz)
        }.getOrElse { false }
    }

    private fun invokeOriginal(target: Any, method: Method, args: Array<Any?>?): Any? {
        return try {
            method.invoke(target, *args.orEmpty())
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun isModuleClass(clazz: Class<*>): Boolean {
        val loader = clazz.classLoader ?: return false
        val hostLoader = HookEnv.hostClassLoader
        var current: ClassLoader? = hostLoader
        while (current != null) {
            if (loader == current) return false
            current = current.parent
        }
        return true
    }

    private class ProxyInstrumentation(
        val base: Instrumentation
    ) : Instrumentation() {

        override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
            DynamicActivityRegistry.getActivityClass(className)?.let { activityClass ->
                return activityClass.getDeclaredConstructor().newInstance()
            }

            return runCatching {
                base.newActivity(cl, className, intent)
            }.getOrElse {
                ParasiticActivity::class.java.classLoader!!
                    .loadClass(className)
                    .getDeclaredConstructor()
                    .newInstance() as Activity
            }
        }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
            prepareActivityCreate(activity, icicle)
            base.callActivityOnCreate(activity, icicle)
        }

        override fun callActivityOnCreate(
            activity: Activity,
            icicle: Bundle?,
            persistentState: PersistableBundle?
        ) {
            prepareActivityCreate(activity, icicle)
            base.callActivityOnCreate(activity, icicle, persistentState)
        }

        private fun prepareActivityCreate(activity: Activity, icicle: Bundle?) {
            ResourcesUtils.injectResourcesToContext(activity.resources)
            if (icicle != null && isTargetActivity(activity.javaClass.name)) {
                icicle.classLoader = moduleLoader
            }
        }
    }
}
