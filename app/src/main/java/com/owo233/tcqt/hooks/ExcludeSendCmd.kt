package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.EMPTY_BYTE_ARRAY
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.ext.afterHook
import com.owo233.tcqt.ext.beforeHook
import com.owo233.tcqt.ext.hookMethod
import com.owo233.tcqt.ext.toUtf8ByteArray
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.HostSpecies
import com.owo233.tcqt.utils.PacketUtils
import com.owo233.tcqt.utils.PlatformTools.QQ_9_1_90_26520
import com.owo233.tcqt.hooks.base.hostInfo
import com.owo233.tcqt.utils.logI
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import oicq.wlogin_sdk.tools.cryptor
import java.lang.reflect.Method

@RegisterAction
@RegisterSetting(key = "exclude_send_cmd", name = "排除指定的CMD发送", type = SettingType.BOOLEAN)
@RegisterSetting(key = "exclude_send_cmd.string.cmd", name = "排除的CMD列表", type = SettingType.STRING)
class ExcludeSendCmd: IAction {
    private val cachePackets by lazy { List(5) { getPatchBuffer(50001 + it) } }
    private val cachedBytes by lazy { concatPackets(cachePackets) }
    private val configClass = XpClassLoader.load("com.tencent.freesia.UnitedConfig")!!
    private val codecWarpper = XpClassLoader.load("com.tencent.qphone.base.util.CodecWarpper")!!
    private val teaKey = ByteArray(16)
    private val pack = PacketUtils()
    private var hook: XC_MethodHook.Unhook? = null

    override fun onRun(ctx: Context, process: ActionProcess) {
        msfRollBack() // 还让不让愉快的玩耍了？

        codecWarpper.hookMethod("nativeEncodeRequest", beforeHook { param ->
            val cmd = param.args[5] as? String ?: return@beforeHook
            if (isCmdBlocked(cmd)) {
                param.result = EMPTY_BYTE_ARRAY
                logI(tag = "ExcludeSendCmd", msg = "已处决CMD: $cmd")
            }
        })
    }

    private fun concatPackets(packets: List<ByteArray>): ByteArray {
        val totalSize = packets.sumOf { it.size }
        val output = ByteArray(totalSize)
        var offset = 0
        for (packet in packets) {
            System.arraycopy(packet, 0, output, offset, packet.size)
            offset += packet.size
        }
        return output
    }

    private fun msfRollBack() {
        configClass.hookMethod("isSwitchOn", afterHook { param ->
            val key = param.args[1] as String
            if (key == "msf_init_optimize" || key == "msf_network_service_switch_new") {
                param.result = false
            }
        })

        if (hostInfo.hostSpecies == HostSpecies.QQ && hostInfo.versionCode >= QQ_9_1_90_26520) {
            val hooker = beforeHook { param ->
                hook?.unhook()
                hook = null

                val method = param.method as Method
                method.invoke(param.thisObject, cachedBytes, 0)

                param.result = Unit
            }

            hook = XposedHelpers.findAndHookMethod(
                codecWarpper,
                "nativeOnReceData",
                ByteArray::class.java, Int::class.java,
                hooker
            )
        }
    }

    private fun getPatchBuffer(ssoseq: Int): ByteArray {
        val body = pack.clear()
            .putInt(ssoseq)
            .putInt(0)
            .putInt(28)
            .putBytes("PhoneSigLcCheck succeed.".toUtf8ByteArray())
            .putInt(19)
            .putBytes("PhSigLcId.Check".toUtf8ByteArray())
            .putInt(8)
            .putHex("0B FE DF 42")
            .putHex("00 00 00 00 00 00 00 0A A8 01 00 C8 01 02")
            .putInt(4)
            .putHex("00 00 00 44 10 02 2C 3C 4C 56 01 61 66 01 62 7D")
            .putHex("00 00 2C 08 00 01 06 03 72 65 73 18 00 01 06 17")
            .putHex("4B 51 51 43 6F 6E 66 69 67 2E 53 69 67 6E 61 74")
            .putHex("75 72 65 52 65 73 70 1D 00 00 04 0A 10 01 0B 8C")
            .putHex("98 0C A8 0C")
            .toByteArray()

        val result = pack.clear()
            .putInt(4 + body.size)
            .putBytes(body)
            .toByteArray()

        val enbody = cryptor.encrypt(result, 0, result.size, teaKey)

        val buffer = pack.clear()
            .putInt(10)
            .putByte(0x02)
            .putByte(0x00)
            .putInt(5)
            .putByte(0x30)
            .putBytes(enbody)
            .toByteArray()

        val packet = pack.clear()
            .putInt(4 + buffer.size)
            .putBytes(buffer)
            .toByteArray()

        return packet
    }

    companion object {
        private val excludeCmdString by lazy { GeneratedSettingList.getString(
            GeneratedSettingList.EXCLUDE_SEND_CMD_STRING_CMD)
        }
        private val excludeCmdEnabled by lazy { GeneratedSettingList.getBoolean(
            GeneratedSettingList.EXCLUDE_SEND_CMD)
        }
        private val excludeCmdSet by lazy {
            excludeCmdString.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

        private val plainCmdSet = excludeCmdSet.filter { !it.startsWith('!') }.toSet()
        private val regexList = excludeCmdSet.filter { it.startsWith('!') }
            .map { Regex(it.removePrefix("!")) }
    }

    /**
     * 判断 cmd 是否在排除列表，支持普通字符串匹配和以 '!' 开头的正则表达式匹配
     */
    private fun isCmdBlocked(cmd: String): Boolean {
        if (cmd in plainCmdSet) return true
        return regexList.any { it.matches(cmd) }
    }

    override val key: String get() = GeneratedSettingList.EXCLUDE_SEND_CMD

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MSF)

    override fun canRun(): Boolean {
        return excludeCmdString.isNotBlank() && excludeCmdEnabled
    }
}
