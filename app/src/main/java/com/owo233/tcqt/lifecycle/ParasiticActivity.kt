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
import androidx.annotation.Keep
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
import java.util.Collections.newSetFromMap
import java.util.IdentityHashMap

@Keep
interface TCQTProxyMarker

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
        val base = activityThread.getObject("mInstrumentation") as? Instrumentation ?: return
        val cleaned = cleanOurProxy(base) as? Instrumentation ?: base
        activityThread.setObject("mInstrumentation", ProxyInstrumentation(cleaned))
    }

    private class ProxyCallback(val base: Handler.Callback?) : Handler.Callback, TCQTProxyMarker {
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

        val oldCallback = runCatching {
            FieldUtils.create(handler)
                .typed(Handler.Callback::class.java)
                .inParent(Handler::class.java)
                .getValue() as? Handler.Callback
        }.getOrNull()

        val cleaned = cleanOurProxy(oldCallback) as? Handler.Callback

        FieldUtils.create(handler)
            .named("mCallback")
            .inParent(Handler::class.java)
            .getField()
            ?.set(handler, ProxyCallback(cleaned))
    }

    private class ActivityManagerInvocationHandler(val base: Any) : java.lang.reflect.InvocationHandler, TCQTProxyMarker {
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

        val base = FieldUtils.create(singleton)
            .named("mInstance")
            .inParent(singletonClass)
            .getValue() ?: return

        val cleaned = cleanOurProxy(base) ?: base

        val interfaceClass = if (isAtLeastQ) {
            "android.app.IActivityTaskManager".toHostClass()
        } else {
            "android.app.IActivityManager".toHostClass()
        }

        val proxy = Proxy.newProxyInstance(
            interfaceClass.classLoader,
            arrayOf(interfaceClass),
            ActivityManagerInvocationHandler(cleaned)
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

    private class PackageManagerInvocationHandler(val base: Any) : java.lang.reflect.InvocationHandler, TCQTProxyMarker {
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
        val base = activityThread.getObjectOrNull("sPackageManager") ?: return
        val cleaned = cleanOurProxy(base) ?: base

        val interfaceClass = "android.content.pm.IPackageManager".toHostClass()
        val proxy = Proxy.newProxyInstance(
            interfaceClass.classLoader,
            arrayOf(interfaceClass),
            PackageManagerInvocationHandler(cleaned)
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

    private fun getBaseValue(obj: Any): Any? {
        val field = runCatching { obj.javaClass.getDeclaredField("base") }.getOrNull()
            ?: obj.javaClass.declaredFields.firstOrNull { it.name == "base" }
            ?: return null
        field.isAccessible = true
        return field.get(obj)
    }

    private fun isOurProxyClass(clazz: Class<*>): Boolean {
        var current: Class<*>? = clazz
        val markerName = TCQTProxyMarker::class.java.name
        while (current != null) {
            for (iface in current.interfaces) {
                if (iface.name == markerName) {
                    return true
                }
            }
            current = current.superclass
        }
        return false
    }

    private fun cleanOurProxy(obj: Any?, visited: MutableSet<Any> = newSetFromMap(IdentityHashMap())): Any? {
        if (obj == null) return null
        if (!visited.add(obj)) return obj

        if (isOurProxyClass(obj.javaClass)) {
            val baseValue = getBaseValue(obj)
            return if (baseValue != null) cleanOurProxy(baseValue, visited) else obj
        }

        if (Proxy.isProxyClass(obj.javaClass)) {
            val ih = Proxy.getInvocationHandler(obj)
            if (isOurProxyClass(ih.javaClass)) {
                val baseValue = getBaseValue(ih)
                return if (baseValue != null) cleanOurProxy(baseValue, visited) else obj
            }
            cleanFields(ih, visited)
            return obj
        }

        cleanFields(obj, visited)
        return obj
    }

    private fun cleanFields(obj: Any, visited: MutableSet<Any>) {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                field.isAccessible = true
                val value = runCatching { field.get(obj) }.getOrNull() ?: continue

                val className = value.javaClass.name
                if (!className.startsWith("java.") &&
                    !className.startsWith("android.") &&
                    !className.startsWith("kotlin.")
                ) {
                    val cleaned = cleanOurProxy(value, visited)
                    if (cleaned !== value) {
                        runCatching { field.set(obj, cleaned) }
                    }
                }
            }
            clazz = clazz.superclass
        }
    }

    private class ProxyInstrumentation(
        val base: Instrumentation
    ) : Instrumentation(), TCQTProxyMarker {

        override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
            DynamicActivityRegistry.getActivityClass(className)?.let { activityClass ->
                return activityClass.getDeclaredConstructor().newInstance()
            }

            if (isTargetActivity(className)) {
                return ParasiticActivity::class.java.classLoader!!
                    .loadClass(className)
                    .getDeclaredConstructor()
                    .newInstance() as Activity
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
