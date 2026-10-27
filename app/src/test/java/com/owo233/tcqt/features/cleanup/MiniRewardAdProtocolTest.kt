package com.owo233.tcqt.features.cleanup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniRewardAdProtocolTest {
    private val sdkReceiver = "com.tencent.qqmini.proxyimpl.AdProxyImpl\$AdResultReceiver"
    private val amsReceiver =
        "com.tencent.mobileqq.minigame.ad.ams.AmsRewardedVideoAdView\$AmsMotiveVideoResultReceiver"

    @Test
    fun `local SDK and AMS reward receivers accept only GDT page data`() {
        for (receiver in listOf(sdkReceiver, amsReceiver)) {
            assertTrue(MiniRewardAdProtocol.isMiniReward(MiniRewardAdProtocol.PAGE_DATA, receiver, null))
            assertFalse(MiniRewardAdProtocol.isMiniReward(null, receiver, null))
            assertFalse(MiniRewardAdProtocol.isMiniReward("java.lang.String", receiver, null))
            assertFalse(MiniRewardAdProtocol.isMiniReward(
                "com.tencent.qqmini.sdk.launcher.model.MiniAppInfo", receiver, null,
            ))
        }
    }

    @Test
    fun `parcelled receiver requires explicit miniapp business source`() {
        val receiver = "android.os.ResultReceiver"
        assertTrue(MiniRewardAdProtocol.isMiniReward(
            MiniRewardAdProtocol.PAGE_DATA, receiver, "biz_src_miniapp",
        ))
        for (refId in listOf(null, "", "biz_src_qqshop", "miniapp")) {
            assertFalse(MiniRewardAdProtocol.isMiniReward(MiniRewardAdProtocol.PAGE_DATA, receiver, refId))
        }
    }

    @Test
    fun `unrelated ads and normal game containers are ignored`() {
        for (receiver in listOf(
            "com.tencent.mobileqq.minigame.ui.GameActivity1",
            "com.tencent.mobileqq.minigame.ui.GameActivity2",
            "com.tencent.gdtad.SomeOtherResultReceiver",
            sdkReceiver + "Extra",
        )) {
            assertFalse(MiniRewardAdProtocol.isMiniReward(
                MiniRewardAdProtocol.PAGE_DATA, receiver, "biz_src_miniapp",
            ))
        }
    }
}
