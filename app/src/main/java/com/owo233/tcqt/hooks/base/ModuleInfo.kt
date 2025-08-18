package com.owo233.tcqt.hooks.base

import android.content.res.XModuleResources
import com.owo233.tcqt.MainEntry

lateinit var modulePath: String
lateinit var moduleRes: XModuleResources

val moduleClassLoader: ClassLoader = MainEntry::class.java.classLoader!!
var moduleLoadInit = false