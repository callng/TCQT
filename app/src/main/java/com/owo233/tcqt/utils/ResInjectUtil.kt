package com.owo233.tcqt.utils

import android.content.res.Resources
import com.owo233.tcqt.R
import com.owo233.tcqt.hooks.base.hostInfo
import com.owo233.tcqt.hooks.base.modulePath

fun injectRes(res: Resources = hostInfo.application.resources) {
    val assets = res.assets
    assets.invoke("addAssetPath", modulePath)
    try {
        logD(msg = "Resources injection result: ${res.getString(R.string.res_inject_success)}")
    } catch (e: Resources.NotFoundException) {
        logE(msg = "Resources injection failed")
    }
}
