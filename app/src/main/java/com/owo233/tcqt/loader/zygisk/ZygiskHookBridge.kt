package com.owo233.tcqt.loader.zygisk

import android.util.Log
import androidx.annotation.Keep
import com.android.dx.DexMaker
import com.android.dx.FieldId
import com.android.dx.MethodId
import com.android.dx.TypeId
import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.loader.api.HookParam
import com.owo233.tcqt.loader.api.Invoker
import com.owo233.tcqt.loader.api.Unhook
import dalvik.system.DexFile
import dalvik.system.InMemoryDexClassLoader
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

@Keep
internal object ZygiskHookBridge {

    private const val TAG = "ZygiskHookBridge"

    private external fun nativeGetArtMethod(executable: Executable): Long
    private external fun nativeHookMethod(targetArt: Long, backupArt: Long, bridgeArt: Long): Int
    private external fun nativeUnhookMethod(targetArt: Long, backupArt: Long): Int
    private external fun nativeTrustDexFile(dexFile: DexFile): Boolean

    private enum class Mode { BEFORE, AFTER, REPLACE }

    /** 一次独立的回调注册；unhook 时按 identity 移除。 */
    private class Registration(val priority: Int, val mode: Mode, val callback: Any)

    private class HookEntry(val member: Member, val backupMethod: Method) {
        val callbacks: CopyOnWriteArrayList<Registration> = CopyOnWriteArrayList()
    }

    private val hooks = ConcurrentHashMap<Long, HookEntry>()
    private val memberToHookId = ConcurrentHashMap<Member, Long>()
    private val idSeq = AtomicLong(0)
    private val hookLock = Any()

    // ── 对外 hook API（IHookEngine 调用） ───────────────────────────────────

    fun hookBefore(member: Member, priority: Int, callback: (HookParam) -> Unit): Unhook =
        install(member, Registration(priority, Mode.BEFORE, callback))

    fun hookAfter(member: Member, priority: Int, callback: (HookParam) -> Unit): Unhook =
        install(member, Registration(priority, Mode.AFTER, callback))

    fun hookReplace(member: Member, priority: Int, callback: (Chain) -> Any?): Unhook =
        install(member, Registration(priority, Mode.REPLACE, callback))

    private fun install(member: Member, reg: Registration): Unhook {
        require(member is Method || member is Constructor<*>) {
            "ZygiskHookBridge: unsupported member type ${member.javaClass}"
        }
        // Native members are hookable: art_hook_method snapshots the whole
        // ArtMethod (including the JNI binding `data_` and the original JNI
        // stub entry point) into the generated backup method before touching
        // the target, so invoking the backup runs the original native
        // implementation. Only abstract members (no executable code) cannot
        // be hooked.
        require(!Modifier.isAbstract(member.modifiers)) {
            "ZygiskHookBridge: cannot hook abstract member $member"
        }

        val hookId = synchronized(hookLock) {
            val existing = memberToHookId[member]
            if (existing != null) {
                existing
            } else {
                val targetArt = nativeGetArtMethod(member)
                require(targetArt != 0L) { "ZygiskHookBridge: art_get_art_method returned 0 for $member" }

                val pair = generateBridgePair(member)
                val backupArt = nativeGetArtMethod(pair.backupMethod)
                val bridgeArt = nativeGetArtMethod(pair.bridgeMethod)
                require(backupArt != 0L && bridgeArt != 0L) {
                    "ZygiskHookBridge: art_get_art_method returned 0 for generated bridge"
                }

                val newId = idSeq.incrementAndGet()
                hooks[newId] = HookEntry(member, pair.backupMethod)
                memberToHookId[member] = newId

                // hookId 必须先写入生成的静态字段，再安装 entry_point。
                pair.setHookId(newId)

                val rc = nativeHookMethod(targetArt, backupArt, bridgeArt)
                if (rc != 0) {
                    hooks.remove(newId)
                    memberToHookId.remove(member)
                    error("ZygiskHookBridge: nativeHookMethod failed (rc=$rc) for $member")
                }
                newId
            }
        }

        val registration = addCallback(hookId, reg)
        return Unhook { removeCallback(hookId, registration, member) }
    }

