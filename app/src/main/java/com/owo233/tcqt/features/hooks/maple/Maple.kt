package com.owo233.tcqt.features.hooks.maple

enum class Maple {
    PublicKernel,  // 9.0.70+
    Kernel,         // 9.0.70-
}

interface IMaple {
    val maple: Maple
}
