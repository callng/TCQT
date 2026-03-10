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
import com.owo233.tcqt.utils.callMethod
import com.owo233.tcqt.utils.callStaticMethod
import com.owo233.tcqt.utils.getObjectField
import com.owo233.tcqt.utils.getStaticObjectField
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.setObjectField
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

    private val moduleLoader by lazy(LazyThreadSafetyMode.NONE) {
        HookEnv.moduleClassLoader
    }

    private val hostLoader by lazy(LazyThreadSafetyMode.NONE) {
        HookEnv.hostClassLoader
    }

    private val sdkInt: Int
        get() = Build.VERSION.SDK_INT

    private val isAtLeastQ: Boolean
        get() = sdkInt >= Build.VERSION_CODES.Q

    private val isAtLeastS: Boolean
        get() = sdkInt >= Build.VERSION_CODES.S

    fun initForStubActivity(ctx: Context) {
        runCatching {
            val activityThread = currentActivityThread() ?: return
            hookInstrumentation(activityThread)
            hookMainHandler(activityThread)
            hookIActivityManager()
            hookIPackageManager(ctx, activityThread)
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
        val base = activityThread.getObjectField("mInstrumentation") as? Instrumentation ?: return
        if (base is ProxyInstrumentation) return
        activityThread.setObjectField("mInstrumentation", ProxyInstrumentation(base))
    }

    private fun hookMainHandler(activityThread: Any) {
        val handler = activityThread.getObjectField("mH") as? Handler ?: return

        val oldCallback = runCatching {
            FieldUtils.create(handler)
                .typed(Handler.Callback::class.java)
                .inParent(Handler::class.java)
                .getValue() as? Handler.Callback
        }.getOrNull()

        FieldUtils.create(handler)
            .named("mCallback")
            .inParent(Handler::class.java)
            .getField()
            ?.set(handler, Handler.Callback { msg ->
                when (msg.what) {
                    MSG_LAUNCH_ACTIVITY,
                    MSG_EXECUTE_TRANSACTION -> {
                        msg.obj?.let { handleLaunchMessage(it, msg.what) }
                    }
                }
                oldCallback?.handleMessage(msg) ?: false
            })
    }

    private fun hookIActivityManager() {
        val singleton = resolveActivityManagerSingleton() ?: return
        val singletonClass = "android.util.Singleton".toHostClass()

        val base = FieldUtils.create(singleton)
            .named("mInstance")
            .inParent(singletonClass)
            .getValue() ?: return

        val interfaceClass = if (isAtLeastQ) {
            "android.app.IActivityTaskManager".toHostClass()
        } else {
            "android.app.IActivityManager".toHostClass()
        }

        val proxy = Proxy.newProxyInstance(
            interfaceClass.classLoader,
            arrayOf(interfaceClass)
        ) { _, method, args ->
            if (method.name == "startActivity") {
                rewriteStartActivityIntent(args)
            }
            invokeOriginal(base, method, args)
        }

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

    private fun hookIPackageManager(ctx: Context, activityThread: Any) {
        val base = activityThread.getObjectField("sPackageManager") ?: return
        val interfaceClass = "android.content.pm.IPackageManager".toHostClass()

        val proxy = Proxy.newProxyInstance(
            interfaceClass.classLoader,
            arrayOf(interfaceClass)
        ) { _, method, args ->
            if (method.name == "getActivityInfo" && !args.isNullOrEmpty()) {
                val fake = maybeFakeActivityInfo(args)
                if (fake != null) {
                    return@newProxyInstance fake
                }
            }
            invokeOriginal(base, method, args)
        }

        activityThread.setObjectField("sPackageManager", proxy)
        runCatching {
            ctx.packageManager.setObjectField("mPM", proxy)
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
                    val singleton = atmClass.getStaticObjectField("IActivityTaskManagerSingleton")
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
                        .getStaticObjectField("IActivityManagerSingleton")
                }.getOrNull()
            }

            else -> {
                runCatching {
                    "android.app.ActivityManagerNative"
                        .toHostClass()
                        .getStaticObjectField("gDefault")
                }.getOrNull()
            }
        }
    }

    private fun handleLaunchMessage(recordOrTransaction: Any, what: Int) {
        when (what) {
            MSG_LAUNCH_ACTIVITY -> {
                val stubIntent = recordOrTransaction.getObjectField("intent") as? Intent ?: return
                val originalIntent = unwrapIntent(stubIntent) ?: return
                recordOrTransaction.setObjectField("intent", originalIntent)
            }

            MSG_EXECUTE_TRANSACTION -> {
                val callbacks = runCatching {
                    recordOrTransaction.callMethod("getCallbacks") as? List<*>
                }.getOrNull() ?: return

                for (item in callbacks) {
                    if (item == null) continue
                    if (!item.javaClass.name.contains("LaunchActivityItem")) continue

                    val stubIntent = item.getObjectField("mIntent") as? Intent ?: continue
                    val originalIntent = unwrapIntent(stubIntent) ?: continue

                    item.setObjectField("mIntent", originalIntent)

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
            record.setObjectField("intent", originalIntent)
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

    private class ProxyInstrumentation(
        private val base: Instrumentation
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
