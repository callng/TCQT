package com.owo233.tcqt.utils

import java.io.*
import kotlin.experimental.xor

/**
 * TeaUtil - 基于 TEA 算法的加密/解密工具类（CBC 模式变种）
 *
 * 说明：
 * - 使用 16 轮 TEA 加密，密钥长度 16 字节（128 位）
 * - 采用类似 CBC 的链式模式，通过前一个密文块异或当前明文
 * - 支持随机填充，防止相同明文生成相同密文
 */
class TeaUtil {
    // 当前处理位置（在明文块中的偏移）
    private var pos = 0

    // 上一个密文块的起始偏移（用于 CBC 异或）
    private var preCrypt = 0

    // 当前密文块的起始偏移
    private var crypt = 0

    // 当前明文处理的总进度（用于解密时判断边界）
    private var contextStart = 0

    // 是否是第一个块（需要特殊处理：与 prePlain 异或）
    private var header = true

    // 加密密钥（16 字节）
    private var key: ByteArray? = null

    // 输出密文缓冲区
    private var out: ByteArray? = null

    // 当前明文块（8 字节）
    private var plain: ByteArray? = null

    // 上一个明文块（用于链式异或）
    private var prePlain: ByteArray? = null

    // 填充状态（用于控制填充逻辑）
    private var padding = 0

    // 随机数生成器（用于生成随机填充字节）
    private val random = java.util.Random()

