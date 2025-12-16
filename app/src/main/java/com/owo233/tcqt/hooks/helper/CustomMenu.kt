package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.hooks.base.load
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.aio.menu.ui.d
import com.tencent.qqnt.aio.menu.ui.f

internal object CustomMenu {

    @Volatile
    private var cachedType: MenuType? = null

    private enum class MenuType { F, D, NONE }

    @JvmStatic
    fun createItemIconNt(
        msg: Any,
        text: String,
        icon: Int,
        id: Int,
        click: () -> Unit
    ): Any {
        require(AIOMsgItem::class.isInstance(msg)) { "msg must be AIOMsgItem" }

        val type = cachedType ?: detectMenuClass().also { cachedType = it }

        return when (type) {
            MenuType.F -> object : f(msg as AIOMsgItem) {
                override fun b(): Int = icon
                override fun c(): Int = id
                override fun e(): String = text
                override fun f(): String = text
                override fun h() = click()
            }

            MenuType.D -> object : d(msg as AIOMsgItem) {
                override fun b(): Int = icon
                override fun c(): Int = id
                override fun e(): String = text
                override fun f(): String = text
                override fun h() = click()
            }

            else -> error("没有找到合适的抽象菜单类(f/d 都不符合预期),无法创建菜单项.")
        }
    }

    private fun detectMenuClass(): MenuType {
        val clazzF = runCatching { load("com.tencent.qqnt.aio.menu.ui.f") }.getOrNull()
        if (clazzF?.let { !it.isInterface && hasAbstractMenuMethods(it) } == true) return MenuType.F

        val clazzD = runCatching { load("com.tencent.qqnt.aio.menu.ui.d") }.getOrNull()
        if (clazzD?.let { !it.isInterface && hasAbstractMenuMethods(it) } == true) return MenuType.D

        return MenuType.NONE
    }

    private fun hasAbstractMenuMethods(clazz: Class<*>): Boolean {
        val names = clazz.declaredMethods.map { it.name }
        return listOf("b", "c", "e", "f", "h").all(names::contains)
    }
}
