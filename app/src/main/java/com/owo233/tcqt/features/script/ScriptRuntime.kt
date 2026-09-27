package com.owo233.tcqt.features.script

import android.app.Activity
import bsh.BshMethod
import bsh.Interpreter
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.script.DynamicActivityPort
import com.owo233.tcqt.features.script.bean.ScriptInfo
import com.owo233.tcqt.host.QQInterfaces
import java.io.File
import java.lang.reflect.Modifier

/**
 * 单个脚本的运行实例：BeanShell 解释器、类加载器链、脚本菜单注册表与事件回调。
 *
 * 一个脚本 = 一个 [ScriptRuntime]，`start` / `stop` 成对调用；
 * [namespace] 里所有公开方法都会作为全局函数注入脚本作用域。
 */
internal class ScriptRuntime(val info: ScriptInfo) {

    /** 脚本悬浮菜单：菜单名 -> 脚本回调方法名。 */
    val menuItems = linkedMapOf<String, String>()

    /** 消息长按菜单：菜单名 -> 脚本回调方法名。 */
    val msgMenuItems = linkedMapOf<String, String>()

    val loader = ScriptClassLoader(javaClass.classLoader)

    val file: File get() = File(info.dirPath, ScriptInfo.ENTRY_FILE)

    val configDir: File get() = File(info.dirPath, ScriptInfo.CONFIG_DIR)

    val namespace = ScriptNamespace(this)

    var interpreter: Interpreter = Interpreter()
        private set

    private var started = false

    /** 装载并执行 `main.java`；过程中任何异常都向上抛给调用者处理。 */
    @Synchronized
    fun start() {
        stop(invokeCallback = false)

        info.updateFromDisk()

        val scriptFile = file
        check(scriptFile.exists()) { "${ScriptInfo.ENTRY_FILE} 不存在" }
        configDir.mkdirs()

        try {
            interpreter = Interpreter().apply {
                set("context", hostContext())
                // 自动装载可能早于 runtime 就绪（内核进程尤其明显）：全局变量取不到就降级，
                // 绝不能让整个 start() 抛异常 —— 那会让脚本彻底起不来、onMsg 也收不到。
                set("myUin", currentUinOrEmpty())
                set("classLoader", HookEnv.hostClassLoader)
                set("pluginId", info.id)
                set("pluginPath", info.dirPath)
                setClassLoader(loader)

                bindApiMethods(this)

                source(scriptFile.absolutePath)
            }
        } catch (t: Throwable) {
            stop(invokeCallback = false)
            throw t
        }

        started = true
        info.isRunning = true
    }

    @Synchronized
    fun stop(invokeCallback: Boolean = true) {
        if (invokeCallback && started) {
            invoke(ScriptEvents.UNLOAD, emptyArray(), emptyArray())
        }

        runCatching {
            unregisterActivities()
            interpreter.nameSpace.clear()
            menuItems.clear()
            msgMenuItems.clear()
        }.onFailure { Log.e("脚本运行态清理失败 [${info.id}]", it) }

        started = false
        info.isRunning = false
    }

    /** 调用脚本中签名匹配的公开方法；方法不存在或参数不匹配时静默返回 false。 */
    fun invoke(methodName: String, paramTypes: Array<Class<*>>, args: Array<Any?>): Boolean {
        if (!started) return false
        return runCatching {
            val space = interpreter.nameSpace ?: return false
            if (!space.methodNames.contains(methodName)) return false
            space.getMethod(methodName, paramTypes).invoke(args, interpreter)
            true
        }.onFailure {
            Log.e("脚本方法调用失败 [${info.id}] $methodName", it)
        }.getOrDefault(false)
    }

    fun hasMethod(methodName: String): Boolean =
        started && runCatching {
            interpreter.nameSpace?.methodNames?.contains(methodName) == true
        }.getOrDefault(false)

