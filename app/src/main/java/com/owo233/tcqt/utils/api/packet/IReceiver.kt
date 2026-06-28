package com.owo233.tcqt.utils.api.packet

fun interface IReceiver {
    fun onReceive(data: ByteArray)
}