    /**
     * 加密指定字节数组
     *
     * @param data 明文数据
     * @param key 密钥（必须为 16 字节）
     * @return 加密后的密文
     */
    fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        return encrypt(data, 0, data.size, key)
    }

    /**
     * 解密指定字节数组
     *
     * @param data 密文数据
     * @param key 密钥（必须为 16 字节）
     * @return 解密后的明文，失败返回 null
     */
    fun decrypt(data: ByteArray, key: ByteArray): ByteArray? {
        return decrypt(data, 0, data.size, key)
    }

    /**
     * 核心加密函数
     *
     * 流程：
     * 1. 添加随机头（长度 + 随机字节）
     * 2. 添加 2 字节随机头（协议头）
     * 3. 加密主数据
     * 4. 填充 0 至 8 字节对齐
     * 5. 分块加密（8 字节/块，CBC 模式）
     *
     * @param input 明文数据
     * @param offset 起始偏移
     * @param length 数据长度
     * @param key 密钥
     * @return 密文
     */
    private fun encrypt(
        input: ByteArray,
        offset: Int,
        length: Int,
        key: ByteArray
    ): ByteArray {
        // 初始化工作区
        plain = ByteArray(8)
        prePlain = ByteArray(8)
        this.key = key
        header = true

        // 计算填充长度：使 (length + 10 + padLen) 成为 8 的倍数
        val padLen = (length + 10) % 8
        pos = if (padLen != 0) 8 - padLen else 0

        // 输出缓冲区：padLen + 数据 + 10 字节头部
        out = ByteArray(pos + length + 10)

        val plain = this.plain ?: return out!!

        // 第一个字节：高 5 位随机，低 3 位存储 padLen（0-7）
        plain[0] = ((random.nextInt() and 0xF8) or pos).toByte()

        // 填充前 pos 个随机字节
        for (i in 1..pos) {
            plain[i] = random.nextInt().toByte()
        }
        pos += 1 // 指向下一个位置

        // 初始化 prePlain 为全 0（上一个明文块）
        this.prePlain?.fill(0)

        // 添加 2 个随机字节（协议头）
        padding = 1
        while (padding <= 2) {
            if (pos < 8) {
                plain[pos++] = random.nextInt().toByte()
                padding++
            }
            if (pos == 8) encrypt8Bytes()
        }

        // 加密主数据
        var inputIndex = offset
        var remaining = length
        while (remaining > 0) {
            if (pos < 8) {
                plain[pos++] = input[inputIndex++]
                remaining--
            }
            if (pos == 8) encrypt8Bytes()
        }

        // 填充尾部 0，直到 8 字节对齐
        padding = 1
        while (padding <= 7) {
            if (pos < 8) {
                plain[pos++] = 0
                padding++
            }
            if (pos == 8) encrypt8Bytes()
        }

        return out!!
    }

    /**
     * 加密当前 8 字节明文块
     *
     * 步骤：
     * 1. 当前明文与上一个明文块（或上一个密文块）异或
     * 2. TEA 加密
     * 3. 输出密文再与上一个明文块异或（二次混淆）
     * 4. 更新 prePlain 和 preCrypt
     */
    private fun encrypt8Bytes() {
        val plain = this.plain ?: return
        val prePlain = this.prePlain ?: return
        val out = this.out ?: return

        // 第一步：CBC 异或
        for (i in 0 until 8) {
            plain[i] = if (header) {
                // 第一个块：与 prePlain（初始为 0）异或
                plain[i] xor prePlain[i]
            } else {
                // 后续块：与上一个密文块异或
                plain[i] xor out[preCrypt + i]
            }
        }

        // 第二步：TEA 加密
        val encrypted = encipher(plain) ?: throw IllegalStateException("TEA encryption failed")

        // 第三步：写入输出缓冲区
        System.arraycopy(encrypted, 0, out, crypt, 8)

        // 第四步：密文再与 prePlain 异或（增强安全性）
        for (i in 0 until 8) {
            out[crypt + i] = out[crypt + i] xor prePlain[i]
        }

        // 第五步：更新状态
        System.arraycopy(plain, 0, prePlain, 0, 8) // prePlain = 当前明文
        preCrypt = crypt
        crypt += 8
        pos = 0
        header = false // 后续块不再是头块
    }

    /**
     * TEA 加密核心算法（16 轮）
     *
     * @param data 8 字节明文（v0, v1）
     * @return 8 字节密文
     */
    private fun encipher(data: ByteArray): ByteArray? {
        return try {
            // 拆分 8 字节为两个 32 位无符号整数（v0, v1）
            var v0 = getUnsignedInt(data, 0, 4)
            var v1 = getUnsignedInt(data, 4, 4)

            // 拆分密钥为 4 个 32 位整数
            val k0 = getUnsignedInt(key!!, 0, 4)
            val k1 = getUnsignedInt(key!!, 4, 4)
            val k2 = getUnsignedInt(key!!, 8, 4)
            val k3 = getUnsignedInt(key!!, 12, 4)

            var sum: Long = 0
            var rounds = 16

            while (rounds-- > 0) {
                sum = (sum + 0x9E3779B9L) and 0xFFFFFFFFL // Δ = sqrt(5)/4 * 2^32
                v0 = (v0 + (((v1 shl 4) + k0) xor (v1 + sum) xor ((v1 ushr 5) + k1))) and 0xFFFFFFFFL
                v1 = (v1 + (((v0 shl 4) + k2) xor (v0 + sum) xor ((v0 ushr 5) + k3))) and 0xFFFFFFFFL
            }

            // 写回字节数组
            ByteArrayOutputStream(8).use { baos ->
                DataOutputStream(baos).use { dos ->
                    dos.writeInt(v0.toInt())
                    dos.writeInt(v1.toInt())
                }
                baos.toByteArray()
            }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * TEA 解密核心算法（16 轮）
     *
     * @param data 8 字节密文
     * @param offset 起始偏移
     * @return 8 字节明文
     */
    private fun decipher(data: ByteArray, offset: Int): ByteArray? {
        return try {
            // 读取两个 32 位整数（v0, v1）
            var v0 = getUnsignedInt(data, offset, 4)
            var v1 = getUnsignedInt(data, offset + 4, 4)

            // 读取密钥
            val k0 = getUnsignedInt(key!!, 0, 4)
            val k1 = getUnsignedInt(key!!, 4, 4)
            val k2 = getUnsignedInt(key!!, 8, 4)
            val k3 = getUnsignedInt(key!!, 12, 4)

            // 初始 sum = Δ * 16 = 0xE3779B90
            var sum = 0xE3779B90L
            var rounds = 16

            while (rounds-- > 0) {
                v1 = (v1 - (((v0 shl 4) + k2) xor (v0 + sum) xor ((v0 ushr 5) + k3))) and 0xFFFFFFFFL
                v0 = (v0 - (((v1 shl 4) + k0) xor (v1 + sum) xor ((v1 ushr 5) + k1))) and 0xFFFFFFFFL
                sum = (sum - 0x9E3779B9L) and 0xFFFFFFFFL
            }

            // 写回字节数组
            ByteArrayOutputStream(8).use { baos ->
                DataOutputStream(baos).use { dos ->
                    dos.writeInt(v0.toInt())
                    dos.writeInt(v1.toInt())
                }
                baos.toByteArray()
            }
        } catch (_: IOException) {
            ByteArray(50)
        }
    }

    /**
     * 解密整个字节数组（从偏移 0 开始）
     */
    private fun decipher(data: ByteArray): ByteArray? = decipher(data, 0)

    /**
     * 从字节数组中读取无符号 32 位整数（大端序）
     *
     * @param data 字节数组
     * @param offset 起始位置
     * @param size 读取字节数（最多 4）
     * @return 无符号整数（Long 表示）
     */
    private fun getUnsignedInt(data: ByteArray, offset: Int, size: Int): Long {
        val end = (offset + size).coerceAtMost(data.size)
        var value: Long = 0
        for (i in offset until end) {
            value = (value shl 8) or (data[i].toLong() and 0xFF)
        }
        return value
    }

    /**
     * 解密入口函数
     *
     * @param data 密文
     * @param offset 起始偏移
     * @param length 长度
     * @param key 密钥
     * @return 明文，失败返回 null
     */
    private fun decrypt(
        data: ByteArray,
        offset: Int,
        length: Int,
        key: ByteArray
    ): ByteArray? {
        this.key = key
        preCrypt = 0
        crypt = 0

        // 基本校验：必须是 8 的倍数，且至少 16 字节
        if (length % 8 != 0 || length < 16) return null

        // 解密第一个块（包含头部信息）
        val headerBlock = decipher(data, offset) ?: return null
        prePlain = headerBlock

        // 从第一个字节提取填充长度（低 3 位）
        val padLen = headerBlock[0].toInt() and 7
        val actualLength = length - padLen - 10 // 减去头部和填充
        if (actualLength < 0) return null

        val result = ByteArray(actualLength)
        var resultIndex = 0

        // 初始化状态
        crypt = 8
        contextStart = 8
        pos = 1 // 跳过第一个字节（padLen）
        padding = 1

        // 跳过 2 个随机字节（协议头）
        while (padding <= 2) {
            if (pos < 8) {
                pos++
                padding++
            }
            if (pos == 8 && !decrypt8Bytes(data, offset, length)) return null
        }

        // 解密主数据
        while (resultIndex < actualLength) {
            if (pos < 8) {
                val prePlain = this.prePlain ?: return null
                result[resultIndex++] = prePlain[pos] xor data[preCrypt + offset + pos]
                pos++
            }
            if (pos == 8) {
                preCrypt = crypt - 8
                if (!decrypt8Bytes(data, offset, length)) return null
            }
        }

        // 验证尾部填充是否为 0
        padding = 1
        while (padding < 8) {
            if (pos < 8) {
                val prePlain = this.prePlain ?: return null
                if ((prePlain[pos] xor data[preCrypt + offset + pos]) != 0.toByte()) {
                    return null // 填充不为 0，数据损坏
                }
                pos++
            }
            if (pos == 8) {
                preCrypt = crypt
                if (!decrypt8Bytes(data, offset, length)) return null
            }
            padding++
        }

        return result
    }

    /**
     * 解密 8 字节块（CBC 模式）
     *
     * @param data 密文
     * @param offset 偏移
     * @param length 总长度
     * @return 是否成功
     */
    private fun decrypt8Bytes(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Boolean {
        val prePlain = this.prePlain ?: return false
        pos = 0

        while (pos < 8) {
            if (contextStart + pos >= length) return true
            val srcIndex = crypt + offset + pos
            if (srcIndex >= data.size) return false
            prePlain[pos] = prePlain[pos] xor data[srcIndex]
            pos++
        }

        val decrypted = decipher(prePlain) ?: return false
        this.prePlain = decrypted
        contextStart += 8
        crypt += 8
        pos = 0
        return true
    }
}
