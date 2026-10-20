package com.owo233.tcqt.features.menu.troop

import kotlin.test.Test
import kotlin.test.assertEquals

class AdminPermissionAccessTest {
    @Test
    fun `entry appears only for QQ owner targeting another administrator`() {
        for (isQQ in listOf(false, true))
            for (owner in listOf(false, true))
                for (admin in listOf(false, true))
                    for (targetOwner in listOf(false, true))
                        for (targetAdmin in listOf(false, true)) {
                            val roles = TroopMemberRoles(owner, admin, targetOwner, targetAdmin)
                            assertEquals(
                                isQQ && owner && !targetOwner && targetAdmin,
                                roles.permissions(isQQ).canEditAdminPermissions,
                                roles.toString()
                            )
                        }
    }
}
