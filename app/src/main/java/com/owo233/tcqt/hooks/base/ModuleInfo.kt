package com.owo233.tcqt.hooks.base

import android.content.res.XModuleResources
import com.owo233.tcqt.MainEntry
import com.owo233.tcqt.utils.invoke

lateinit var modulePath: String
lateinit var moduleRes: XModuleResources

val moduleClassLoader: ClassLoader = MainEntry::class.java.classLoader!!
var moduleLoadInit = false

fun getModuleFilePath(): String {
    runCatching {
        return MainEntry::class.java.classLoader!!
            .invoke("findResource", "AndroidManifest.xml")!!
            .invoke("getPath") as String
    }
    return modulePath
}
