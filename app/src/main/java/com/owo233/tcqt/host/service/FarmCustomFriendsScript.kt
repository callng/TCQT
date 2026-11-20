package com.owo233.tcqt.host.service

/** Inserts the adapter inside the Cocos main factory, after System has been initialized. */
internal object FarmCustomFriendsScript {

    const val ASSET = "assets/tcqt/farm-custom-friends.js"
    const val MARKER = "TCQT_FARM_CUSTOM_FRIENDS_V1"
    const val MAX_SCRIPT_BYTES = 16 * 1024 * 1024

    private val mainName = Regex("assets/main/index(?:\\.[A-Za-z0-9_-]+)?\\.js")
    private val mainFactory = Regex(
        """define\(\s*["']assets/main/index(?:\.[A-Za-z0-9_-]+)?\.js["']\s*,\s*function\s*\(\s*require\s*,\s*module\s*,\s*exports\s*\)\s*\{\s*["']use strict["'];""",
    )

    // Triton's package decorator adds this prefix before ScriptRuntime executes the file.
    private fun packageName(name: String): String = name.removePrefix("/game/")

    fun isCandidate(name: String): Boolean = packageName(name).let { it == "game.js" || mainName.matches(it) }

    fun inject(name: String, source: String, adapter: String): String? {
        if (!isCandidate(name) || source.length > MAX_SCRIPT_BYTES || MARKER in source) return null
        val factories = mainFactory.findAll(source).take(2).toList()
        val position = when {
            factories.size == 1 -> factories.single().range.last + 1
            factories.isEmpty() && mainName.matches(packageName(name)) -> {
                // Some SDK packages keep main in its own file instead of the define bundle.
                val strict = Regex("""^\s*["']use strict["'];""").find(source) ?: return null
                if (!source.contains("System")) return null
                strict.range.last + 1
            }
            else -> return null
        }
        return source.substring(0, position) + "\n;" + adapter + "\n" + source.substring(position)
    }
}
