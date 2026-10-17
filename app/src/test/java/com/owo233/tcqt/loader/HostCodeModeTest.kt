package com.owo233.tcqt.loader

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
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
}
