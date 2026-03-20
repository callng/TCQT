package com.owo233.tcqt.servlet.util

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterInputStream

internal object DeflateTools {

    fun uncompress(input: ByteArray): ByteArray {
        return InflaterInputStream(input.inputStream()).use { it.readBytes() }
    }

    fun compress(input: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            DeflaterOutputStream(this).use { it.write(input) }
        }.toByteArray()
    }

    fun gzip(data: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(data) }
        }.toByteArray()
    }

    fun ungzip(data: ByteArray): ByteArray {
        return GZIPInputStream(data.inputStream()).use { it.readBytes() }
    }
}
