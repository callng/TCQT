package com.owo233.tcqt.core.config

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

/** Limits are checked before Android allocates a bitmap. */
internal object PngIconInput {

    const val MAX_FILE_BYTES = 8 * 1024 * 1024
    const val ICON_SIZE = 256
    private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    fun read(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= MAX_FILE_BYTES) { "PNG 图片不能超过 8 MB" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray().also { dimensions(it) }
    }

    fun dimensions(bytes: ByteArray): Pair<Int, Int> {
        require(bytes.size >= 33 && signature.indices.all { bytes[it] == signature[it] } &&
            bytes.copyOfRange(12, 16).contentEquals(byteArrayOf(73, 72, 68, 82)) &&
            ByteBuffer.wrap(bytes, 8, 4).int == 13
        ) { "请选择有效的 PNG 图片" }
        val width = ByteBuffer.wrap(bytes, 16, 4).int
        val height = ByteBuffer.wrap(bytes, 20, 4).int
        require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 32L * 1024 * 1024) {
            "PNG 图片尺寸过大，请缩小后重试"
        }
        return width to height
    }
}
