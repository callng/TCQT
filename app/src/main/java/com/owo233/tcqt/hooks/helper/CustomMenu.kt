package com.owo233.tcqt.hooks.helper

import com.android.dx.Code
import com.android.dx.DexMaker
import com.android.dx.FieldId
import com.android.dx.TypeId
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.log.Log
import dalvik.system.InMemoryDexClassLoader
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer

internal object CustomMenu {

    private val candidateNames = arrayOf(
        "com.tencent.qqnt.aio.menu.ui.e", // 2026-08-06 9.3.35 起
        "com.tencent.qqnt.aio.menu.ui.f", // 2025-11-04 9.2.30 起
        "com.tencent.qqnt.aio.menu.ui.d", // 2025-08-28 9.1.35(已知最低版本) 起
    )

    /** 优先识别 icon/id;新版本出现未知名字时回退到名字排序并告警。 */
    private val iconMethodNames = setOf("b")
    private val idMethodNames = setOf("c")

    @Volatile
    private var cachedFactory: MenuItemFactory? = null

    /** 各候选类名的失败记录(类名 → Class 实例);宿主类实例变化(热更新/插件替换)后自动重试。 */
    @Volatile
    private var failedProbes: Map<String, Class<*>> = emptyMap()

    @JvmStatic
    fun createItemIconNt(
        msg: Any,
        text: String,
        icon: Int,
        id: Int,
        click: () -> Unit
    ): Any {
        val factory = resolveFactory()
            ?: error("没有找到合适的抽象菜单类,无法创建菜单项.")

        return factory.create(msg, text, icon, id, click)
    }

    private fun resolveFactory(): MenuItemFactory? {
        cachedFactory?.let { if (!it.isStale()) return it }
        return synchronized(this) {
            val cached = cachedFactory
            if (cached != null && !cached.isStale()) cached
            else detectFactory().also { cachedFactory = it }
        }
    }

    private fun detectFactory(): MenuItemFactory? {
        for (name in candidateNames) {
            val clazz = load(name) ?: continue
            if (failedProbes[name] === clazz) continue
            val factory = try {
                MenuItemFactory.build(clazz)
            } catch (_: Throwable) {
                // Log.w("CustomMenu: 候选类 $name 不适用,跳过 (${e.message})", e)
                failedProbes = failedProbes + (name to clazz)
                continue
            }
            return factory
        }
        return null
    }

