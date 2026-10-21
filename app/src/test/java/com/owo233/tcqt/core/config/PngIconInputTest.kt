package com.owo233.tcqt.core.config

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PngIconInputTest {
    private fun header(width: Int, height: Int): ByteArray = ByteBuffer.allocate(33)
        .put(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        .putInt(13).put(byteArrayOf(73, 72, 68, 82))
        .putInt(width).putInt(height).array()

    @Test
    fun `accepts valid PNG dimensions including narrow images`() {
        assertEquals(256 to 128, PngIconInput.dimensions(header(256, 128)))
        assertEquals(1 to 8192, PngIconInput.dimensions(header(1, 8192)))
    }

    @Test
    fun `rejects fake PNG truncated headers and misplaced IHDR`() {
        assertFailsWith<IllegalArgumentException> { PngIconInput.dimensions(ByteArray(33)) }
        assertFailsWith<IllegalArgumentException> { PngIconInput.dimensions(header(32, 32).copyOf(32)) }
        assertFailsWith<IllegalArgumentException> {
            PngIconInput.dimensions(header(32, 32).apply { this[12] = 0 })
        }
        assertFailsWith<IllegalArgumentException> {
            PngIconInput.dimensions(header(32, 32).apply { this[11] = 12 })
        }
    }

    @Test
    fun `rejects zero negative oversized dimensions and excessive pixel counts`() {
        listOf(0 to 32, -1 to 32, 32 to 0, 8193 to 32, 8192 to 8192).forEach { (w, h) ->
            assertFailsWith<IllegalArgumentException> { PngIconInput.dimensions(header(w, h)) }
        }
    }

    @Test
    fun `bounded read preserves all image bytes`() {
        val bytes = header(20, 10) + ByteArray(9000) { (it % 256).toByte() }
        assertContentEquals(bytes, PngIconInput.read(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `rejects oversized streams without trusting provider metadata`() {
        val endless = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(0, off, off + len)
                return len
            }
        }
        assertFailsWith<IllegalArgumentException> { PngIconInput.read(endless) }
    }
}
