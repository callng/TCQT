package com.owo233.tcqt.host.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.CrashCatcher
import com.owo233.tcqt.core.log.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 把普通音频文件转成 QQ 语音用的 silk 格式。
 *
 * QQ 的 AIO 语音播放器是 SilkPlayer，只解码 silk；直接发 mp3 会只有气泡没声音。
 * 这里：MediaCodec 解码 -> PCM(16bit mono 24k) -> QQ 的 SilkCodecWrapper 编码 -> .silk 文件。
 */
internal object VoiceSilkEncoder {

    // QQ 语音用 24000Hz（QQAudioUtils.a 索引 3）。比 16000 音质好很多：
    // 手写重采样从 44.1k 降到 16k 时高频混叠严重，听感很"糊"。
    internal const val TARGET_RATE = 24000

    // QQ silk 文件头 = 10 字节：[采样率索引(1)] + "#!SILK_V3"(9)
    // 索引取自 QQAudioUtils.a = {8000,12000,16000,24000,36000,44100,48000}
    private val SAMPLE_RATES = intArrayOf(8000, 12000, 16000, 24000, 36000, 44100, 48000)

    /** 生成 QQ 规范的 10 字节 silk 文件头（含采样率索引字节）。 */
    internal fun silkHeader(rate: Int): ByteArray {
        val idx = SAMPLE_RATES.indexOf(rate).let { if (it < 0) 0 else it }
        val header = ByteArray(10)
        header[0] = idx.toByte()
        System.arraycopy("#!SILK_V3".toByteArray(Charsets.US_ASCII), 0, header, 1, 9)
        return header
    }

    /** 转码为 silk，成功返回生成的 .silk 文件。 */
    fun convert(context: android.content.Context, src: File, outDir: File): File? {
        CrashCatcher.breadcrumb("convert_start ${src.name} ${src.length()}B")
        val decoded = try {
            decodeToPcm(src)
        } catch (t: Throwable) {
            Log.e("VoiceSilkEncoder: 解码异常", t)
            null
        }
        if (decoded == null) {
            Log.e("VoiceSilkEncoder: decodeToPcm 返回 null，无法解码 ${src.name}")
            return null
        }
        val (pcm, rate) = decoded
        CrashCatcher.breadcrumb("decoded ${pcm.size} samples @${rate}Hz")
        if (pcm.isEmpty()) {
            Log.e("VoiceSilkEncoder: 解码结果为空")
            return null
        }
        val mono = if (rate != TARGET_RATE) resample(pcm, rate, TARGET_RATE) else pcm
        lastWaveAmplitudes = computeWaveAmplitudes(mono, TARGET_RATE)

        val silk = try {
            encodeSilk(mono)
        } catch (t: Throwable) {
            Log.e("VoiceSilkEncoder: silk 编码异常", t)
            null
        }
        if (silk == null || silk.isEmpty()) {
            Log.e("VoiceSilkEncoder: encodeSilk 返回 null/空，silk 编码失败")
            return null
        }
        CrashCatcher.breadcrumb("encoded ${silk.size}B")

        return try {
            outDir.mkdirs()
            val out = File(outDir, src.nameWithoutExtension + ".silk")
            out.outputStream().use { os ->
                os.write(silk)
                os.flush()
                runCatching { os.fd.sync() }
            }
            out
        } catch (t: Throwable) {
            Log.e("VoiceSilkEncoder: 写文件失败 ${outDir.absolutePath}", t)
            null
        }
    }

    /** 最近一次转码生成的波形（0-127，对齐 QQ 的 waveAmplitudes）。 */
    @Volatile
    var lastWaveAmplitudes: ArrayList<Byte> = ArrayList()

    /** 由 PCM 计算音量包络（每 100ms 一点，归一化到 0-127）。 */
    private fun computeWaveAmplitudes(pcm: ShortArray, sampleRate: Int): ArrayList<Byte> {
        val result = ArrayList<Byte>()
        if (pcm.isEmpty() || sampleRate <= 0) return result
        val step = (sampleRate / 10).coerceAtLeast(1)   // 100ms
        var i = 0
        while (i < pcm.size) {
            val end = minOf(i + step, pcm.size)
            var sum = 0.0
            for (j in i until end) {
                val v = pcm[j].toDouble()
                sum += v * v
            }
            val rms = kotlin.math.sqrt(sum / (end - i).coerceAtLeast(1))
            val level = ((rms / 32768.0) * 127.0).toInt().coerceIn(0, 127)
            result.add(level.toByte())
            i = end
        }
        return result
    }

