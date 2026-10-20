package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.log.Log
import java.util.concurrent.atomic.AtomicBoolean

@RegisterAction
object TroopAdminPermissions : Feature(
    key = "troop_admin_permissions",
    name = "群管理员权限设置",
    desc = "[临时功能]强制显示设置管理员页面的「设置权限」入口，并解除基础管理、加群管理等五项扩展权限的点击限制。",
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true), // minQQVersion = 16580L
) {

    override fun install() {
        val reported = AtomicBoolean()
        loadOrThrow("com.tencent.mobileqq.unitedconfig_android.api.impl.UnitedConfigManagerImpl")
            .hookMethodAfter("loadAsString", String::class.java, String::class.java) { param ->
                if (param.args[0] != TroopAdminPermissionConfig.KEY || param.throwable != null) {
                    return@hookMethodAfter
                }
                val raw = param.result as? String
                val updated = TroopAdminPermissionConfig.forceEnable(raw)
                param.result = updated
                if (reported.compareAndSet(false, true)) {
                    Log.d("TroopAdminPermissions: source=${TroopAdminPermissionConfig.describe(raw)}, " +
                        "rawLength=${raw?.length ?: 0}, resultLength=${updated.length}")
                }
            }
    }
}
