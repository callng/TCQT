/**
 * 代码来自：https://github.com/oneQAQone/QFun
 * 翻译：owo233
 */

package com.owo233.tcqt.hooks.func.theme

import com.owo233.tcqt.HookEnv
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

internal object FileUtil {

    fun getThemeRootDir(): String {
        return "${HookEnv.application.applicationInfo.dataDir}/app_theme_810/"
    }

    fun forceDelete(dir: File) {
        if (!dir.exists()) return
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { forceDelete(it) }
        }
        dir.delete()
    }

    fun downloadFile(url: String, dest: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000

        try {
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 16384)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun unzipFile(zip: File, dest: File) {
        ZipInputStream(BufferedInputStream(zip.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(dest, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().buffered().use { out ->
                        zis.copyTo(out, bufferSize = 16384)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        dest.listFiles()?.takeIf { it.size == 1 && it[0].isDirectory && it[0].name != "raw" }?.let { list ->
            val inner = list[0]
            inner.listFiles()?.forEach { item ->
                item.renameTo(File(dest, item.name))
            }
            inner.delete()
        }
    }

    fun extractInnerPackage(src: File, dest: File) {
        val isDouble = ZipInputStream(src.inputStream()).use { zis ->
            val first = zis.nextEntry
            first != null && !first.isDirectory && first.name.lowercase().endsWith(".zip")
        }

        if (isDouble) {
            ZipInputStream(BufferedInputStream(src.inputStream())).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".zip")) {
                        dest.outputStream().buffered().use { out ->
                            zis.copyTo(out, bufferSize = 16384)
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        } else {
            src.renameTo(dest)
        }
    }
}
