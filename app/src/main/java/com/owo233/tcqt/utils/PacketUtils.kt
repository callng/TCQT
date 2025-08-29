package com.owo233.tcqt.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketUtils(private val defaultOrder: ByteOrder = ByteOrder.BIG_ENDIAN) {

    // 每个线程一个 ByteBuffer
    private val threadLocalBuffer = ThreadLocal<ByteBuffer>()

    /**
     * 确保当前线程的 buffer 已初始化
     */
    private fun ensureBufferInitialized(): ByteBuffer {
        var buffer = threadLocalBuffer.get()
        if (buffer == null) {
            buffer = ByteBuffer.allocate(1024).order(defaultOrder)
            threadLocalBuffer.set(buffer)
        }
        return buffer
    }

    /**
     * 确保当前 buffer 有足够的空间
     */
    private fun ensureCapacity(additional: Int) {
        val buffer = ensureBufferInitialized()
        if (buffer.remaining() < additional) {
            val newCapacity = maxOf((buffer.capacity() * 2), buffer.position() + additional)
            val newBuffer = ByteBuffer.allocate(newCapacity).order(buffer.order())
            buffer.flip()
            newBuffer.put(buffer)
            threadLocalBuffer.set(newBuffer)
        }
    }

    /**
     * 设置字节序
     */
    fun setByteOrder(order: ByteOrder): PacketUtils {
        val buffer = ensureBufferInitialized()
        threadLocalBuffer.set(buffer.order(order))
        return this
    }

    /**
     * 清空当前 buffer（position = 0）
     */
    fun clear(): PacketUtils {
        val buffer = ensureBufferInitialized()
        buffer.clear()
        return this
    }

    /**
     * 写入单个字节
     */
    fun putByte(value: Byte): PacketUtils {
        ensureCapacity(1)
        ensureBufferInitialized().put(value)
        return this
    }

    /**
     * 写入 short（2 字节）
     */
    fun putShort(value: Short): PacketUtils {
        ensureCapacity(2)
        ensureBufferInitialized().putShort(value)
        return this
    }

    /**
     * 写入 int（4 字节）
     */
    fun putInt(value: Int): PacketUtils {
        ensureCapacity(4)
        ensureBufferInitialized().putInt(value)
        return this
    }

    /**
     * 写入 long（8 字节）
     */
    fun putLong(value: Long): PacketUtils {
        ensureCapacity(8)
        ensureBufferInitialized().putLong(value)
        return this
    }

    /**
     * 写入 float
     */
    fun putFloat(value: Float): PacketUtils {
        ensureCapacity(4)
        ensureBufferInitialized().putFloat(value)
        return this
    }

    /**
     * 写入 double
     */
    fun putDouble(value: Double): PacketUtils {
        ensureCapacity(8)
        ensureBufferInitialized().putDouble(value)
        return this
    }

    /**
     * 写入字节数组
     */
    fun putBytes(data: ByteArray): PacketUtils {
        if (data.isEmpty()) return this
        ensureCapacity(data.size)
        ensureBufferInitialized().put(data)
        return this
    }

    /**
     * 写入十六进制字符串（如 "0A 0B 0C"）
     */
    fun putHex(hex: String): PacketUtils {
        val bytes = hexStringToByteArray(hex)
        return putBytes(bytes)
    }

    /**
     * 获取当前写入位置
     */
    fun getPosition(): Int {
        return ensureBufferInitialized().position()
    }

    /**
     * 转换为字节数组（只复制已写入部分）
     */
    fun toByteArray(): ByteArray {
        val buffer = ensureBufferInitialized()
        val size = buffer.position()
        val array = ByteArray(size)
        buffer.duplicate().apply { rewind() }.get(array)
        return array
    }

    /**
     * 将十六进制字符串转为字节数组
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.filter { !it.isWhitespace() }
        require(cleanHex.length % 2 == 0) { "Invalid hex string length: $cleanHex" }
        return ByteArray(cleanHex.length / 2) { index ->
            cleanHex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
