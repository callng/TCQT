package com.owo233.tcqt.lifecycle

import android.app.Activity
import java.util.concurrent.ConcurrentHashMap

object DynamicActivityRegistry {

    private val registry = ConcurrentHashMap<String, Class<out Activity>>()

    fun register(clazz: Class<*>) {
        require(Activity::class.java.isAssignableFrom(clazz)) {
            "The registered class ${clazz.name} must be a subclass of Activity"
        }

        @Suppress("UNCHECKED_CAST")
        registry[clazz.name] = clazz as Class<out Activity>
    }

    fun unregister(className: String) {
        registry.remove(className)
    }

    fun contains(className: String): Boolean {
        return registry.containsKey(className)
    }

    fun getActivityClass(className: String): Class<out Activity>? {
        return registry[className]
    }
}
