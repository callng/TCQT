package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.config.SettingsBackend
import com.owo233.tcqt.core.config.SettingsStore
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.hook.HookEngineManager
import com.owo233.tcqt.core.hook.HookParam
import com.owo233.tcqt.core.hook.IHookEngine
import com.owo233.tcqt.core.hook.Unhook
import com.owo233.tcqt.ui.settings.FeatureCatalog
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession
import java.lang.reflect.Member
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AntiRecallModeTest {

    private class Session : IQQNTWrapperSession by IQQNTWrapperSession.CppProxy() {
        fun switchToBackGround() = Unit
    }

    private class Param(override val method: Member, override var args: Array<Any?>) : HookParam {
        override val thisObject: Any? = null
        override var result: Any? = "original"
        override var throwable: Throwable? = null
    }

    @Test
    fun uiSelectionsChooseTheMatchingEngine() = withSettings {
        val group = FeatureCatalog.getAllFeatures().single { it.key == "msg_anti_recall" }
            .optionGroups.single { it.key == "msg_anti_recall.mode" }
        assertEquals(group.options.single { it.label == "Java" }.value, group.fallbackValue)
        TCQTSetting.setBoolean("msg_anti_recall", true)
        for (option in group.options) {
            TCQTSetting.setInt(group.key, option.value)
            assertEquals(option.label == "Native", AntiRecallConfig.isNativeEnabled())
            assertEquals(option.label == "Java", AntiRecallConfig.isJavaEnabled())
        }
        TCQTSetting.setInt(group.key, 0) // Previous unset/default value still uses Java.
        assertTrue(AntiRecallConfig.isJavaEnabled())
        assertFalse(AntiRecallConfig.isNativeEnabled())
        for (option in group.options) {
            TCQTSetting.setInt(group.key, option.value)
            TCQTSetting.setBoolean("msg_anti_recall", false)
            assertFalse(AntiRecallConfig.isNativeEnabled())
            assertFalse(AntiRecallConfig.isJavaEnabled())
        }
    }

    @Test
    fun installedJavaCallbacksStopAfterSelectingNativeOrDisabling() = withSettings {
        val callbacks = mutableMapOf<Member, (HookParam) -> Unit>()
        val engineField = HookEngineManager::class.java.getDeclaredField("engine")
        val previousEngine = engineField.get(null)
        HookEngineManager.engine = Proxy.newProxyInstance(
            IHookEngine::class.java.classLoader, arrayOf(IHookEngine::class.java)
        ) { _, method, args ->
            check(method.name == "hookBefore") { "Unexpected engine call: ${method.name}" }
            @Suppress("UNCHECKED_CAST")
            callbacks[args!![0] as Member] = args[2] as (HookParam) -> Unit
            Unhook {}
        } as IHookEngine
        val service = Proxy.newProxyInstance(
            IKernelService::class.java.classLoader, arrayOf(IKernelService::class.java)
        ) { _, _, _ -> Session() } as IKernelService
        try {
            TCQTSetting.setBoolean("msg_anti_recall", true)
            TCQTSetting.setInt("msg_anti_recall.mode", AntiRecallConfig.NATIVE_MODE)
            NTServiceFetcher.onFetch(service)
            assertTrue(callbacks.isEmpty(), "Native must not install MSF or background hooks")

            TCQTSetting.setInt("msg_anti_recall.mode", AntiRecallConfig.JAVA_MODE)
            NTServiceFetcher.onFetch(service)
            assertEquals(2, callbacks.size)
            val background = callbacks.keys.single { it.name == "switchToBackGround" }
            val push = callbacks.keys.single { it.name == "onMsfPush" }
            val javaBackground = Param(background, emptyArray())
            callbacks.getValue(background)(javaBackground)
            assertEquals(null, javaBackground.result)

            TCQTSetting.setInt("msg_anti_recall.mode", AntiRecallConfig.NATIVE_MODE)
            repeat(2) { state ->
                if (state == 1) {
                    TCQTSetting.setBoolean("msg_anti_recall", false)
                    TCQTSetting.setInt("msg_anti_recall.mode", AntiRecallConfig.JAVA_MODE)
                }
                val idleBackground = Param(background, emptyArray())
                callbacks.getValue(background)(idleBackground)
                assertEquals("original", idleBackground.result)
                for (cmd in listOf(
                    "trpc.msg.register_proxy.RegisterProxy.InfoSyncPush",
                    "trpc.msg.olpush.OlPushService.MsgPush",
                )) {
                    // An inactive callback must not log or try to decode this invalid packet.
                    val bytes = byteArrayOf(0xff.toByte())
                    val idlePush = Param(push, arrayOf(cmd, bytes, null))
                    callbacks.getValue(push)(idlePush)
                    assertEquals("original", idlePush.result)
                    assertSame(bytes, idlePush.args[1])
                }
            }
        } finally {
            engineField.set(null, previousEngine)
        }
    }

    private fun withSettings(block: () -> Unit) {
        val directory = Files.createTempDirectory("tcqt-recall-mode").toFile()
        var data: Map<String, Any> = emptyMap()
        val store = SettingsStore(directory, "test") { object : SettingsBackend {
            override fun read() = data
            override fun replace(values: Map<String, Any>) { data = values }
            override fun close() = Unit
        } }
        // Replace only the test JVM's lazy store; production settings and files are untouched.
        val delegateField = TCQTSetting::class.java.getDeclaredField("config\$delegate")
            .apply { isAccessible = true }
        val delegate = delegateField.get(null)
        val valueField = delegate.javaClass.getDeclaredField("_value").apply { isAccessible = true }
        val previousStore = valueField.get(delegate)
        valueField.set(delegate, store)
        try {
            block()
        } finally {
            valueField.set(delegate, previousStore)
            directory.deleteRecursively()
        }
    }
}
