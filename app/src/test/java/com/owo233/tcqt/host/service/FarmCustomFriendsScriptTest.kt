package com.owo233.tcqt.host.service

import java.io.File
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FarmCustomFriendsScriptTest {
    private val adapter = "/* ${FarmCustomFriendsScript.MARKER} */ install();"
    private val factory = "define(\"assets/main/index.3ba8b.js\", function(require, module, exports){\n\"use strict\";"

    @Test
    fun `Triton decorated source names are supported at the final execution boundary`() {
        val source = "$factory original(); });"
        assertEquals(
            FarmCustomFriendsScript.inject("game.js", source, adapter),
            FarmCustomFriendsScript.inject("/game/game.js", source, adapter),
        )
        assertTrue(FarmCustomFriendsScript.isCandidate("/game/assets/main/index.abc.js"))
        assertFalse(FarmCustomFriendsScript.isCandidate("/engine/game.js"))
        assertFalse(FarmCustomFriendsScript.isCandidate("/game/../game.js"))
    }

    @Test
    fun `adapter runs inside the main factory and preserves strict mode and game code`() {
        val source = "setupSystem();\n$factory\noriginal();\n}); require('game.js');"
        val patched = FarmCustomFriendsScript.inject("game.js", source, adapter)!!
        assertEquals(source, patched.replace("\n;$adapter\n", ""))
        assertTrue(patched.indexOf(adapter) > patched.indexOf(factory))
        assertTrue(patched.indexOf(adapter) < patched.indexOf("original();"))
    }

    @Test
    fun `only a single identified main bundle can be modified`() {
        assertNull(FarmCustomFriendsScript.inject("subContext.js", factory, adapter))
        assertNull(FarmCustomFriendsScript.inject("other/game.js", factory, adapter))
        assertNull(FarmCustomFriendsScript.inject("game.js", "define('other.js',function(){});", adapter))
        assertNull(FarmCustomFriendsScript.inject("game.js", factory + factory, adapter))
        assertNull(FarmCustomFriendsScript.inject("game.js", "x".repeat(FarmCustomFriendsScript.MAX_SCRIPT_BYTES + 1), adapter))
    }

    @Test
    fun `cached or repeated requests never insert the adapter twice`() {
        val patched = FarmCustomFriendsScript.inject("game.js", "$factory original(); });", adapter)!!
        assertNull(FarmCustomFriendsScript.inject("game.js", patched, adapter))
    }

    @Test
    fun `standalone main and hash changes are supported without matching unrelated scripts`() {
        val source = "'use strict'; System.register('chunks:///_virtual/FriendManager.ts',[],function(){});"
        val patched = FarmCustomFriendsScript.inject("assets/main/index.abc123.js", source, adapter)!!
        assertTrue(patched.startsWith("'use strict';\n;"))
        assertNull(FarmCustomFriendsScript.inject("assets/main/index.abc123.js", "unrecognized();", adapter))
        assertFalse(FarmCustomFriendsScript.isCandidate("assets/main/index/evil.js"))
        assertTrue(FarmCustomFriendsScript.isCandidate("assets/main/index.js"))
    }

    @Test
    fun `optional official fixture is transformed with the shipped adapter`() {
        val fixture = System.getenv("TCQT_FARM_GAME_DIR")?.let(::File)
        assumeTrue("Set TCQT_FARM_GAME_DIR to a local official script fixture", fixture != null)
        val source = File(fixture!!, "game.js").readText()
        val shipped = File("src/main/assets/tcqt/farm-custom-friends.js").readText()
        val patched = requireNotNull(FarmCustomFriendsScript.inject("game.js", source, shipped))
        assertEquals(source, patched.replace("\n;$shipped\n", ""))
        assertEquals(1, Regex(FarmCustomFriendsScript.MARKER).findAll(patched).count())
        File("build/outputs/farm-custom-friends").mkdirs()
        File("build/outputs/farm-custom-friends/game.patched.js").writeText(patched)
    }
}
