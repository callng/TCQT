package com.owo233.tcqt.host.service.api

import com.owo233.tcqt.core.proto.ProtoUtils
import com.owo233.tcqt.core.proto.asByteArray
import com.owo233.tcqt.core.proto.asInt
import com.owo233.tcqt.core.proto.asLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminPermissionProtocolTest {
    @Test
    fun `server rejection from captured packet stays a failure`() {
        val packet = "08 9c b8 02 10 02 18 ea 07 22 00 2a 1d " +
            "70 65 72 6d 69 73 73 69 6f 6e 20 69 74 65 6d 20 69 73 20 6e 6f 74 20 6f 70 65 6e 65 64"
        val error = assertFailsWith<AdminPermissionException> {
            AdminPermissionProtocol.checkResponse(packet.hexBytes())
        }
        assertEquals("permission item is not opened (1002)", error.message)
        assertFailsWith<AdminPermissionException> { AdminPermissionProtocol.checkResponse(byteArrayOf()) }
        assertFailsWith<AdminPermissionException> {
            AdminPermissionProtocol.checkResponse("08 01 10 02 18 00".hexBytes())
        }
        AdminPermissionProtocol.checkResponse("08 9c b8 02 10 02 18 00 22 00".hexBytes())
    }

    @Test
    fun `supported kick limits are serialized as counts rather than picker indices`() {
        for (limit in listOf(0, 5, 10, 20, 50, 100, 200)) {
            val request = ProtoUtils.decodeFromByteArray(
                AdminPermissionProtocol.encodeChange(1035302366, 2151407677, AdminPermission.KICK, true, limit)
            )
            assertEquals(0x9c1c, request[1].asInt)
            assertEquals(2, request[2].asInt)
            assertEquals(1035302366L, request[4, 1].asLong)
            assertEquals(2151407677L, request[4, 2].asLong)
            assertEquals(1, request[4, 3, 1].asInt)
            assertEquals(1, request[4, 3, 2].asInt)
            assertEquals(limit, AdminPermissionProtocol.decodeKickLimit(request[4, 3, 3].asByteArray))
        }
        val disabled = ProtoUtils.decodeFromByteArray(
            AdminPermissionProtocol.encodeChange(1, 2, AdminPermission.KICK, false, 271)
        )
        assertEquals(2, disabled[4, 3, 2].asInt)
        assertTrue(disabled[4, 3, 3].asByteArray.isEmpty())
    }

    @Test
    fun `unsupported kick limits are rejected before encoding`() {
        for (limit in listOf(-1, 1, 7, 8, 15, 30, 99, 150, 201, 271, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException>("limit=$limit") {
                AdminPermissionProtocol.encodeChange(1, 2, AdminPermission.KICK, true, limit)
            }
        }
        // Other permissions do not include a kick-limit extension, even if a future
        // server returns a count outside the current preset list.
        val recall = ProtoUtils.decodeFromByteArray(
            AdminPermissionProtocol.encodeChange(1, 2, AdminPermission.RECALL, true, 8)
        )
        assertTrue(recall[4, 3, 3].asByteArray.isEmpty())
        assertEquals(8, AdminPermissionProtocol.decodeKickLimit("08 08".hexBytes()))
        assertTrue(AdminPermissionProtocol.isEnabled(null))
        assertFalse(AdminPermissionProtocol.isEnabled(2))
    }

    private fun String.hexBytes() = split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
