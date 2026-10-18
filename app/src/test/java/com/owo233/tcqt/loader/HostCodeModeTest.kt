package com.owo233.tcqt.loader

import java.nio.file.Files
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostCodeModeTest {

    private class DescribedLoader(
        private val description: String,
        parent: ClassLoader? = null,
    ) : ClassLoader(parent) {
        override fun toString(): String = description
    }

    private open class TinkerClassLoader : ClassLoader(null)

    @Test
    fun ordinaryHostAndDelegateLastLoadersUseNormalCode() {
        assertFalse(HostCodeMode.isHotPatched(DescribedLoader(
            "dalvik.system.PathClassLoader[DexPathList[[zip file /data/app/com.tencent.mobileqq/base.apk]]]"
        )))
        assertFalse(HostCodeMode.isHotPatched(DescribedLoader(
            "dalvik.system.DelegateLastClassLoader[DexPathList[[zip file /data/app/com.tencent.tim/base.apk]]]"
        )))
        assertFalse(HostCodeMode.isHotPatched(DescribedLoader(
            "com.tencent.tinker.loader.TinkerLoader"
        )))
    }

    @Test
    fun tinkerLoaderAndItsSubclassAndParentAreRecognized() {
        assertTrue(HostCodeMode.isHotPatched(TinkerClassLoader()))
        assertTrue(HostCodeMode.isHotPatched(object : TinkerClassLoader() {}))
        assertTrue(HostCodeMode.isHotPatched(DescribedLoader("host", TinkerClassLoader())))
    }

    @Test
    fun patchedDexPathIsRecognizedWithoutSpecialLoaderName() {
        assertTrue(HostCodeMode.isHotPatched(DescribedLoader(
            "dalvik.system.DelegateLastClassLoader[DexPathList[[zip file /data/user/0/com.tencent.mobileqq/tinker/patch-abcd/dex/tinker_classN.apk]]]"
        )))
    }

    @Test
    fun reportedQqDelegateLastLoaderSelectsPatchCacheWhileBaseApkSelectsNormalCache() {
        val patchPath = "/data/user/0/com.tencent.mobileqq/tinker/patch-48ac0b65/dex/tinker_classN.apk"
        val installPath = "/data/app/~~jXHD2j_khFGhpgldNJPvdg==/com.tencent.mobileqq-0SPVlsU6MlbZLu1qbwu2Pg=="
        val description = """dalvik.system.DelegateLastClassLoader[DexPathList[[zip file "$patchPath"],nativeLibraryDirectories=[$installPath/lib/arm64, $installPath/base.apk!/lib/arm64-v8a, /system/lib64, /system_ext/lib64]]]"""

        assertTrue(HostCodeMode.isHotPatched(DescribedLoader(description)))
        assertFalse(HostCodeMode.isHotPatched(DescribedLoader(
            description.replace(patchPath, "$installPath/base.apk")
        )))
    }

    @Test
    fun deletingPatchDoesNotChangeRunningLoaderButNextNormalStartIsUnpatched() {
        val directory = Files.createTempDirectory("tcqt-code-mode")
        val patch = directory.resolve("tinker").resolve("patch-abcd").resolve("classes.dex")
        try {
            Files.createDirectories(patch.parent)
            Files.createFile(patch)
            val runningLoader = DescribedLoader("DexPathList[$patch]")
            assertTrue(HostCodeMode.isHotPatched(runningLoader))
            Files.delete(patch)
            assertTrue(HostCodeMode.isHotPatched(runningLoader))
            assertFalse(HostCodeMode.isHotPatched(DescribedLoader(
                "DexPathList[/data/app/com.tencent.mobileqq/base.apk]"
            )))
        } finally {
            Files.deleteIfExists(patch)
            Files.deleteIfExists(patch.parent)
            Files.deleteIfExists(patch.parent.parent)
            Files.deleteIfExists(directory)
        }
    }

    private fun withPatch(block: (File) -> Unit) {
        val root = Files.createTempDirectory("tcqt-patch-identity").toFile()
        try {
            val patch = root.resolve("tinker/patch-abcd/dex/tinker_classN.apk")
            requireNotNull(patch.parentFile).mkdirs()
            writePatch(patch, "first")
            block(patch)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writePatch(file: File, dex: String, resource: String = "image") {
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, value) in mapOf("classes.dex" to dex, "assets/icon" to resource)) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun patchLoader(file: File, nativePath: String = "/data/app/lib/arm64") = DescribedLoader(
        """dalvik.system.DelegateLastClassLoader[DexPathList[[zip file "${file.absolutePath}"],nativeLibraryDirectories=[$nativePath]]]"""
    )

    @Test
    fun repeatedStartsWithSamePatchReuseCacheDespiteNewLoaderAndNativePath() = withPatch { patch ->
        val first = HostCodeMode.identify(patchLoader(patch))
        val nextStart = HostCodeMode.identify(patchLoader(patch, "/data/app/new-install/lib/arm64"))
        assertNotNull(first.fingerprint)
        assertTrue(nextStart.isHotPatched)
        assertTrue(nextStart.matches(first.fingerprint))
        assertFalse(nextStart.matches(null)) // Legacy patch caches must be refreshed once.

        // Repacking resources and ZIP timestamps must not invalidate unchanged DEX code.
        writePatch(patch, "first", resource = "new-image")
        assertEquals(first, HostCodeMode.identify(patchLoader(patch)))
    }

    @Test
    fun samePathAndSameSizeDexReplacementInvalidatesCache() = withPatch { patch ->
        val first = HostCodeMode.identify(patchLoader(patch))
        val oldSize = patch.length()
        writePatch(patch, "other")
        assertEquals(oldSize, patch.length())
        val replaced = HostCodeMode.identify(patchLoader(patch))
        assertNotNull(replaced.fingerprint)
        assertFalse(replaced.matches(first.fingerprint))
    }

    @Test
    fun deletedOrUnreadablePatchNeverReusesPreviousCache() = withPatch { patch ->
        val loader = patchLoader(patch)
        val first = HostCodeMode.identify(loader)
        assertTrue(patch.delete())
        val deleted = HostCodeMode.identify(loader)
        assertTrue(deleted.isHotPatched)
        assertNull(deleted.fingerprint)
        assertFalse(deleted.matches(first.fingerprint))
        assertFalse(deleted.matches(""))

        patch.writeText("not a DEX archive")
        assertNull(HostCodeMode.identify(loader).fingerprint)
        assertFalse(HostCodeMode.identify(TinkerClassLoader()).matches(first.fingerprint))
    }

    @Test
    fun rawDexHeaderAndEveryLoadedDexEntryParticipateInIdentity() = withPatch { patch ->
        val rawDex = patch.resolveSibling("classes2.dex")
        val header = ByteArray(32).also {
            "dex\n035\u0000".toByteArray().copyInto(it)
            it[8] = 1
        }
        rawDex.writeBytes(header)
        val loader = DescribedLoader(
            """DexPathList[[zip file "${patch.absolutePath}", dex file "${rawDex.absolutePath}"]]"""
        )
        val first = HostCodeMode.identify(loader)
        assertNotNull(first.fingerprint)
        header[8] = 2
        rawDex.writeBytes(header)
        assertFalse(HostCodeMode.identify(loader).matches(first.fingerprint))
    }

    @Test
    fun normalStartupContinuesToAcceptLegacyNormalCache() {
        val normal = HostCodeMode.identify(DescribedLoader("DexPathList[[zip file /data/app/base.apk]]"))
        assertFalse(normal.isHotPatched)
        assertTrue(normal.matches(null))
    }
}
