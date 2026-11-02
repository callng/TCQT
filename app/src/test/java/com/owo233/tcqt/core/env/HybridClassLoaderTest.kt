package com.owo233.tcqt.core.env

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.Test

class HybridClassLoaderTest {
    private class ModuleType

    @Test
    fun androidApiStubInDexDoesNotShadowThePlatformClass() {
        val platformType = android.view.WindowInsetsAnimation.Callback::class.java
        withModuleNames(setOf(platformType.name)) {
            val child = object : ClassLoader(HybridClassLoader) {
                override fun findClass(name: String): Class<*> =
                    throw AssertionError("Platform class was routed to the module DEX: $name")
            }
            assertSame(platformType, child.loadClass(platformType.name))
        }
    }

    private fun withModuleNames(names: Set<String>, block: () -> Unit) {
        val field = HybridClassLoader::class.java.getDeclaredField("moduleClasses")
            .apply { isAccessible = true }
        val previous = field.get(null)
        try {
            field.set(null, names)
            block()
        } finally {
            field.set(null, previous)
        }
    }

    @Test
    fun moduleTypeReturnsToChildEvenWhenSystemLoaderHasTheSameName() =
        withModuleNames(setOf(ModuleType::class.java.name, "java.lang.String")) {
            var childLookups = 0
            val child = object : ClassLoader(HybridClassLoader) {
                override fun findClass(name: String): Class<*> {
                    childLookups++
                    return ModuleType::class.java
                }
            }
            assertSame(ModuleType::class.java, child.loadClass(ModuleType::class.java.name))
            assertEquals(1, childLookups)
            assertSame(String::class.java, child.loadClass("java.lang.String"))
            assertEquals(1, childLookups)
        }

    @Test
    fun missingModuleTypePreservesTheChildsRealFailure() =
        withModuleNames(setOf("missing.module.Type")) {
            val failure = ClassNotFoundException("child DEX does not contain the requested type")
            val child = object : ClassLoader(HybridClassLoader) {
                override fun findClass(name: String): Class<*> = throw failure
            }
            assertSame(failure, assertFailsWith<ClassNotFoundException> {
                child.loadClass("missing.module.Type")
            })
        }

    @Test
    fun unavailableDexIndexRetainsNormalParentLookup() = withModuleNames(emptySet()) {
        var childLookups = 0
        val child = object : ClassLoader(HybridClassLoader) {
            override fun findClass(name: String): Class<*> {
                childLookups++
                throw ClassNotFoundException(name)
            }
        }
        assertSame(ModuleType::class.java, child.loadClass(ModuleType::class.java.name))
        assertEquals(0, childLookups)
    }
}
