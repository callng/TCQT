package com.owo233.tcqt.features.misc

import com.owo233.tcqt.core.config.IntSetting
import com.owo233.tcqt.ui.settings.FeatureCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MiniAppShareProtocolTest {
    @Test
    fun `host fields include protected app info and inherited app identity`() {
        // Reproduce the public-only lookup that failed on QQ 9.3.80.
        assertFailsWith<NoSuchFieldException> {
            HostShareData::class.java.getField("miniAppInfo")
        }
        val fields = MiniAppShareDataFields(HostShareData::class.java, HostInnerShareData::class.java)
        val data = HostInnerShareData(HostMiniAppInfo())
        assertEquals(0, fields.target.getInt(data))
        assertEquals(11, fields.source.getInt(data))
        assertEquals(1, fields.scene.getInt(data))
        assertSame(data.fromActivity, fields.activity.get(data))
        assertEquals("123", fields.appId(data))
        assertTrue(MiniAppShareProtocol.shouldSkip(2, fields.target.getInt(data), fields.appId(data)))
    }

    @Test
    fun `missing host app info keeps the share page`() {
        val fields = MiniAppShareDataFields(HostShareData::class.java, HostInnerShareData::class.java)
        val data = HostInnerShareData(null)
        assertNull(fields.appId(data))
        assertFalse(MiniAppShareProtocol.shouldSkip(2, fields.target.getInt(data), fields.appId(data)))
    }

    @Test
    fun `keep page is the visible default for existing users`() {
        val setting = MiniAppShare.settings.single() as IntSetting
        assertEquals("mini_app_share.share_page", setting.key)
        assertEquals(1, setting.defaultValue)
        assertEquals(listOf("保留分享页面", "跳过分享页面"), setting.options)
        assertFalse(setting.isHide)
        val feature = FeatureCatalog.getAllFeatures().single { it.key == MiniAppShare.key }
        val group = feature.optionGroups.single { it.key == setting.key }
        assertEquals(1, group.fallbackValue)
        assertEquals(listOf(1, 2), group.options.map { it.value })
        assertTrue(feature.desc.contains("无法区分奖励分享与普通分享"))
        for (target in -1..10) {
            assertFalse(MiniAppShareProtocol.shouldSkip(setting.defaultValue, target, "123"))
        }
    }

    @Test
    fun `opt in covers the traced QQ friend and group targets`() {
        for (target in listOf(0, 2, 5, 6)) {
            assertTrue(MiniAppShareProtocol.shouldSkip(2, target, "123"))
        }
    }

    @Test
    fun `qzone wechat guild updatable and unknown targets keep original handling`() {
        for (target in listOf(-1, 1, 3, 4, 7, 8, 9, 10, Int.MAX_VALUE)) {
            assertFalse(MiniAppShareProtocol.shouldSkip(2, target, "123"))
        }
    }

    @Test
    fun `missing app identity or invalid setting never suppresses sharing`() {
        for (appId in listOf(null, "", "  ")) {
            assertFalse(MiniAppShareProtocol.shouldSkip(2, 0, appId))
        }
        for (mode in listOf(-1, 0, 1, 3, Int.MAX_VALUE)) {
            assertFalse(MiniAppShareProtocol.shouldSkip(mode, 0, "123"))
        }
    }

    @Test
    fun `payment and unrelated sources or scenes keep original handling`() {
        assertTrue(MiniAppShareProtocol.isStandardShare(11, 1))
        assertTrue(MiniAppShareProtocol.isStandardShare(12, 1))
        for (source in listOf(-1, 0, 1, 10, 13, 14, Int.MAX_VALUE)) {
            assertFalse(MiniAppShareProtocol.isStandardShare(source, 1))
        }
        for (scene in listOf(-1, 0, 2, 3, 4, 5)) {
            assertFalse(MiniAppShareProtocol.isStandardShare(11, scene))
        }
    }

    private open class HostMiniAppBaseInfo {
        @JvmField val appId = "123"
    }

    private class HostMiniAppInfo : HostMiniAppBaseInfo()

    private open class HostShareData(@JvmField protected val miniAppInfo: HostMiniAppInfo?) {
        @JvmField val shareTarget = 0
        @JvmField val shareSource = 11
    }

    private class HostInnerShareData(info: HostMiniAppInfo?) : HostShareData(info) {
        @JvmField val shareScene = 1
        @JvmField val fromActivity = Any()
    }
}
