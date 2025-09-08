@file:OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.owo233.tcqt.hooks.helper

import com.tencent.qqnt.kernel.api.IKernelService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.ExperimentalSerializationApi

internal object NTServiceFetcher {
    private lateinit var iKernelService: IKernelService
    private var curKernelHash = 0

    fun onFetch(service: IKernelService) {
        val msgService = service.msgService ?: return
        val curHash = service.hashCode() + msgService.hashCode()

        if (isInitForNt(curHash)) return

        curKernelHash = curHash
        this.iKernelService = service
    }

    private fun isInitForNt(hash: Int) = hash == curKernelHash

    val kernelService: IKernelService
        get() = iKernelService
}
