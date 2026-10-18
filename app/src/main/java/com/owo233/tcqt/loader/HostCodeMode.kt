package com.owo233.tcqt.loader

import com.owo233.tcqt.core.dexkit.DexKitHostCode
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** 判断当前 loader 链是否实际包含补丁代码，不依赖补丁文件现在是否存在。 */
internal object HostCodeMode {

    private val patchDexPath = Regex("[/\\\\]tinker(?:[/\\\\]|[-_])", RegexOption.IGNORE_CASE)
    private val dexEntryPath = Regex("(?:zip|dex) file \"([^\"]+)\"")

    fun isHotPatched(classLoader: ClassLoader): Boolean {
        return generateSequence(classLoader) { it.parent }.any(::isPatchLoader)
    }

    fun identify(classLoader: ClassLoader): DexKitHostCode {
        val patchLoaders = generateSequence(classLoader) { it.parent }.filter(::isPatchLoader).toList()
        if (patchLoaders.isEmpty()) return DexKitHostCode(isHotPatched = false)

        // Use only actual loader entries, never a directory scan or patch.info: a downloaded
        // replacement (or deleted patch) need not be the code this process is running.
        val fingerprint = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            fun record(value: String) {
                digest.update(value.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
            }
            record("tcqt-patch-dex-v1")
            patchLoaders.forEach { loader ->
                val paths = dexEntryPath.findAll(loader.toString()).map { it.groupValues[1] }.toList()
                check(paths.isNotEmpty()) { "Patch loader has no readable DEX paths" }
                paths.forEach { path ->
                    val file = File(path)
                    check(file.isFile) { "Loaded patch DEX is missing: $path" }
                    record(file.canonicalPath)
                    if (file.extension.equals("dex", ignoreCase = true)) {
                        // DEX header includes its checksum, SHA-1 signature and file size.
                        val header = DataInputStream(file.inputStream()).use { input ->
                            ByteArray(32).also(input::readFully)
                        }
                        check(header.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10)))
                        digest.update(header)
                    } else {
                        // Central-directory DEX CRCs change even for same-size replacements.
                        // Reading them avoids hashing the entire patch on the startup thread.
                        ZipFile(file).use { zip ->
                            val entries = zip.entries().asSequence().filter {
                                !it.isDirectory && it.name.endsWith(".dex")
                            }.sortedBy { it.name }.toList()
                            check(entries.isNotEmpty()) { "Patch archive contains no DEX" }
                            entries.forEach { entry ->
                                check(entry.crc >= 0 && entry.size >= 0)
                                record(entry.name)
                                record(entry.size.toString())
                                record(entry.crc.toString())
                            }
                        }
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
        // Unknown/deleted paths retain the conservative rescan behavior.
        return DexKitHostCode(isHotPatched = true, fingerprint = fingerprint)
    }

    private fun isPatchLoader(loader: ClassLoader): Boolean =
        generateSequence<Class<*>>(loader.javaClass) { it.superclass }.any {
            it.simpleName == "TinkerClassLoader"
        } || patchDexPath.containsMatchIn(loader.toString())
}
