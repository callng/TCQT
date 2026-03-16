package com.owo233.tcqt.servlet

import NS_MINI_INTERFACE.INTERFACE
import com.tencent.mobileqq.mini.servlet.MiniAppSSOCmdHelper

internal object MiniSvc {

    fun judgeTiming() {
        // 这些定义注释都是AI解释的,不知道对不对,管它呢 (能用就行了)
        val req = INTERFACE.StJudgeTimingReq().apply {
            appid.set("1112173744") // 小程序的 AppID
            factType.set(13) // 代表定时上报
            duration.set(32) // 本次上报经过的秒数
            reportTime.set(System.currentTimeMillis() / 1000) // 当前 Unix 时间戳（秒）
            totalTime.set(300) // 今日累计在线总秒数
            launchId.set((System.currentTimeMillis() / 1000).toString()) // 启动时的毫秒时间戳字符串
            via.set("2016_4") // 渠道标识
            appType.set(1) // 应用类型
            scene.set(2014) // 入口场景值
            afterCertify.set(1) // 用户实名状态
            AdsTotalTime.set(0) // 用户在本次 duration 期间观看广告的总时长
            sourceID.set("") // 为空
        }

        MiniAppSSOCmdHelper.sendSSOCmdRequest(
            "LightAppSvc.mini_app_growguard.JudgeTiming",
            "1112173744",
            req,
            INTERFACE.StJudgeTimingRsp::class.java,
            null
        )
    }
}