    // ── 解码：任意音频 -> PCM 16bit 单声道 ──────────────────────────────────
    private fun decodeToPcm(src: File): Pair<ShortArray, Int>? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(src.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            // 尽量让解码器直接把采样率重采样到目标值（系统重采样器带抗混叠滤波，
            // 质量远好于我们自己的线性插值；解码器不支持时会忽略，下面靠 outputFormat 兜底）。
            runCatching { format.setInteger(MediaFormat.KEY_SAMPLE_RATE, TARGET_RATE) }
            runCatching {
                format.setInteger(
                    MediaFormat.KEY_PCM_ENCODING,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
            }
            codec.configure(format, null, null, 0)
            codec.start()

            // 用 ShortArray 手动扩容，绝不用 ArrayList<Short>：Kotlin 的 ArrayList<Short>
            // 每个元素都装箱成 java.lang.Short 对象，几十秒音频就是几百万个对象，
            // 会直接把 QQ 撑到被 LMK 杀掉（这种被杀不产生 tombstone，所以之前看不到崩溃日志）。
            var pcm = ShortArray(1 shl 18)
            var pcmLen = 0
            fun pushPcm(v: Short) {
                if (pcmLen >= pcm.size) pcm = pcm.copyOf(pcm.size * 2)
                pcm[pcmLen++] = v
            }
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    // MediaExtractor 报的采样率/声道常与实际不符（MP3 头信息不可靠），
                    // 必须以解码器真正输出的格式为准。否则会：立体声没混合、重采样比率算错，
                    // 结果就是"声音完全听不出来源"。
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        runCatching {
                            sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channelCount = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        CrashCatcher.breadcrumb("decoder outputFormat: ${sampleRate}Hz ${channelCount}ch")
                    }
                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        buf.order(ByteOrder.LITTLE_ENDIAN)
                        val shorts = ShortArray(info.size / 2)
                        buf.asShortBuffer().get(shorts)
                        // 多声道 -> 单声道（取平均）
                        if (channelCount > 1) {
                            var i = 0
                            while (i + channelCount <= shorts.size) {
                                var sum = 0
                                for (c in 0 until channelCount) sum += shorts[i + c]
                                pushPcm((sum / channelCount).toShort())
                                i += channelCount
                            }
                        } else {
                            for (s in shorts) pushPcm(s)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) outputDone = true
                    }
                }
            }
            codec.stop()
            codec.release()
            return pcm.copyOf(pcmLen) to sampleRate
        } catch (t: Throwable) {
            Log.e("VoiceSilkEncoder: 解码失败", t)
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    // ── 线性重采样 ────────────────────────────────────────────────────────
    /**
     * 重采样。
     *
     * 降采样时用「区间平均」（等效 box 低通滤波器）—— 纯线性插值在 44.1k→24k/16k
     * 这种大比率降采样时高频会严重混叠，听感就是"糊"。升采样才用线性插值。
     */
    private fun resample(input: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || input.isEmpty()) return input
        val step = from.toDouble() / to
        val outLen = (input.size / step).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        if (from < to) {
            // 升采样：线性插值
            for (i in 0 until outLen) {
                val pos = i * step
                val i0 = pos.toInt().coerceIn(0, input.size - 1)
                val i1 = (i0 + 1).coerceIn(0, input.size - 1)
                val frac = pos - i0
                out[i] = (input[i0] * (1 - frac) + input[i1] * frac).toInt().toShort()
            }
        } else {
            // 降采样：对每个输出点取源区间平均值（抗混叠）
            for (i in 0 until outLen) {
                val start = (i * step).toInt().coerceIn(0, input.size - 1)
                val end = ((i + 1) * step).toInt().coerceIn(start + 1, input.size)
                var sum = 0L
                for (j in start until end) sum += input[j]
                out[i] = (sum / (end - start)).toShort()
            }
        }
        return out
    }

    // ── silk 编码（反射调用 QQ 的 SilkCodecWrapper） ───────────────────────
    //
    // 不同 QQ 版本 SilkCodecWrapper 的方法名/可见性都不一样（9.2.30 没有
    // 9.3.70 上的 b(int,int,int)），所以这里**完全不认名字**：
    //   * 初始化：找参数为 (int,int,int) 的方法
    //   * 编码：找 (long, byte[], byte[], int) -> int
    //   * 拿不到 handle 时，直接调 native 的 (int,int) -> long（= SilkEncoderNew）
    // 并把探测到的方法列表打进日志，便于换版本时排查。
    private fun encodeSilk(pcm: ShortArray): ByteArray? {
        val ctx = HookEnv.hostAppContext
        val loader = ctx.classLoader
        return runCatching {
            val cls = loader.loadClass("com.tencent.mobileqq.utils.SilkCodecWrapper")
            val intT = Int::class.javaPrimitiveType!!
            val longT = Long::class.javaPrimitiveType!!

            val wrapper = runCatching {
                cls.getConstructor(android.content.Context::class.java).newInstance(ctx)
            }.getOrElse {
                cls.getConstructor(
                    android.content.Context::class.java, Boolean::class.javaPrimitiveType
                ).newInstance(ctx, true)
            }

            // 收集本类 + 所有父类的声明方法
            val allMethods = LinkedHashSet<java.lang.reflect.Method>()
            var c: Class<*>? = cls
            while (c != null && c != Any::class.java) {
                allMethods.addAll(c.declaredMethods)
                c = c.superclass
            }
            allMethods.forEach { runCatching { it.isAccessible = true } }

            // 1) 初始化：按签名找“全 int 参数、返回 void”的方法，参数多的优先
            //    （9.2.30 是 a(int,int,int)，9.3.70 是 b(int,int,int)；
            //      万一以后变成 (int,int) 或 (int,int,int,int) 也能命中）
            val initCandidates = allMethods.filter { m ->
                m.returnType == Void.TYPE &&
                        m.parameterTypes.isNotEmpty() &&
                        m.parameterTypes.all { it == intT }
            }.sortedByDescending { it.parameterTypes.size }

            var initCalled = false
            for (m in initCandidates) {
                val args = when (m.parameterTypes.size) {
                    1 -> arrayOf<Any>(TARGET_RATE)
                    2 -> arrayOf<Any>(TARGET_RATE, TARGET_RATE)
                    else -> Array(m.parameterTypes.size) { i ->
                        if (i == 0) TARGET_RATE else if (i == 1) TARGET_RATE else 1
                    }
                }
                val ok = runCatching { m.invoke(wrapper, *args) }.isSuccess
                if (ok) {
                    initCalled = true
                    break
                }
            }
            if (!initCalled) {
                Log.w("VoiceSilkEncoder: 没有可用的 Java 初始化方法，改走 native 直建")
            }

            // 2) handle：先扫字段，扫不到就直接调 native
            var handle = 0L
            var cc: Class<*>? = cls
            while (cc != null && cc != Any::class.java && handle == 0L) {
                for (f in cc.declaredFields) {
                    if (f.type != longT) continue
                    runCatching { f.isAccessible = true }
                    val v = runCatching { f.getLong(wrapper) }.getOrDefault(0L)
                    if (v != 0L) {
                        handle = v
                        break
                    }
                }
                cc = cc.superclass
            }
            if (handle == 0L) {
                val newFn = allMethods.firstOrNull { m ->
                    java.lang.reflect.Modifier.isNative(m.modifiers) &&
                            m.returnType == longT && m.parameterTypes.size == 2 &&
                            m.parameterTypes.all { it == intT }
                }
                if (newFn != null) {
                    handle = newFn.invoke(wrapper, TARGET_RATE, TARGET_RATE) as Long
                }
            }
            if (handle == 0L) {
                Log.e(
                    "VoiceSilkEncoder: 编码器 handle 获取失败（silk native 未就绪）可用方法 = " +
                            allMethods.joinToString(" | ") { m ->
                                "${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }})->${m.returnType.simpleName}"
                            }
                )
                return null
            }

            // 3) 编码方法：(long, byte[], byte[], int) -> int，native 优先
            val encCandidates = allMethods.filter { m ->
                m.returnType == intT && m.parameterTypes.size == 4 &&
                        m.parameterTypes[0] == longT &&
                        m.parameterTypes[1] == ByteArray::class.java &&
                        m.parameterTypes[2] == ByteArray::class.java &&
                        m.parameterTypes[3] == intT
            }
            val encFn = encCandidates.firstOrNull { java.lang.reflect.Modifier.isNative(it.modifiers) }
                ?: encCandidates.firstOrNull()
            if (encFn == null) {
                Log.e("VoiceSilkEncoder: 找不到 encode(long,byte[],byte[],int) 方法")
                return null
            }
            // 4) 释放：(long) -> void（可选）
            val delFn = allMethods.firstOrNull { m ->
                m.returnType == Void.TYPE && m.parameterTypes.size == 1 &&
                        m.parameterTypes[0] == longT
            }

            // silk 输入帧 = 20ms / 16bit / mono
            val frameBytes = TARGET_RATE / 50 * 2
            val out = java.io.ByteArrayOutputStream()
            val encBuf = ByteArray(frameBytes)
            val frame = ByteArray(frameBytes)

            var pos = 0
            var frameCount = 0
            while (pos < pcm.size) {
                java.util.Arrays.fill(frame, 0)
                val n = minOf(frameBytes / 2, pcm.size - pos)
                var k = 0
                while (k < n) {
                    val s = pcm[pos + k].toInt()
                    frame[k * 2] = (s and 0xFF).toByte()
                    frame[k * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    k++
                }
                val len = encFn.invoke(wrapper, handle, frame, encBuf, frameBytes) as Int
                if (len > 0) {
                    frameCount++
                    // silk 帧：2 字节小端长度 + 数据
                    out.write(len and 0xFF)
                    out.write((len shr 8) and 0xFF)
                    out.write(encBuf, 0, len)
                }
                pos += n
            }

            if (delFn != null) runCatching { delFn.invoke(wrapper, handle) }

            if (frameCount == 0 || out.size() == 0) {
                Log.e("VoiceSilkEncoder: 编码输出为空（native 静默返回 0），判定失败")
                return null
            }

            // silk 文件 = 10 字节头([采样率索引] + "#!SILK_V3")
            //           + 若干帧(2字节小端长度 + 数据)
            //           + 0xFFFF 结束标记
            out.write(0xFF)
            out.write(0xFF)
            silkHeader(TARGET_RATE) + out.toByteArray()
        }.onFailure {
            Log.e("VoiceSilkEncoder: silk 编码失败", it)
        }.getOrNull()
    }
}
