package com.owo233.tcqt.loader.validation

import android.annotation.SuppressLint
import android.app.Application
import android.os.Looper
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.config.SettingsStore
import com.owo233.tcqt.core.dexkit.DexKitCache
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.hook.HookEngineManager
import com.owo233.tcqt.core.hook.IHookEngine
import com.owo233.tcqt.core.hook.Unhook
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.features.chat.ChatPosition
import com.owo233.tcqt.features.chat.RememberChatPosition
import dalvik.system.PathClassLoader
import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Member
import java.lang.reflect.Proxy
import kotlin.system.exitProcess

/** Standalone app_process checks; never installs hooks into the running QQ process. */
object ChatPositionCompatibilityMain {
    @SuppressLint("UnsafeDynamicallyLoadedCode", "DuplicateCreateDexKit")
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "Expected command, library path and fixture directory/APK" }
        System.load(args[1])
        @Suppress("DEPRECATION")
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        val report = JSONObject().put("command", args[0])
        when (args[0]) {
            "compat" -> {
                // The boot parent prevents module dependencies from shadowing QQ's AndroidX classes.
                val loader = PathClassLoader(args[2], ClassLoader.getSystemClassLoader().parent)
                val descriptors = DexKitBridge.create(args[2]).use { bridge ->
                    RememberChatPosition.getQueryMap().mapValues { (key, query) ->
                        val matches = bridge.findMethod(query as FindMethod)
                        check(matches.size == 1) { "$key: expected one match, got $matches" }
                        matches.single().descriptor.also { report.put(key, it) }
                    }.also {
                        // The exit callback resolves the actual holder lazily; validate it as well.
                        val baseVB = loader.loadClass("com.tencent.mobileqq.aio.msglist.AIOMsgListVB")
                            .findMethod { name = "getMAIOContext\$sdk_debug"; paramCount = 0 }.declaringClass
                        val holder = loader.loadClass("androidx.recyclerview.widget.RecyclerView\$ViewHolder")
                        val holderTypes = bridge.findMethod {
                            matcher { paramCount(0); returnType(baseVB.name) }
                        }.map { method ->
                            loader.loadClass(method.descriptor.substringBefore(';').removePrefix("L").replace('/', '.'))
                        }.distinct().filter(holder::isAssignableFrom)
                        check(holderTypes.isNotEmpty()) { "No message holder getter for ${baseVB.name}" }
                        val getters = holderTypes.map { type ->
                            type.findMethod { paramCount = 0; returnType = baseVB; requireUnique = true }
                        }
                        report.put("holderGetters", JSONArray(getters.map(Member::toString)))
                    }
                }
                descriptors.values.forEach { DexMethod(it).getMethodInstance(loader) }
                HookEnv.setHostClassLoader(loader)
                DexKitCache::class.java.getDeclaredField("cacheMap").apply { isAccessible = true }
                    .set(null, descriptors)
                val hooks = mutableListOf<Member>()
                // Run the production install() to test every reflection lookup, not a copied list.
                HookEngineManager.engine = Proxy.newProxyInstance(
                    IHookEngine::class.java.classLoader, arrayOf(IHookEngine::class.java),
                ) { _, method, values ->
                    check(method.name in setOf("hookBefore", "hookAfter")) { "Unexpected engine call: $method" }
                    hooks += values[0] as Member
                    Unhook { }
                } as IHookEngine
                RememberChatPosition.onRun(Application(), ActionProcess.MAIN)
                check(hooks.size == 11) { "Expected position, unread and button hooks: $hooks" }
                loader.loadClass("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService").getMethod(
                    "getLatestDbMsgs", loader.loadClass("com.tencent.qqnt.kernelpublic.nativeinterface.Contact"),
                    Int::class.java, loader.loadClass("com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback"),
                )
                report.put("hooks", JSONArray(hooks.map(Member::toString)))
            }
            "inspect" -> {
                val directory = File(args[2]).canonicalFile
                require(directory.path.startsWith("/data/local/tmp/tcqt-chat-position-compat/"))
                @Suppress("DEPRECATION")
                if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
                val settings = SettingsStore(File(directory, "setting"), "TCQT")
                val positions = SettingsStore(File(directory, "chat_position"), "positions").all()
                val decoded = positions.values.map { (it as? String)?.let(ChatPosition::decode) }
                check(decoded.none { it == null }) { "Invalid position records" }
                report.put("enabled", settings["remember_chat_position"])
                report.put("positions", positions.size)
                report.put("friends", positions.keys.count { it.split(':').getOrNull(1) == "1" })
                report.put("groups", positions.keys.count { it.split(':').getOrNull(1) == "2" })
                report.put("partialOffsets", decoded.count { it!!.offset < 0 })
                report.put("accountCount", positions.keys.map { it.substringBefore(':') }.distinct().size)
                report.put("identitiesValid", decoded.all { it!!.msgId > 0 || it.msgSeq > 0 })
            }
            else -> error("Unknown command: ${args[0]}")
        }
        println(report.put("passed", true))
        exitProcess(0)
    }
}
