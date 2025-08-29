package com.owo233.tcqt.utils

import com.owo233.tcqt.ext.XpClassLoader
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.aio.menu.ui.d

internal object CustomMenu {

    /**
     * 只能在使用NT架构的宿主才能调用本方法！因为之前没有图标只有文本
     */
    @JvmStatic
    fun createItemIconNt(
        msg: Any,
        text: String,
        icon: Int,
        id: Int,
        click: () -> Unit
    ): Any {
        val msgClass = XpClassLoader.load("com.tencent.mobileqq.aio.msg.AIOMsgItem")!!
        if (!msgClass.isInstance(msg)) {
            throw IllegalArgumentException("msg must be AIOMsgItem")
        }

        // 直接用宿主的抽象类写一个本地子类吧，但是这抽象类它是被混淆的，说不定什么时候改了
        val menuItemClass = object : d(msg as AIOMsgItem) {
            override fun b(): Int = icon
            override fun c(): Int = id
            override fun e(): String = text
            override fun f(): String = text
            override fun h() = click()
        }

        return menuItemClass
    }
}
