@file:Suppress("DEPRECATION")

package com.owo233.tcqt.hooks.func.advanced

import android.annotation.SuppressLint
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.IntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.callOriginal

@RegisterAction
class FakeNetworkStatus : IAction {

    override val key: String get() = "fake_network_status"
    override val processes: Set<ActionProcess>
        get() = setOf(ActionProcess.MAIN, ActionProcess.MSF, ActionProcess.TOOL, ActionProcess.QZONE)
    override val name: String get() = "伪装网络状态"
    override val desc: String get() = "将网络类型伪装为指定的 WIFI / 5G / 4G。"
    override val uiTab: String get() = "高级"
    override val settings: List<Setting<*>>
        get() = listOf(
            IntSetting(
                MODE_KEY,
                "伪装网络类型",
                MODE_WIFI,
                "选择要伪装成的网络环境",
                listOf("WIFI", "5G", "4G")
            )
        )

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onRun(app: Application, process: ActionProcess) {
        hookConnectivityManager()
        hookTelephonyManager()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun hookConnectivityManager() {
        val cm = ConnectivityManager::class.java

        cm.getMethod("getActiveNetworkInfo").hookBefore { param ->
            val real = param.method.callOriginal(param.thisObject) as? NetworkInfo
            if (real != null) {
                applyFakeNetworkInfo(real, currentMode())
                param.result = real
            }
        }

        runCatching {
            cm.getMethod("getNetworkCapabilities", Network::class.java).hookBefore { param ->
                val real = param.method.callOriginal(
                    param.thisObject, *param.args
                ) as? NetworkCapabilities
                if (real != null) {
                    applyFakeTransport(real, currentMode())
                    param.result = real
                }
            }
        }

        runCatching {
            cm.getMethod("getDefaultNetworkCapabilitiesForActiveNetwork").hookBefore { param ->
                val real = param.method.callOriginal(param.thisObject) as? NetworkCapabilities
                if (real != null) {
                    applyFakeTransport(real, currentMode())
                    param.result = real
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun hookTelephonyManager() {
        TelephonyManager::class.java.methods
            .filter { it.name == "getNetworkType" || it.name == "getDataNetworkType" }
            .forEach { method ->
                method.hookBefore { param ->
                    param.result = telephonyNetworkType(currentMode())
                }
            }
    }

    private fun currentMode(): Int = TCQTSetting.getInt(MODE_KEY)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun telephonyNetworkType(mode: Int): Int = when (mode) {
        MODE_WIFI -> TelephonyManager.NETWORK_TYPE_UNKNOWN
        MODE_5G -> TelephonyManager.NETWORK_TYPE_NR
        MODE_4G -> TelephonyManager.NETWORK_TYPE_LTE
        else -> TelephonyManager.NETWORK_TYPE_UNKNOWN
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun applyFakeNetworkInfo(info: NetworkInfo, mode: Int) {
        val type: Int
        val subtype: Int
        val typeName: String
        val subtypeName: String
        when (mode) {
            MODE_WIFI -> {
                type = ConnectivityManager.TYPE_WIFI
                subtype = 0
                typeName = "WIFI"
                subtypeName = ""
            }
            MODE_5G -> {
                type = ConnectivityManager.TYPE_MOBILE
                subtype = TelephonyManager.NETWORK_TYPE_NR
                typeName = "MOBILE"
                subtypeName = "NR"
            }
            MODE_4G -> {
                type = ConnectivityManager.TYPE_MOBILE
                subtype = TelephonyManager.NETWORK_TYPE_LTE
                typeName = "MOBILE"
                subtypeName = "LTE"
            }
            else -> return
        }
        try {
            setNetworkInfoField(info, "mNetworkType", type)
            setNetworkInfoField(info, "mSubtype", subtype)
            setNetworkInfoField(info, "mTypeName", typeName)
            setNetworkInfoField(info, "mSubtypeName", subtypeName)
        } catch (e: Throwable) {
            Log.e("FakeNetworkStatus", e)
        }
    }

    private fun setNetworkInfoField(info: NetworkInfo, name: String, value: Any) {
        val field = NetworkInfo::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(info, value)
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun applyFakeTransport(nc: NetworkCapabilities, mode: Int) {
        try {
            val transportField = NetworkCapabilities::class.java.getDeclaredField("mTransportTypes")
            transportField.isAccessible = true
            transportField.setLong(
                nc,
                if (mode == MODE_WIFI) {
                    1L shl NetworkCapabilities.TRANSPORT_WIFI
                } else {
                    1L shl NetworkCapabilities.TRANSPORT_CELLULAR
                }
            )

            val capField = NetworkCapabilities::class.java.getDeclaredField("mNetworkCapabilities")
            capField.isAccessible = true
            val caps = capField.getLong(nc)
            val notMeteredBit = 1L shl NetworkCapabilities.NET_CAPABILITY_NOT_METERED
            capField.setLong(
                nc,
                if (mode == MODE_WIFI) caps or notMeteredBit else caps and notMeteredBit.inv()
            )
        } catch (e: Throwable) {
            Log.e("FakeNetworkStatus", e)
        }
    }

    private companion object {
        private const val MODE_KEY = "fake_network_status.mode"
        private const val MODE_WIFI = 1
        private const val MODE_5G = 2
        private const val MODE_4G = 3
    }
}
