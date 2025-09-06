package com.owo233.tcqt.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType

class ActionRegistrarProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        processActions(resolver)
        processSettings(resolver)
        return emptyList()
    }

    private fun processActions(resolver: Resolver) {
        val annotated = resolver.getSymbolsWithAnnotation(RegisterAction::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        if (!annotated.iterator().hasNext()) return

        val file = codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            "com.owo233.tcqt.generated",
            "GeneratedActionList"
        )

        file.bufferedWriter().use { writer ->
            writer.write("package com.owo233.tcqt.generated\n\n")
            writer.write("import com.owo233.tcqt.ext.IAction\n\n")
            writer.write("object GeneratedActionList {\n")
            writer.write("    val ACTIONS: Array<Class<out IAction>> = arrayOf(\n")

            annotated.forEach { classDecl ->
                val qName = classDecl.qualifiedName?.asString()
                if (qName != null) {
                    writer.write("        $qName::class.java,\n")
                }
            }

            writer.write("    )\n")
            writer.write("}\n")
        }
    }

    private fun processSettings(resolver: Resolver) {
        val annotatedClasses = resolver
            .getSymbolsWithAnnotation(RegisterSetting::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        if (!annotatedClasses.iterator().hasNext()) return

        val allSettings = annotatedClasses
            .flatMap { extractAllSettings(it) }
            .toList()

        // 检查重复 key
        allSettings.groupBy { it.key }
            .filterValues { it.size > 1 }
            .forEach { (dupKey, list) ->
                logger.error("Duplicate setting key: \"$dupKey\" found in ${list.map { it.name }}")
            }

        val file = codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            "com.owo233.tcqt.generated",
            "GeneratedSettingList"
        )

        file.bufferedWriter().use { writer ->
            val sb = StringBuilder()
            sb.appendLine("package com.owo233.tcqt.generated")
            sb.appendLine()
            sb.appendLine("import com.owo233.tcqt.internals.setting.TCQTSetting")
            sb.appendLine("import com.owo233.tcqt.internals.setting.TCQTSetting.Setting")
            sb.appendLine("import com.owo233.tcqt.utils.logE")
            sb.appendLine()
            sb.appendLine("internal object GeneratedSettingList {")
            sb.appendLine()

            allSettings.forEach { setting ->
                val constantName = generateSafeConstantName(setting.key)
                sb.appendLine("    const val $constantName: String = \"${setting.key}\"")
            }

            sb.appendLine()
            sb.appendLine("    val SETTING_MAP = hashMapOf<String, TCQTSetting.Setting<out Any>>(")
            allSettings.forEach { setting ->
                val constantName = generateSafeConstantName(setting.key)
                val settingType = "TCQTSetting.SettingType.${setting.type.name}"
                val defaultVal = setting.defaultValue
                sb.appendLine("        $constantName to TCQTSetting.Setting($constantName, $settingType, $defaultVal),")
            }
            sb.appendLine("    )")
            sb.appendLine()

            sb.appendLine("    @Suppress(\"UNCHECKED_CAST\")")
            sb.appendLine("    private inline fun <reified T : Any> getSettingValue(settingKey: String): T? {")
            sb.appendLine("        return try {")
            sb.appendLine("            val setting: Setting<T> = TCQTSetting.getSetting(settingKey)")
            sb.appendLine("            setting.getValue(null, null)")
            sb.appendLine("        } catch (e: Exception) {")
            sb.appendLine("            logE(msg = \"getSettingValue error for key: \$settingKey\", cause = e)")
            sb.appendLine("            null")
            sb.appendLine("        }")
            sb.appendLine("    }")
            sb.appendLine()

            sb.appendLine("    fun getString(settingKey: String): String = getSettingValue<String>(settingKey).orEmpty().trim()")
            sb.appendLine("    fun getInt(settingKey: String): Int = getSettingValue<Int>(settingKey) ?: 0")
            sb.appendLine("    fun getBoolean(settingKey: String): Boolean = getSettingValue<Boolean>(settingKey) ?: false")

            sb.appendLine("}")

            writer.write(sb.toString())
        }
    }

    private fun extractAllSettings(classDecl: KSClassDeclaration): Sequence<SettingInfo> {
        return classDecl.annotations
            .filter { it.shortName.asString() == "RegisterSetting" }
            .asSequence()
            .map { annotation ->
                val args = annotation.arguments.associateBy { it.name?.asString() }

                val key = (args["key"]?.value as? String)?.ifBlank { null }
                val name = args["name"]?.value as? String ?: ""

                val type = (args["type"]?.value?.toString() ?: "BOOLEAN").let {
                    try {
                        SettingType.valueOf(it.substringAfterLast("."))
                    } catch (_: IllegalArgumentException) {
                        SettingType.BOOLEAN
                    }
                }

                val defaultValue = (args["defaultValue"]?.value as? String)?.ifBlank { null }

                val actualKey = key ?: classDecl.simpleName.asString()
                    .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2].lowercase()}" }
                    .lowercase()

                val formattedDefaultValue = when (type) {
                    SettingType.BOOLEAN -> defaultValue?.toBooleanStrictOrNull()?.toString() ?: "false"
                    SettingType.INT -> defaultValue?.toIntOrNull()?.toString() ?: "0"
                    SettingType.STRING -> "\"" + (defaultValue ?: "")
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"") + "\""
                }

                SettingInfo(actualKey, name, type, formattedDefaultValue)
            }
    }

    private fun generateSafeConstantName(key: String): String {
        return key
            .replace(".", "_")  // device.model -> device_model
            .replace("-", "_")  // app-config -> app_config
            .replace(" ", "_")  // app config -> app_config
            .replace("+", "_PLUS_")  // version+ -> version_PLUS_
            .replace("@", "_AT_")   // user@domain -> user_AT_domain
            .replace("#", "_HASH_") // config#1 -> config_HASH_1
            .replace("$", "_DOLLAR_") // price$ -> price_DOLLAR_
            .replace("%", "_PERCENT_") // rate% -> rate_PERCENT_
            .replace("&", "_AND_")   // A&B -> A_AND_B
            .replace("*", "_STAR_")  // level* -> level_STAR_
            .replace("(", "_LPAREN_") // func( -> func_LPAREN_
            .replace(")", "_RPAREN_") // )end -> _RPAREN_end
            .replace("[", "_LBRACKET_") // arr[ -> arr_LBRACKET_
            .replace("]", "_RBRACKET_") // ]end -> _RBRACKET_end
            .replace("{", "_LBRACE_") // obj{ -> obj_LBRACE_
            .replace("}", "_RBRACE_") // }end -> _RBRACE_end
            .replace("=", "_EQUALS_") // key=value -> key_EQUALS_value
            .replace("?", "_QUESTION_") // opt? -> opt_QUESTION_
            .replace("!", "_EXCLAMATION_") // alert! -> alert_EXCLAMATION_
            .replace("<", "_LT_")    // less< -> less_LT_
            .replace(">", "_GT_")    // greater> -> greater_GT_
            .replace("/", "_SLASH_") // path/to -> path_SLASH_to
            .replace("\\", "_BACKSLASH_") // path\to -> path_BACKSLASH_to
            .replace(":", "_COLON_") // time:now -> time_COLON_now
            .replace(";", "_SEMICOLON_") // cmd; -> cmd_SEMICOLON_
            .replace(",", "_COMMA_") // a,b -> a_COMMA_b
            .replace("'", "_QUOTE_") // it's -> it_QUOTE_s
            .replace("\"", "_DOUBLEQUOTE_") // "text" -> _DOUBLEQUOTE_text_DOUBLEQUOTE_
            .replace("`", "_BACKTICK_") // `code` -> _BACKTICK_code_BACKTICK_
            .replace("~", "_TILDE_") // ~temp -> _TILDE_temp
            .replace("^", "_CARET_") // ^start -> _CARET_start
            .replace("|", "_PIPE_")  // a|b -> a_PIPE_b
            // 移除其他非字母数字下划线的字符
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            // 处理连续的下划线
            .replace(Regex("_{2,}"), "_")
            // 处理开头和结尾的下划线
            .trim('_')
            // 如果以数字开头，加上前缀
            .let { if (it.firstOrNull()?.isDigit() == true) "KEY_$it" else it }
            // 转为大写
            .uppercase()
            // 确保不为空或全为下划线
            .takeIf { it.isNotBlank() && it != "_" } ?: "UNKNOWN_KEY"
    }

    private data class SettingInfo(
        val key: String,
        val name: String,
        val type: SettingType,
        val defaultValue: String
    )
}
