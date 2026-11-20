package com.owo233.tcqt.core.env

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.owo233.tcqt.core.log.Log
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * QQ 内置轻提示（QQToast）的反射封装。
 *
 * 目标入口与 QAuxv 的 `io.github.qauxv.util.Toasts` 相同：
 * `com.tencent.mobileqq.widget.QQToast.makeText(Context, int, CharSequence, int): Toast`，
 * 拿到 Toast 后再 `show()`。QQ 版本会混淆这个方法名（`a` / `b` / `makeText`），
 * 因此优先按签名查找，找不到再按候选名兜底；类本身找不到时再尝试小程序 SDK 的同名类。
 *
 * 任一步失败都回落系统 Toast，并写日志说明具体原因，避免宿主版本差异导致静默无提示。
 */
internal object QQToastCompat {

    private const val HOST_TOAST = "com.tencent.mobileqq.widget.QQToast"
    private const val MINI_TOAST = "com.tencent.qqmini.sdk.core.widget.QQToast"

    const val TYPE_INFO = 0
    const val TYPE_ERROR = 1
    const val TYPE_SUCCESS = 2
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var cachedClass: Class<*>? = null

    @Volatile
    private var cachedMakeText: Method? = null

    fun info(message: String) = show(TYPE_INFO, message)

    fun success(message: String) = show(TYPE_SUCCESS, message)

    fun error(message: String) = show(TYPE_ERROR, message)

    fun show(type: Int, message: String, duration: Int = LENGTH_SHORT) {
        val context = runCatching { HookEnv.hostAppContext }.getOrNull()
        if (context == null) {
            Log.w("QQToast 未执行：宿主 Context 尚未就绪")
            return
        }
        if (HostBridge.topActivity() == null) {
            // Toast 窗口不要求 Activity，但 Android 11+ 会拦截后台应用的自定义 Toast。
            Log.i("QQToast 当前没有前台界面，后台时可能不显示")
        }
        val action = Runnable {
            runCatching { deliver(context, type, message, duration) }
                .onFailure { Log.e("QQToast 投递失败", it) }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    private fun deliver(context: Context, type: Int, message: String, duration: Int) {
        val error = runCatching {
            val clazz = resolveToastClass()
                ?: throw ClassNotFoundException("$HOST_TOAST 与 $MINI_TOAST 均无法加载")
            val makeText = resolveMakeText(clazz)
            val toast = makeText.invoke(null, context, type, message, duration) as? Toast
                ?: throw IllegalStateException("${clazz.name}.${makeText.name} 没有返回 Toast")
            toast.show()
        }.exceptionOrNull() ?: return

        Log.e(
            "QQToast 调用失败，回落系统 Toast（${error::class.java.simpleName}: ${error.message}）",
            error,
        )
        runCatching { Toast.makeText(context, message, duration).show() }
            .onFailure { Log.e("系统 Toast 回落失败", it) }
    }

    private fun resolveToastClass(): Class<*>? = cachedClass ?: run {
        listOf(HOST_TOAST, MINI_TOAST).firstNotNullOfOrNull { name ->
            runCatching { HookEnv.hostClassLoader.loadClass(name) }.getOrNull()
                ?: runCatching { Class.forName(name) }.getOrNull()
        }?.also { cachedClass = it }
    }

    private fun resolveMakeText(clazz: Class<*>): Method = cachedMakeText ?: run {
        val bySignature = clazz.methods.firstOrNull {
            Modifier.isStatic(it.modifiers) &&
                it.returnType == Toast::class.java &&
                it.parameterTypes.size == 4 &&
                it.parameterTypes[0] == Context::class.java &&
                it.parameterTypes[1] == Integer.TYPE &&
                it.parameterTypes[2] == CharSequence::class.java &&
                it.parameterTypes[3] == Integer.TYPE
        }
        val resolved = bySignature ?: listOf("makeText", "a", "b").firstNotNullOfOrNull { name ->
            runCatching {
                clazz.getMethod(
                    name,
                    Context::class.java,
                    Integer.TYPE,
                    CharSequence::class.java,
                    Integer.TYPE,
                )
            }.getOrNull()
        }
        (resolved ?: throw NoSuchMethodException(
            "${clazz.name} 缺少 (Context,int,CharSequence,int):Toast 的静态方法"
        )).also { cachedMakeText = it }
    }
}
