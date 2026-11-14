package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import java.io.File
import java.util.zip.ZipFile

/** Adapts the actual script immediately before execution, after package/cache decoration. */
internal class FarmCustomFriendsHost(private val isCustomSession: () -> Boolean) {

    private val adapter by lazy {
        ZipFile(HookEnv.moduleApkPath).use { apk ->
            apk.getInputStream(checkNotNull(apk.getEntry(FarmCustomFriendsScript.ASSET)))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    fun install() {
        val runtime = loadOrThrow("com.tencent.mobileqq.triton.internal.script.ScriptRuntime")
        val engineContext = runtime.getDeclaredField("engineContext").apply { isAccessible = true }
        val getPackage = loadOrThrow("com.tencent.mobileqq.triton.internal.engine.EngineContext").getMethod("getGamePackage")
        val getId = loadOrThrow("com.tencent.mobileqq.triton.filesystem.GamePackage").getMethod("getId")
        val scriptClass = loadOrThrow("com.tencent.mobileqq.triton.script.ScriptFile")
        val contentClass = loadOrThrow($$"com.tencent.mobileqq.triton.script.ScriptFile$Content")
        val pathClass = loadOrThrow($$"com.tencent.mobileqq.triton.script.ScriptFile$Path")
        // ScriptFile itself only declares getValid; names belong to its concrete variants.
        val getContentName = contentClass.getMethod("getName")
        val getPathName = pathClass.getMethod("getName")
        val contentConstructor = contentClass.getConstructor(String::class.java, String::class.java, File::class.java)
        val getContent = contentClass.getMethod("getContent")
        val getPath = pathClass.getMethod("getPath")

        // Local diagnostic channel: only allowlisted stages/counts are retained.
        runtime.getMethod("onScriptCall", String::class.java, String::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).hookBefore { param ->
            if (!isCustomSession()) return@hookBefore
            val body = param.args[1] as? String ?: return@hookBefore
            if (FarmCustomFriendsDiagnostics.accept(body)) param.result = "{}"
        }

        runtime.getMethod("loadScript", scriptClass).hookBefore { param ->
            try {
                val script = param.args[0] ?: return@hookBefore
                val name = when {
                    contentClass.isInstance(script) -> getContentName.invoke(script) as String
                    pathClass.isInstance(script) -> getPathName.invoke(script) as String
                    else -> return@hookBefore
                }
                if (!FarmCustomFriendsScript.isCandidate(name)) return@hookBefore
                val context = engineContext.get(param.thisObject)
                if (getId.invoke(getPackage.invoke(context)) != FarmAccountProtocol.APP_ID) return@hookBefore
                if (!isCustomSession()) {
                    FarmCustomFriendsDiagnostics.record("unmarked_script")
                    return@hookBefore
                }
                FarmCustomFriendsDiagnostics.record("script_received")
                val source = when {
                    contentClass.isInstance(script) -> getContent.invoke(script) as String
                    pathClass.isInstance(script) -> {
                        val file = getPath.invoke(script) as File
                        check(file.length() in 1..FarmCustomFriendsScript.MAX_SCRIPT_BYTES.toLong())
                        file.readText(Charsets.UTF_8)
                    }
                    else -> error("Unsupported script type")
                }
                val patched = FarmCustomFriendsScript.inject(name, source, adapter)
                if (patched == null) {
                    FarmCustomFriendsDiagnostics.record("script_rejected")
                    return@hookBefore
                }
                // Preserve the decorated name; never execute the original compiled cache.
                param.args[0] = contentConstructor.newInstance(name, patched, null)
                FarmCustomFriendsDiagnostics.record("script_transformed")
            } catch (_: Exception) {
                FarmCustomFriendsDiagnostics.record("script_failed")
            }
        }
        FarmCustomFriendsDiagnostics.record("hook_ready")
    }
}