    private fun addCallback(hookId: Long, reg: Registration): Registration {
        val entry = requireNotNull(hooks[hookId]) { "$TAG: no entry for hookId=$hookId" }
        synchronized(entry.callbacks) {
            val idx = entry.callbacks.indexOfFirst { it.priority < reg.priority }
            if (idx == -1) entry.callbacks.add(reg) else entry.callbacks.add(idx, reg)
        }
        return reg
    }

    private fun removeCallback(hookId: Long, reg: Registration, member: Member) {
        synchronized(hookLock) {
            val entry = hooks[hookId] ?: return
            if (!entry.callbacks.remove(reg)) return
            if (entry.callbacks.isNotEmpty()) return

            // 最后一个回调被移除：真正卸载 native hook。
            val targetArt = nativeGetArtMethod(member as Executable)
            val backupArt = nativeGetArtMethod(entry.backupMethod)
            if (targetArt != 0L && backupArt != 0L && nativeUnhookMethod(targetArt, backupArt) == 0) {
                hooks.remove(hookId)
                memberToHookId.remove(member)
            } else {
                Log.e(TAG, "failed to unhook $member; callback removed but native hook kept")
            }
        }
    }

    // ── Invoke original（Invoker 用） ────────────────────────────────────────

    fun invokeOriginal(member: Member, thisObject: Any?, args: Array<Any?>): Any? {
        val id = memberToHookId[member]
            ?: throw IllegalArgumentException("ZygiskHookBridge: member is not hooked: $member")
        val entry = hooks[id]
            ?: throw IllegalArgumentException("ZygiskHookBridge: member is not hooked: $member")
        return try {
            invokeBackup(entry, thisObject, args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun invokeBackup(entry: HookEntry, thisObject: Any?, args: Array<Any?>): Any? {
        val backup = entry.backupMethod
        return if (Modifier.isStatic(entry.member.modifiers)) {
            backup.invoke(null, *args)
        } else {
            backup.invoke(thisObject, *args)
        }
    }

    // ── Dispatch（由生成的 bridge 方法调用） ────────────────────────────────

    @JvmStatic
    @Keep
    fun dispatch(hookId: Long, thisObject: Any?, args: Array<Any?>): Any? {
        val entry = hooks[hookId]
            ?: run {
                // Unhook 与进行中调用的窗口：entry 已被移除但 trampoline 入口
                // 仍可能被正在执行的调用命中。此时不能把异常抛进宿主——异常
                // 会穿越 trampoline 的裸跳帧,静默返回 null 即可。
                Log.w(TAG, "dispatch: no entry for hookId=$hookId (unhooked?)")
                return null
            }
        val param = MutableHookParam(entry.member, thisObject, args)
        val snapshot = entry.callbacks.toList()

        // ── before 阶段（priority 降序） ────────────────────────────────────
        var beforeCount = 0
        for (reg in snapshot) {
            if (param.earlyReturn) break
            try {
                when (reg.mode) {
                    Mode.BEFORE -> (reg.callback as (HookParam) -> Unit).invoke(param)
                    Mode.REPLACE -> {
                        val chain = ZygiskChain(param, entry)
                        // result 的 setter 会置 earlyReturn
                        param.result = (reg.callback as (Chain) -> Any?).invoke(chain)
                    }
                    Mode.AFTER -> Unit
                }
            } catch (t: Throwable) {
                // 与 Xposed 一致：before 回调抛异常视为回调失败，不中断原方法。
                Log.e(TAG, "before callback failed for ${entry.member}", t)
                param.resetAfterBeforeFailure()
            }
            beforeCount++
        }

        // ── 调用原方法（backup） ────────────────────────────────────────────
        if (!param.earlyReturn) {
            try {
                val self = if (Modifier.isStatic(entry.member.modifiers)) null else param.thisObject
                param.result = invokeBackup(entry, self, param.args)
            } catch (e: InvocationTargetException) {
                param.throwable = e.targetException ?: e
            } catch (t: Throwable) {
                param.throwable = t
            }
        }

        // ── after 阶段（逆序） ──────────────────────────────────────────────
        for (i in beforeCount - 1 downTo 0) {
            val reg = snapshot[i]
            if (reg.mode != Mode.AFTER) continue
            val state = param.snapshot()
            try {
                (reg.callback as (HookParam) -> Unit).invoke(param)
            } catch (t: Throwable) {
                // 恢复进入该回调前的状态，继续执行剩余 after 回调。
                Log.e(TAG, "after callback failed for ${entry.member}", t)
                param.restore(state)
            }
        }

        val finalThrowable = param.throwable
        val finalResult = param.result
        param.clear()
        finalThrowable?.let { throw it }
        return finalResult
    }

    private class MutableHookParam(
        override val method: Member,
        private val receiver: Any?,
        override var args: Array<Any?>,
    ) : HookParam {

        override val thisObject: Any get() = ZygiskThisObject.get(receiver)

        private var resultValue: Any? = null
        private var throwableValue: Throwable? = null
        var earlyReturn = false
            private set

        override var result: Any?
            get() = resultValue
            set(value) {
                resultValue = value
                throwableValue = null
                earlyReturn = true
            }

        override var throwable: Throwable?
            get() = throwableValue
            set(value) {
                throwableValue = value
                resultValue = null
                earlyReturn = true
            }

        fun snapshot(): Pair<Any?, Throwable?> = resultValue to throwableValue

        fun restore(state: Pair<Any?, Throwable?>) {
            resultValue = state.first
            throwableValue = state.second
        }

        fun resetAfterBeforeFailure() {
            resultValue = null
            throwableValue = null
            earlyReturn = false
        }

        fun clear() {
            earlyReturn = false
        }
    }

    private class ZygiskChain(
        private val param: MutableHookParam,
        private val entry: HookEntry,
    ) : Chain, HookParam by param {

        override fun proceed(args: Array<Any?>): Any? {
            val self = if (Modifier.isStatic(entry.member.modifiers)) null else param.thisObject
            return invokeBackup(entry, self, args)
        }
    }

    // ── DexMaker bridge/backup 生成 ─────────────────────────────────────────

    private data class BridgePair(
        val bridgeMethod: Method,
        val backupMethod: Method,
        /** 生成类写入 hookId 的入口，必须在 nativeHookMethod 之前调用。 */
        val setHookId: (Long) -> Unit,
    )

    private val bridgeCounter = AtomicLong(0)

    private val OBJ = TypeId.OBJECT as TypeId<Any>
    @Suppress("UNCHECKED_CAST")
    private val OBJ_ARR = TypeId.get<Array<Any?>>("[Ljava/lang/Object;")

    private fun generateBridgePair(target: Executable): BridgePair {
        val targetParams = target.parameterTypes
        val targetReturn: Class<*> = when (target) {
            is Method -> target.returnType
            is Constructor<*> -> Void.TYPE
            else -> Void.TYPE
        }

        val suffix = java.lang.Long.toHexString(bridgeCounter.incrementAndGet())
        val fqn = "com.owo233.tcqt.loader.zygisk.HkBr$suffix"
        val descriptor = "L${fqn.replace('.', '/')};"

        val dm = DexMaker()
        val classId = TypeId.get<Any>(descriptor)
        dm.declare(classId, fqn, Modifier.PUBLIC, OBJ)

        // static long hookId
        @Suppress("UNCHECKED_CAST")
        val hookIdFld = classId.getField(TypeId.LONG, "hookId") as FieldId<Any, Long>
        dm.declare(hookIdFld, Modifier.PUBLIC or Modifier.STATIC, 0L)

        // ZygiskHookBridge.dispatch(long, Object, Object[]) -> Object
        val rtFqn = "com/owo233/tcqt/loader/zygisk/ZygiskHookBridge"
        @Suppress("UNCHECKED_CAST")
        val rtType = TypeId.get<Any>("L$rtFqn;")
        @Suppress("UNCHECKED_CAST")
        val dispatchMid = rtType.getMethod(OBJ, "dispatch", TypeId.LONG, OBJ, OBJ_ARR) as MethodId<Any, Any>

        val isStatic = target is Method && Modifier.isStatic(target.modifiers)

        // 引用参数统一用 Object（避免引用宿主私有类）；基本类型保持精确。
        val dexParamClasses = Array(targetParams.size) { i ->
            if (targetParams[i].isPrimitive) targetParams[i] else Any::class.java
        }
        val dexReturnClass: Class<*> = when {
            targetReturn == Void.TYPE || targetReturn.isPrimitive -> targetReturn
            else -> Any::class.java
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> tid(c: Class<T>): TypeId<T> = when (c) {
            Int::class.javaPrimitiveType -> TypeId.INT as TypeId<T>
            Long::class.javaPrimitiveType -> TypeId.LONG as TypeId<T>
            Boolean::class.javaPrimitiveType -> TypeId.BOOLEAN as TypeId<T>
            Byte::class.javaPrimitiveType -> TypeId.BYTE as TypeId<T>
            Char::class.javaPrimitiveType -> TypeId.CHAR as TypeId<T>
            Short::class.javaPrimitiveType -> TypeId.SHORT as TypeId<T>
            Float::class.javaPrimitiveType -> TypeId.FLOAT as TypeId<T>
            Double::class.javaPrimitiveType -> TypeId.DOUBLE as TypeId<T>
            Void.TYPE -> TypeId.VOID as TypeId<T>
            else -> TypeId.get(c)
        }

        val allParamTids: Array<TypeId<*>> = Array(targetParams.size) { i -> tid(dexParamClasses[i]) }
        val retTid = tid(dexReturnClass)

        data class BoxInfo(val wrapperTid: TypeId<*>, val valueOfMid: MethodId<*, *>, val unboxMid: MethodId<*, *>)

        fun boxInfo(prim: Class<*>): BoxInfo? {
            if (!prim.isPrimitive || prim == Void.TYPE) return null
            val wrapperClass: Class<*> = when (prim) {
                Int::class.javaPrimitiveType -> java.lang.Integer::class.java
                Long::class.javaPrimitiveType -> java.lang.Long::class.java
                Boolean::class.javaPrimitiveType -> java.lang.Boolean::class.java
                Byte::class.javaPrimitiveType -> java.lang.Byte::class.java
                Char::class.javaPrimitiveType -> java.lang.Character::class.java
                Short::class.javaPrimitiveType -> java.lang.Short::class.java
                Float::class.javaPrimitiveType -> java.lang.Float::class.java
                Double::class.javaPrimitiveType -> java.lang.Double::class.java
                else -> return null
            }
            val unboxName = when (prim) {
                Int::class.javaPrimitiveType -> "intValue"
                Long::class.javaPrimitiveType -> "longValue"
                Boolean::class.javaPrimitiveType -> "booleanValue"
                Byte::class.javaPrimitiveType -> "byteValue"
                Char::class.javaPrimitiveType -> "charValue"
                Short::class.javaPrimitiveType -> "shortValue"
                Float::class.javaPrimitiveType -> "floatValue"
                Double::class.javaPrimitiveType -> "doubleValue"
                else -> return null
            }
            @Suppress("UNCHECKED_CAST")
            val wTid = TypeId.get(wrapperClass as Class<Any>)
            val primTid = tid(prim)
            @Suppress("UNCHECKED_CAST")
            return BoxInfo(
                wrapperTid = wTid,
                valueOfMid = wTid.getMethod(wTid, "valueOf", primTid),
                unboxMid = wTid.getMethod(primTid, unboxName),
            )
        }

        fun declareBridgeOrBackup(name: String, isBackup: Boolean) {
            @Suppress("UNCHECKED_CAST")
            val mid = classId.getMethod(retTid, name, *allParamTids) as MethodId<Any, Any>
            val modifiers = Modifier.PUBLIC or if (isStatic) Modifier.STATIC else 0
            val code = dm.declare(mid, modifiers)

            if (isBackup) {
                // body 永不执行：native 层会用目标原 ArtMethod 覆盖 backup。
                @Suppress("UNCHECKED_CAST")
                val usoeTid = TypeId.get<UnsupportedOperationException>(
                    "Ljava/lang/UnsupportedOperationException;")
                val exLocal = code.newLocal(usoeTid)
                val msgLocal = code.newLocal(TypeId.STRING)
                code.loadConstant(msgLocal, "backup not initialized")
                @Suppress("UNCHECKED_CAST")
                val ctorMid = usoeTid.getConstructor(TypeId.STRING) as MethodId<UnsupportedOperationException, Void>
                code.newInstance(exLocal, ctorMid, msgLocal)
                code.throwValue(exLocal)
                return
            }

            val receiver = if (isStatic) null else code.getThis(classId)
            @Suppress("UNCHECKED_CAST")
            val parameterLocals = Array(targetParams.size) { i ->
                code.getParameter(i, allParamTids[i] as TypeId<Any>)
            }
            val parameterBoxInfos = Array(targetParams.size) { i -> boxInfo(targetParams[i]) }
            val hookIdLocal = code.newLocal(TypeId.LONG)
            val selfLocal = code.newLocal(OBJ)
            val sizeLocal = code.newLocal(TypeId.INT)
            val argsLocal = code.newLocal(OBJ_ARR)
            @Suppress("UNCHECKED_CAST")
            val indexLocals = Array(targetParams.size) { code.newLocal(TypeId.INT) }
            @Suppress("UNCHECKED_CAST")
            val argumentLocals = Array(targetParams.size) { i ->
                val argumentType = parameterBoxInfos[i]?.wrapperTid ?: OBJ
                code.newLocal(argumentType as TypeId<Any>)
            }
            val rawResultLocal = code.newLocal(OBJ)
            val returnBoxInfo = boxInfo(targetReturn)
            @Suppress("UNCHECKED_CAST")
            val returnWrapperLocal = returnBoxInfo?.let { code.newLocal(it.wrapperTid as TypeId<Any>) }
            @Suppress("UNCHECKED_CAST")
            val primitiveResultLocal = returnBoxInfo?.let { code.newLocal(tid(targetReturn) as TypeId<Any>) }

            // 1. 读 hookId
            code.sget(hookIdFld, hookIdLocal)

            // 2. self（静态方法为 null）
            if (receiver == null) {
                code.loadConstant(selfLocal as com.android.dx.Local<Nothing?>, null)
            } else {
                code.cast(selfLocal, receiver)
            }

            // 3. new Object[paramCount]
            code.loadConstant(sizeLocal, targetParams.size)
            code.newArray(argsLocal, sizeLocal)

            // 4. 装箱并写入 args[i]
            for (i in targetParams.indices) {
                val paramLocal = parameterLocals[i]
                val idxLocal = indexLocals[i]
                val argumentLocal = argumentLocals[i]
                code.loadConstant(idxLocal, i)

                val bi = parameterBoxInfos[i]
                if (bi != null) {
                    @Suppress("UNCHECKED_CAST")
                    code.invokeStatic(bi.valueOfMid as MethodId<Any, Any>, argumentLocal, paramLocal)
                } else {
                    code.cast(argumentLocal, paramLocal)
                }
                code.aput(argsLocal, idxLocal, argumentLocal)
            }

            // 5. dispatch(hookId, self, args) -> Object
            code.invokeStatic(dispatchMid, rawResultLocal, hookIdLocal, selfLocal, argsLocal)

            // 6. 返回（按需拆箱）
            when {
                targetReturn == Void.TYPE -> code.returnVoid()
                targetReturn.isPrimitive -> {
                    val bi = requireNotNull(returnBoxInfo)
                    val wLocal = requireNotNull(returnWrapperLocal)
                    val primLocal = requireNotNull(primitiveResultLocal)
                    code.cast(wLocal, rawResultLocal)
                    @Suppress("UNCHECKED_CAST")
                    code.invokeVirtual(bi.unboxMid as MethodId<Any, Any>, primLocal, wLocal)
                    code.returnValue(primLocal)
                }
                else -> code.returnValue(rawResultLocal)
            }
        }

        declareBridgeOrBackup("bridge", isBackup = false)
        declareBridgeOrBackup("backup", isBackup = true)

        // ── 加载生成的 dex ───────────────────────────────────────────────────
        val dexBytes = dm.generate()
        val parentLoader = ZygiskHookBridge::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader()

        val genClass = InMemoryDexClassLoader(ByteBuffer.wrap(dexBytes), parentLoader)
            .loadClass(fqn) as Class<*>

        val bridgeMethod = genClass.getDeclaredMethod("bridge", *dexParamClasses)
        val backupMethod = genClass.getDeclaredMethod("backup", *dexParamClasses)
        bridgeMethod.isAccessible = true
        backupMethod.isAccessible = true
        val hookIdStaticFld = genClass.getDeclaredField("hookId").also { it.isAccessible = true }

        return BridgePair(
            bridgeMethod = bridgeMethod,
            backupMethod = backupMethod,
            setHookId = { id -> hookIdStaticFld.setLong(null, id) },
        )
    }

    // ── Invoker ──────────────────────────────────────────────────────────────

    internal class ZygiskInvoker(private val method: Member) : Invoker {

        override fun invokeOrigin(thisObject: Any?, vararg args: Any?): Any? =
            invokeOriginal(method, thisObject, arrayOf(*args))

        override fun invokeWithMaxPriority(
            maxPriority: Int,
            thisObject: Any?,
            vararg args: Any?
        ): Any? = invokeOrigin(thisObject, *args)
    }
}