    /**
     * 按「候选参数个数」调用脚本方法：命中第一个存在的重载就调用。
     *
     * 用于文档里允许多种签名的回调（如 `chatInterface` 的 3 参 / 4 参版本），
     * 避免写死一种签名导致另一种写法的脚本收不到事件。
     */
    fun invokeByNameSize(
        name: String,
        sizes: IntArray,
        buildArgs: (Int) -> Array<Any?>,
    ): Boolean {
        if (!started) return false

        val space = interpreter.nameSpace ?: return false
        val methods = space.methods ?: return false

        sizes.forEach { size ->
            val target = methods.firstOrNull { it.name == name && it.parameterTypes.size == size }
                ?: return@forEach

            return runCatching {
                target.invoke(buildArgs(size), interpreter)
                true
            }.onFailure {
                Log.e("脚本方法调用失败 [${info.id}] $name/$size", it)
            }.getOrDefault(false)
        }
        return false
    }

    /**
     * 悬浮菜单点击：调用脚本里注册的回调。
     *
     * 与文档一致，回调可写 3 参（聊天类型 / PeerUin / 昵称）或 4 参（多一个 Contact）。
     */
    fun invokeMenuItem(callback: String, contact: ScriptChatContext) {
        invokeByNameSize(
            name = callback,
            sizes = intArrayOf(3, 4),
        ) { size ->
            if (size == 4) {
                arrayOf(contact.chatType, contact.peerUin, contact.peerName, contact.toKernelContact())
            } else {
                arrayOf(contact.chatType, contact.peerUin, contact.peerName)
            }
        }
    }

    /** 调用返回字符串的脚本方法；方法不存在或返回非字符串时返回 null。 */
    fun invokeForString(methodName: String, arg: String): String? {
        if (!started) return null
        return runCatching {
            val method = scriptMethod(methodName, 1) ?: return null
            method.invoke(arrayOf(arg), interpreter) as? String
        }.onFailure {
            Log.e("脚本方法调用失败 [${info.id}] $methodName", it)
        }.getOrNull()
    }

    fun scriptMethod(methodName: String, paramCount: Int): BshMethod? =
        runCatching {
            interpreter.nameSpace?.methods?.firstOrNull {
                it.name == methodName && it.parameterTypes.size == paramCount
            }
        }.getOrNull()

    /** 把 [ScriptNamespace] 的公开方法注入解释器命名空间（含重载）。 */
    private fun bindApiMethods(interpreter: Interpreter) {
        ScriptNamespace::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .filterNot { it.name.contains("$") }
            .filterNot { Modifier.isStatic(it.modifiers) }
            .forEach { method ->
                runCatching { interpreter.nameSpace.setMethod(BshMethod(method, namespace)) }
                    .onFailure { Log.e("脚本 API 注入失败: ${method.name}", it) }
            }
    }

    fun currentActivity(): Activity? = runCatching { QQInterfaces.topActivity }.getOrNull()

    /** 脚本注册过、需要在停止时注销的 Activity 类名。 */
    private val registeredActivities = linkedSetOf<String>()

    /**
     * 注册脚本自己的 Activity，停止脚本时自动注销。
     *
     * 只做注册：实际启动仍由宿主的 Activity 代理流程完成。
     */
    fun registerActivity(activityClass: Class<*>) {
        require(Activity::class.java.isAssignableFrom(activityClass)) {
            "registerActivity 只接受 Activity 子类：${activityClass.name}"
        }
        check(DynamicActivityPort.register(activityClass)) {
            "动态 Activity 端口未就绪（loader 尚未注入实现）"
        }
        synchronized(registeredActivities) { registeredActivities += activityClass.name }
    }

    private fun unregisterActivities() {
        synchronized(registeredActivities) {
            registeredActivities.forEach(DynamicActivityPort::unregister)
            registeredActivities.clear()
        }
    }

    /** 宿主 Application Context；就绪前退回应用对象，仍取不到则返回 null（脚本侧判空）。 */
    private fun hostContext(): android.content.Context? =
        runCatching { QQInterfaces.context.applicationContext }
            .getOrElse { runCatching { HookEnv.application }.getOrNull() }

    /** 当前登录 QQ 号；未登录 / runtime 未就绪时为空串。 */
    private fun currentUinOrEmpty(): String =
        runCatching { QQInterfaces.currentUin }.getOrDefault("")
}