    private class MenuItemFactory private constructor(
        private val className: String,
        private val menuClass: Class<*>,
        private val msgType: Class<*>,
        private val constructor: Constructor<*>,
    ) {
        fun isStale(): Boolean = load(className) !== menuClass

        fun create(msg: Any, text: String, icon: Int, id: Int, click: () -> Unit): Any {
            require(msgType.isInstance(msg)) {
                "msg 类型不符: 期望 ${msgType.name},实际 ${msg.javaClass.name}"
            }
            return constructor.newInstance(msg, icon, id, text, Runnable { click() })
        }

        companion object {

            fun build(menuClass: Class<*>): MenuItemFactory {
                val abstractMethods = collectAbstractMethods(menuClass)
                require(abstractMethods.size == 5 && abstractMethods.all { it.parameterCount == 0 }) {
                    "菜单抽象类结构不符合预期: 抽象方法=${abstractMethods.size} 个(需 5 个且全部无参)"
                }

                // 按返回类型分组,再按语义角色分配
                val intMethods = abstractMethods.filter { it.returnType == Int::class.javaPrimitiveType }
                val stringMethods = abstractMethods.filter { it.returnType == String::class.java }
                val voidMethods = abstractMethods.filter { it.returnType == Void.TYPE }
                require(intMethods.size == 2 && stringMethods.size == 2 && voidMethods.size == 1) {
                    "菜单抽象类结构不符合预期: int=${intMethods.size} string=${stringMethods.size} void=${voidMethods.size}"
                }
                val (iconMethod, idMethod) = assignIntRoles(intMethods)

                // 父类构造器:优先接收 AIOMsgItem 的单参构造器,退而求其次取任意单参构造器
                val superCtor = menuClass.constructors.firstOrNull {
                    it.parameterCount == 1 && it.parameterTypes[0].name == AIO_MSG_ITEM
                } ?: menuClass.constructors.firstOrNull { it.parameterCount == 1 }
                    ?: error("菜单抽象类没有单参数构造器: ${menuClass.name}")
                val msgType = superCtor.parameterTypes[0]

                val dexMaker = DexMaker()
                val generatedName = "Lcom/owo233/tcqt/gen/MenuItem" +
                    Integer.toHexString(menuClass.name.hashCode()) + ";"
                val generatedType = TypeId.get<Any>(generatedName)
                @Suppress("UNCHECKED_CAST")
                val superType = TypeId.get(menuClass as Class<Any>)
                dexMaker.declare(generatedType, "MenuItem.generated", Modifier.PUBLIC, superType)

                // 实例字段(图标/id/文本/点击回调)
                val iconField = generatedType.getField(TypeId.INT, "icon")
                val idField = generatedType.getField(TypeId.INT, "id")
                val textField = generatedType.getField(TypeId.STRING, "text")
                val clickField = generatedType.getField(TypeId.get(Runnable::class.java), "click")
                dexMaker.declare(iconField, Modifier.PRIVATE, null)
                dexMaker.declare(idField, Modifier.PRIVATE, null)
                dexMaker.declare(textField, Modifier.PRIVATE, null)
                dexMaker.declare(clickField, Modifier.PRIVATE, null)

                // 构造器: (msg, icon, id, text, click)
                val msgTypeId = TypeId.get(msgType)
                val ctorId = generatedType.getConstructor(
                    msgTypeId, TypeId.INT, TypeId.INT, TypeId.STRING,
                    TypeId.get(Runnable::class.java)
                )
                emitConstructor(
                    dexMaker.declare(ctorId, Modifier.PUBLIC),
                    generatedType, superType, msgTypeId,
                    iconField, idField, textField, clickField
                )

                // 抽象方法:按语义角色分派到对应字段
                emitIntGetter(dexMaker, generatedType, iconMethod.name, iconField)
                emitIntGetter(dexMaker, generatedType, idMethod.name, idField)
                stringMethods.forEach { emitStringGetter(dexMaker, generatedType, it.name, textField) }
                emitClick(dexMaker, generatedType, voidMethods[0].name, clickField)

                // 内存中生成 dex 并加载(API 26+),父加载器用宿主 ClassLoader 以解析宿主类
                val dexBytes = dexMaker.generate()
                val loader = InMemoryDexClassLoader(ByteBuffer.wrap(dexBytes), HookEnv.hostClassLoader)
                val generatedClass = loader.loadClass(
                    generatedName.removePrefix("L").removeSuffix(";").replace('/', '.')
                )
                require(!Modifier.isAbstract(generatedClass.modifiers)) {
                    "生成的菜单子类仍是抽象类: ${generatedClass.name}"
                }
                val constructor = generatedClass.getConstructor(
                    msgType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    String::class.java, Runnable::class.java
                )
                return MenuItemFactory(menuClass.name, menuClass, msgType, constructor)
            }

            private const val AIO_MSG_ITEM = "com.tencent.mobileqq.aio.msg.AIOMsgItem"

            /**
             * 决定两个 int 抽象方法哪个是 icon、哪个是 id。
             * 优先用历史方法名映射(所有已知版本 icon 方法名都是 b、id 都是 c);
             * 只识别出其中一个时,另一个直接取剩余方法,不再排序(避免排序颠倒已识别语义);
             * 两个都未知时回退到名字排序,并打 warning 提醒人工核对映射表。
             */
            private fun assignIntRoles(intMethods: List<Method>): Pair<Method, Method> {
                val icon = intMethods.firstOrNull { it.name in iconMethodNames }
                val id = intMethods.firstOrNull { it.name in idMethodNames }
                return when {
                    icon != null && id != null && icon !== id -> icon to id
                    icon != null -> {
                        Log.w("CustomMenu: 未识别 id 方法名(int: ${intMethods.map { it.name }}),假定 ${icon.name}=icon,${intMethods.first { it !== icon }.name}=id")
                        icon to intMethods.first { it !== icon }
                    }
                    id != null -> {
                        Log.w("CustomMenu: 未识别 icon 方法名(int: ${intMethods.map { it.name }}),假定 ${intMethods.first { it !== id }.name}=icon,${id.name}=id")
                        intMethods.first { it !== id } to id
                    }
                    else -> {
                        val sorted = intMethods.sortedBy { it.name }
                        Log.w(
                            "CustomMenu: 无法按已知映射识别 icon/id(int 方法名: ${intMethods.map { it.name }})," +
                                "按名字序假定 icon=${sorted[0].name}, id=${sorted[1].name}," +
                                "请核对后将新方法名补入 iconMethodNames/idMethodNames"
                        )
                        sorted[0] to sorted[1]
                    }
                }
            }

            /** 收集本类及所有父类(不含 Object)的抽象方法,按完整签名(返回类型+方法名+参数类型)去重。 */
            private fun collectAbstractMethods(clazz: Class<*>): List<Method> {
                val seen = HashSet<String>()
                val result = ArrayList<Method>()
                var c: Class<*>? = clazz
                while (c != null && c != Any::class.java) {
                    for (m in c.declaredMethods) {
                        if (Modifier.isAbstract(m.modifiers) && seen.add(methodSignatureKey(m))) {
                            result.add(m)
                        }
                    }
                    c = c.superclass
                }
                return result
            }

            /** 完整方法签名,可区分同名不同参的重载方法。 */
            private fun methodSignatureKey(m: Method): String =
                m.returnType.name + " " + m.name +
                    "(" + m.parameterTypes.joinToString(",") { it.name } + ")"

            private fun emitConstructor(
                code: Code,
                generatedType: TypeId<Any>,
                superType: TypeId<Any>,
                msgTypeId: TypeId<*>,
                iconField: FieldId<Any, Int>,
                idField: FieldId<Any, Int>,
                textField: FieldId<Any, String>,
                clickField: FieldId<Any, Runnable>,
            ) {
                val thisLocal = code.getThis(generatedType)
                // super(msg)
                code.invokeDirect(
                    superType.getConstructor(msgTypeId), null, thisLocal,
                    code.getParameter(0, msgTypeId)
                )
                code.iput(iconField, thisLocal, code.getParameter(1, TypeId.INT))
                code.iput(idField, thisLocal, code.getParameter(2, TypeId.INT))
                code.iput(textField, thisLocal, code.getParameter(3, TypeId.STRING))
                code.iput(clickField, thisLocal, code.getParameter(4, TypeId.get(Runnable::class.java)))
                code.returnVoid()
            }

            private fun emitIntGetter(
                dexMaker: DexMaker,
                generatedType: TypeId<Any>,
                name: String,
                field: FieldId<Any, Int>,
            ) {
                val code = dexMaker.declare(generatedType.getMethod(TypeId.INT, name), Modifier.PUBLIC)
                val result = code.newLocal(TypeId.INT)
                code.iget(field, result, code.getThis(generatedType))
                code.returnValue(result)
            }

            private fun emitStringGetter(
                dexMaker: DexMaker,
                generatedType: TypeId<Any>,
                name: String,
                field: FieldId<Any, String>,
            ) {
                val code = dexMaker.declare(generatedType.getMethod(TypeId.STRING, name), Modifier.PUBLIC)
                val result = code.newLocal(TypeId.STRING)
                code.iget(field, result, code.getThis(generatedType))
                code.returnValue(result)
            }

            private fun emitClick(
                dexMaker: DexMaker,
                generatedType: TypeId<Any>,
                name: String,
                field: FieldId<Any, Runnable>,
            ) {
                val code = dexMaker.declare(generatedType.getMethod(TypeId.VOID, name), Modifier.PUBLIC)
                val clickLocal = code.newLocal(TypeId.get(Runnable::class.java))
                code.iget(field, clickLocal, code.getThis(generatedType))
                code.invokeInterface(
                    TypeId.get(Runnable::class.java).getMethod(TypeId.VOID, "run"),
                    null, clickLocal
                )
                code.returnVoid()
            }
        }
    }
}
