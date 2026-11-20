package com.owo233.tcqt.loader.validation

import android.annotation.SuppressLint
import com.owo233.tcqt.features.appearance.ChatGroups
import dalvik.system.PathClassLoader
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod

/** Read-only APK compatibility checks through app_process, without starting QQ. */
object ChatGroupsCompatibilityMain {
    // ADB fixture: load the matching library supplied alongside the test APK.
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Expected libdexkit.so and QQ APK paths" }
        System.load(args[0])
        val loader = PathClassLoader(args[1], javaClass.classLoader)
        val report = JSONObject().put("apk", args[1])
        DexKitBridge.create(args[1]).use { bridge ->
            val descriptors = ChatGroups.getQueryMap().mapValues { (key, query) ->
                val matches = when (query) {
                    is FindClass -> bridge.findClass(query).map { it.descriptor }
                    is FindMethod -> bridge.findMethod(query).map { it.descriptor }
                    else -> error("Unsupported query: $key")
                }
                check(matches.size == 1) { "$key: $matches" }
                matches.single().also { report.put(key, it) }
            }
            val register = DexMethod(descriptors.getValue("stickySearchRegisterHeader")).getMethodInstance(loader)
            val merge = DexMethod(descriptors.getValue("chatGroupsMergeList")).getMethodInstance(loader)
            check(register.declaringClass == merge.declaringClass)
            merge.declaringClass.getDeclaredMethod("submitList", List::class.java)
            DexMethod(descriptors.getValue("chatGroupsUpdateHeader")).getMethodInstance(loader)
            val recent = DexClass(descriptors.getValue("chatGroupsRecentItem")).getInstance(loader)
            val contact = recent.declaredFields.single {
                it.type.name == "com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo"
            }.type
            val types = mapOf("chatType" to "int", "contactId" to "long", "peerUin" to "long",
                "unreadCnt" to "long", "unreadFlag" to "long", "remark" to "java.lang.String",
                "peerName" to "java.lang.String", "peerUid" to "java.lang.String")
            types.forEach { (name, type) -> check(contact.getField(name).type.name == type) }
            val guild = contact.getField("guildContactInfo").type
            check(guild.getField("guildName").type == String::class.java)
            val unread = guild.getField("unreadCntInfo").type.getField("showUnreadCnt").type
            check(unread.getField("cnt").type == Int::class.javaPrimitiveType)
            check(unread.getField("type").type == Int::class.javaPrimitiveType)

            // Independently compare the optional named binding with DEX fold metadata.
            val foldBinding = try {
                loader.loadClass("com.tencent.qqnt.chats.core.adapter.holder.ChatsFoldItemHolder\$bind\$1")
            } catch (_: ClassNotFoundException) {
                null
            }
            val fold = foldBinding?.getDeclaredField("\$foldItem")?.type
            val folds = bridge.findClass {
                matcher { superClass("java.lang.Object"); usingEqStrings("foldedItems") }
            }
            check(folds.map { it.name } == listOfNotNull(fold?.name))
            fold?.declaredFields?.single { it.type == List::class.java }
            report.put("foldClass", fold?.name ?: JSONObject.NULL)
        }
        println(report.put("passed", true))
    }
}
