package com.owo233.tcqt.host.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FarmAccountProtocolTest {
    @Test
    fun `preloaded login bypass is limited to farm account keys`() {
        assertTrue(FarmAccountProtocol.isFarmPreloadKey("key_preload_login_info12345${FarmAccountProtocol.APP_ID}"))
        listOf(
            "key_preload_login_info123451112386030",
            "key_preload_login_info${FarmAccountProtocol.APP_ID}",
            "key_preload_login_infoother${FarmAccountProtocol.APP_ID}",
            "key_preload_login_info0${FarmAccountProtocol.APP_ID}",
            "other12345${FarmAccountProtocol.APP_ID}",
        ).forEach { assertFalse(FarmAccountProtocol.isFarmPreloadKey(it), it) }
    }

    @Test
    fun `an imported code is only consumed once by fast login`() {
        val code = FarmCustomCode("  imported-code\r\n")
        assertNull(code.take(FarmAccountProtocol.REQUEST_LOGIN))
        assertNull(code.take(999))
        assertEquals("imported-code", code.take(FarmAccountProtocol.REQUEST_FAST_LOGIN))
        assertNull(code.take(FarmAccountProtocol.REQUEST_FAST_LOGIN))
    }

    @Test
    fun `concurrent fast login requests cannot replay an imported code`() {
        val code = FarmCustomCode("imported-code")
        val results = java.util.Collections.synchronizedList(mutableListOf<String?>())
        val start = java.util.concurrent.CountDownLatch(1)
        val threads = List(8) {
            Thread { start.await(); results.add(code.take(FarmAccountProtocol.REQUEST_FAST_LOGIN)) }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, results.count { it == "imported-code" })
    }

    @Test
    fun `invalid pasted credentials are rejected without including them in errors`() {
        listOf("", " \n ", "secret code", "secret\ncode", "secret\u0000code", "x".repeat(4097)).forEach { input ->
            val error = assertFailsWith<IllegalArgumentException> { FarmCustomCode(input) }
            if (input.isNotBlank()) assertFalse(error.message.orEmpty().contains(input))
        }
        assertEquals("opaque+/=_-.code", FarmAccountProtocol.normalizeCustomCode(" opaque+/=_-.code "))
    }

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
    fun `only verified login and friend transfer commands can use another account`() {
        assertTrue(FarmAccountProtocol.mayRoute("LightAppSvc.mini_program_auth.GetCode"))
        assertTrue(FarmAccountProtocol.mayRoute("OidbSvcTrpcTcp.0x94b9_33"))
        assertFalse(FarmAccountProtocol.mayRoute("MessageSvc.PbSendMsg"))
        assertFalse(FarmAccountProtocol.mayRoute("OidbSvcTrpcTcp.0x94b9_34"))
        assertFalse(FarmAccountProtocol.mayRoute("LightAppSvc.mini_program_auth.GetCode.extra"))
        assertFalse(FarmAccountProtocol.mayRoute("LightAppSvc.mini_app_cloudstorage.ModifyFriendInteractiveStorage"))
        assertFalse(FarmAccountProtocol.mayRoute("LightAppSvc.mini_app_cloudstorage.GetFriendCloudStorage.extra"))
    }

    @Test
    fun `friend requests cannot reuse a login routing capability`() {
        assertTrue(FarmAccountProtocol.mayRoute(FarmAccountProtocol.HTTP_TRANSFER_CMD))
        for (operation in 1..2) {
            assertFalse(FarmAccountProtocol.matchesRequest(operation, FarmAccountProtocol.HTTP_TRANSFER_CMD))
        }
        FarmFriendOperation.entries.forEach { operation ->
            assertTrue(FarmAccountProtocol.matchesRequest(operation.id, FarmAccountProtocol.HTTP_TRANSFER_CMD))
            assertFalse(FarmAccountProtocol.matchesRequest(operation.id, FarmAccountProtocol.FAST_LOGIN_CMD))
        }
        assertFalse(FarmAccountProtocol.matchesRequest(999, FarmAccountProtocol.HTTP_TRANSFER_CMD))
        assertFalse(FarmAccountProtocol.mayRoute(FarmAccountProtocol.HTTP_TRANSFER_CMD + ".extra"))
    }

    @Test
    fun `only exact farm friend read endpoints are accepted`() {
        FarmFriendOperation.entries.forEach { operation ->
            assertEquals(operation, FarmFriendOperation.fromRequest(FarmAccountProtocol.APP_ID, "POST", operation.url))
        }
        val url = "http://fakeapi.qq.com/get_friend_storage"
        assertNull(FarmFriendOperation.fromRequest("other-app", "POST", url))
        assertNull(FarmFriendOperation.fromRequest(FarmAccountProtocol.APP_ID, "GET", url))
        listOf(
            "$url?account=12345", "$url/extra", "https://fakeapi.qq.com/get_friend_storage",
            "http://fakeapi.qq.com.evil/get_friend_storage", "http://fakeapi.qq.com/push_gray_message",
            "http://fakeapi.qq.com/set_farm_status", "http://fakeapi.qq.com/set_auth_status",
        ).forEach { assertNull(FarmFriendOperation.fromRequest(FarmAccountProtocol.APP_ID, "POST", it)) }
    }

    @Test
    fun `friend requests cannot consume an imported or preflight login code`() {
        val custom = FarmCustomCode("imported-code")
        val preflight = FarmPreflightCode("12345", "preflight-code", 100)
        FarmFriendOperation.entries.forEach { operation ->
            assertNull(custom.take(operation.id))
            assertNull(preflight.take("12345", operation.id, 101))
        }
        assertEquals("imported-code", custom.take(FarmAccountProtocol.REQUEST_FAST_LOGIN))
        assertEquals("preflight-code", preflight.take("12345", FarmAccountProtocol.REQUEST_FAST_LOGIN, 102))
    }

    @Test
    fun `invalid account values never enter the send path`() {
        assertTrue(FarmAccountProtocol.isAccount("12345"))
        listOf(null, "", "0", "-1", "123 45", "123x", "9223372036854775808").forEach {
            assertFalse(FarmAccountProtocol.isAccount(it), it)
        }
    }
}
