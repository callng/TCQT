package com.owo233.tcqt.features.chat

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 寄生在宿主进程内的透明 Activity：替语音面板调起系统文件选择器。
 *
 * 之所以不用宿主 Dialog 直接选：宿主 Activity 不是 ActivityResultRegistryOwner，
 * Dialog 里的 composition 拿不到 registry。这里借助 TCQT 的寄生 Activity 机制
 * （DynamicActivityRegistry + ParasiticActivity）在宿主进程内起一个真正的 ComponentActivity，
 * 它有完整的 ActivityResultRegistry，选完把 uri 通过 [VoiceFilePicker.deliver] 回传。
 */
class VoicePickerActivity : ComponentActivity() {

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        VoiceFilePicker.deliver(uri)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { picker.launch(arrayOf("audio/*")) }
            .onFailure {
                VoiceFilePicker.deliver(null)
                finish()
            }
    }
}
