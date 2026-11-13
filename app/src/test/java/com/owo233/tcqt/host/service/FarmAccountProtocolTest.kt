package com.owo233.tcqt.host.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FarmAccountProtocolTest {
    @Test
    fun `a code cannot cross accounts or API types`() {
        val code = FarmPreflightCode("12345", "one-time-code", 100)
        assertNull(code.take("67890", FarmAccountProtocol.REQUEST_FAST_LOGIN, 101))
        assertNull(code.take("12345", FarmAccountProtocol.REQUEST_LOGIN, 101))
        assertEquals("one-time-code", code.take("12345", FarmAccountProtocol.REQUEST_FAST_LOGIN, 101))
        assertNull(code.take("12345", FarmAccountProtocol.REQUEST_FAST_LOGIN, 102))
    }

    @Test
    fun `a delayed launch must get a fresh code`() {
        val code = FarmPreflightCode("12345", "expired-code", 100)
        assertNull(code.take("12345", FarmAccountProtocol.REQUEST_FAST_LOGIN, 30_101))
        assertNull(code.take("12345", FarmAccountProtocol.REQUEST_FAST_LOGIN, 200))
    }

    @Test
    fun `only the two verified login commands can use another account`() {
        assertTrue(FarmAccountProtocol.mayRoute("LightAppSvc.mini_program_auth.GetCode"))
        assertTrue(FarmAccountProtocol.mayRoute("OidbSvcTrpcTcp.0x94b9_33"))
        assertFalse(FarmAccountProtocol.mayRoute("MessageSvc.PbSendMsg"))
        assertFalse(FarmAccountProtocol.mayRoute("OidbSvcTrpcTcp.0x94b9_34"))
        assertFalse(FarmAccountProtocol.mayRoute("LightAppSvc.mini_program_auth.GetCode.extra"))
    }

    @Test
    fun `invalid account values never enter the send path`() {
        assertTrue(FarmAccountProtocol.isAccount("12345"))
        listOf(null, "", "0", "-1", "123 45", "123x", "9223372036854775808").forEach {
            assertFalse(FarmAccountProtocol.isAccount(it), it)
        }
    }
}
