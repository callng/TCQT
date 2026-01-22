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
        generateFeaturesJson(resolver)
        return emptyList()
    }

    private fun processActions(resolver: Resolver) {
        val actions = resolver
            .getSymbolsWithAnnotation(RegisterAction::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter { classDecl ->
                val ann = classDecl.annotations
                    .firstOrNull { it.shortName.asString() == "RegisterAction" }

                val enabled = ann?.arguments
                    ?.firstOrNull { it.name?.asString() == "enabled" }
                    ?.value as? Boolean

                enabled ?: true
            }
            .toList()

        if (actions.isEmpty()) return

        val file = codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            "com.owo233.tcqt.generated",
            "GeneratedActionList"
        )

        file.bufferedWriter().use { writer ->
            writer.write(
                """
            package com.owo233.tcqt.generated

            import com.owo233.tcqt.actions.IAction

            internal object GeneratedActionList {

                val ACTIONS: Array<Class<out IAction>> = arrayOf(
            """.trimIndent()
            )
            writer.write("\n")

            // Action 类列表
            actions.forEach { classDecl ->
                val qName = classDecl.qualifiedName?.asString() ?: return@forEach
                writer.write("        $qName::class.java,\n")
            }

            writer.write(
                """
                )

                val ACTION_NAME_MAP: Map<String, String> = mapOf(
            """.trimIndent()
            )
            writer.write("\n")

            // name 映射
            actions.forEach { classDecl ->

                // 跳过 AlwaysRunAction
                if (classDecl.isAlwaysRunAction()) return@forEach

                val settingAnn = classDecl.annotations
                    .firstOrNull { it.shortName.asString() == "RegisterSetting" }
                    ?: return@forEach

                val key = settingAnn.arguments
                    .firstOrNull { it.name?.asString() == "key" }
                    ?.value as? String
                    ?: return@forEach

                val name = settingAnn.arguments
                    .firstOrNull { it.name?.asString() == "name" }
                    ?.value as? String
                    ?: key

                writer.write(
                    "        \"$key\" to \"$name\",\n"
                )
            }

            writer.write(
                """
                )
            }
            """.trimIndent()
            )
        }
    }

    private fun KSClassDeclaration.isAlwaysRunAction(): Boolean {
        return superTypes.any { superType ->
            val resolved = superType.resolve()
            val decl = resolved.declaration as? KSClassDeclaration
            when {
                decl == null -> false
                decl.qualifiedName?.asString() ==
                        "com.owo233.tcqt.actions.AlwaysRunAction" -> true
                else -> decl.isAlwaysRunAction() // 递归向上找
            }
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
            sb.appendLine("import com.owo233.tcqt.foundation.internal.setting.TCQTSetting")
            sb.appendLine("import com.owo233.tcqt.foundation.internal.setting.TCQTSetting.Setting")
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

            sb.appendLine("    fun getString(settingKey: String): String = TCQTSetting.getValue<String>(settingKey).orEmpty().trim()")
            sb.appendLine("    fun getInt(settingKey: String): Int = TCQTSetting.getValue<Int>(settingKey) ?: 0")
            sb.appendLine("    fun getBoolean(settingKey: String): Boolean = TCQTSetting.getValue<Boolean>(settingKey) ?: false")
            sb.appendLine()

            sb.appendLine("    fun setString(settingKey: String, value: String) = TCQTSetting.setValue(settingKey, value)")
            sb.appendLine("    fun setInt(settingKey: String, value: Int) = TCQTSetting.setValue(settingKey, value)")
            sb.appendLine("    fun setBoolean(settingKey: String, value: Boolean) = TCQTSetting.setValue(settingKey, value)")

            sb.appendLine("}")

            writer.write(sb.toString())
        }
    }

    private fun extractAllSettings(classDecl: KSClassDeclaration): Sequence<SettingInfo> {
        return classDecl.annotations
            .filter { it.shortName.asString() == "RegisterSetting" }
            .map { annotation ->
                val args = annotation.arguments.associateBy { it.name?.asString() }

                val key = (args["key"]?.value as? String)?.ifBlank { null }
                val name = args["name"]?.value as? String ?: ""
                val desc = args["desc"]?.value as? String ?: ""
                val hasTextAreas = args["hasTextAreas"]?.value as? Boolean ?: false
                val uiOrder = args["uiOrder"]?.value as? Int ?: 1000
                val textAreaPlaceholder = args["textAreaPlaceholder"]?.value as? String ?: ""
                val hidden = args["hidden"]?.value as? Boolean ?: false
                val options = args["options"]?.value as? String ?: ""
                val uiTab = args["uiTab"]?.value as? String ?: ""

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
                    SettingType.INT, SettingType.INT_MULTI -> defaultValue?.toIntOrNull()?.toString() ?: "0"
                    SettingType.STRING -> "\"" + (defaultValue ?: "")
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"") + "\""
                }

                SettingInfo(actualKey, name, type, formattedDefaultValue, desc, hasTextAreas, uiOrder, textAreaPlaceholder, hidden, options, uiTab)
            }
    }

    private fun generateSafeConstantName(key: String): String {
        return key
            .replace(".", "_")
            .replace("-", "_")
            .replace(" ", "_")
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .replace(Regex("_{2,}"), "_")
            .trim('_')
            .let { if (it.firstOrNull()?.isDigit() == true) "KEY_$it" else it }
            .uppercase()
            .takeIf { it.isNotBlank() && it != "_" } ?: "UNKNOWN_KEY"
    }

    private fun generateFeaturesJson(resolver: Resolver) {
        val enabledActionClasses = resolver
            .getSymbolsWithAnnotation(RegisterAction::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter { classDecl ->
                val ann = classDecl.annotations.find { it.shortName.asString() == "RegisterAction" }
                val enabled = ann?.arguments?.find { it.name?.asString() == "enabled" }?.value as? Boolean
                enabled ?: true
            }
            .map { it.qualifiedName?.asString() }
            .toSet()

        val annotatedClasses = resolver
            .getSymbolsWithAnnotation(RegisterSetting::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter { classDecl ->
                enabledActionClasses.contains(classDecl.qualifiedName?.asString())
            }

        if (!annotatedClasses.iterator().hasNext()) return

        // 收集所有设置信息，按主键分组
        val allSettings = annotatedClasses
            .flatMap { extractAllSettings(it) }
            .toList()

        // 按主键分组，找出主设置和子设置
        val settingGroups = allSettings.groupBy { setting ->
            if (setting.key.contains('.')) {
                setting.key.substringBefore('.')
            } else {
                setting.key
            }
        }

        // 同时生成Kotlin数据类
        val kotlinFile = codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            "com.owo233.tcqt.generated",
            "GeneratedFeaturesData"
        )

        kotlinFile.bufferedWriter().use { writer ->
            val kotlinFeatures = generateKotlinFeatures(settingGroups)

            writer.write(
                $$"""
package com.owo233.tcqt.generated

object GeneratedFeaturesData {

    data class TextAreaConfig(
        val key: String,
        val placeholder: String
    )

    data class OptionConfig(
        val key: String,
        val label: String,
        val value: Int,
        val isMulti: Boolean = false
    )

    data class FeatureConfig(
        val key: String,
        val label: String,
        val desc: String,
        val textareas: List<TextAreaConfig>? = null,
        val options: List<OptionConfig>? = null,
        val uiOrder: Int = 1000,
        val uiTab: String = "基础"
    )

    val FEATURES: List<FeatureConfig> = listOf(
$$kotlinFeatures
    )

    fun toJsonString(): String {
        return buildString {
            append("[")
            FEATURES.forEachIndexed { index, feature ->
                if (index > 0) append(",")
                append("\n        {")
                append("\n            \"key\": \"${feature.key}\",")
                append("\n            \"label\": \"${escapeJsonString(feature.label)}\",")
                append("\n            \"desc\": \"${escapeJsonString(feature.desc)}\",")
                feature.textareas?.let { textareas ->
                    append("\n            \"textareas\": [")
                    textareas.forEachIndexed { taIndex, ta ->
                        if (taIndex > 0) append(",")
                        append("\n                {")
                        append("\n                    \"key\": \"${ta.key}\",")
                        append("\n                    \"placeholder\": \"${escapeJsonString(ta.placeholder)}\"")
                        append("\n                }")
                    }
                    append("\n            ],")
                }
                feature.options?.let { options ->
                    append("\n            \"options\": [")
                    options.forEachIndexed { opIndex, op ->
                        if (opIndex > 0) append(",")
                        append("\n                {")
                        append("\n                    \"key\": \"${escapeJsonString(op.key)}\",")
                        append("\n                    \"label\": \"${escapeJsonString(op.label)}\",")
                        append("\n                    \"value\": ${op.value},")
                        append("\n                    \"isMulti\": ${op.isMulti}")
                        append("\n                }")
                    }
                    append("\n            ],")
                }
                append("\n            \"uiOrder\": ${feature.uiOrder},")
                append("\n            \"uiTab\": \"${escapeJsonString(feature.uiTab)}\"")
                append("\n        }")
            }
            append("\n    ]")
        }
    }

    private fun escapeJsonString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

""".trimIndent())
        }
    }

    private fun generateKotlinFeatures(settingGroups: Map<String, List<SettingInfo>>): String {
        return settingGroups.mapNotNull { (mainKey, settings) ->
            val mainSetting = settings.find { it.key == mainKey } ?: settings.first()

            // 如果主设置被隐藏，则跳过整个功能组
            if (mainSetting.hidden) return@mapNotNull null

            val subSettings = settings.filter { it.key != mainKey && it.key.startsWith("$mainKey.") }

            buildString {
                append("        FeatureConfig(")
                append("\n            key = \"${mainSetting.key}\",")
                append("\n            label = \"${escapeKotlinString(mainSetting.name)}\",")
                append("\n            desc = \"${escapeKotlinString(mainSetting.desc)}\",")

                // 处理文本框
                if (mainSetting.hasTextAreas || subSettings.any { it.type == SettingType.STRING }) {
                    append("\n            textareas = listOf(")

                    val textAreaSettings = if (mainSetting.hasTextAreas && mainSetting.type == SettingType.STRING) {
                        listOf(mainSetting) + subSettings.filter { it.type == SettingType.STRING && !it.hidden }
                    } else {
                        subSettings.filter { it.type == SettingType.STRING && !it.hidden }
                    }

                    textAreaSettings.forEachIndexed { index, textSetting ->
                        if (index > 0) append(",")
                        append("\n                TextAreaConfig(")
                        append("\n                    key = \"${textSetting.key}\",")
                        val placeholder = textSetting.textAreaPlaceholder.ifEmpty { "填写${textSetting.name}内容" }
                        append("\n                    placeholder = \"${escapeKotlinString(placeholder)}\"")
                        append("\n                )")
                    }
                    append("\n            ),")
                }

                // 处理选项（子配置中的INT或INT_MULTI类型且有options）
                val optionSetting = subSettings.find { (it.type == SettingType.INT || it.type == SettingType.INT_MULTI) && it.options.isNotBlank() && !it.hidden }
                if (optionSetting != null) {
                    val optionList = optionSetting.options.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (optionList.isNotEmpty()) {
                        append("\n            options = listOf(")
                        optionList.forEachIndexed { index, label ->
                            if (index > 0) append(",")
                            append("\n                OptionConfig(")
                            append("\n                    key = \"${optionSetting.key}\",")
                            append("\n                    label = \"${escapeKotlinString(label)}\",")
                            // 选项值从1开始，因为0保留给主开关的关闭状态
                            append("\n                    value = ${index + 1},")
                            // 添加类型标记，用于前端区分单选和多选
                            append("\n                    isMulti = ${optionSetting.type == SettingType.INT_MULTI}")
                            append("\n                )")
                        }
                        append("\n            ),")
                    }
                }

                val effectiveOrder = mainSetting.uiOrder
                append("\n            uiOrder = $effectiveOrder,")

                // 确定Tab名称
                val tabName = mainSetting.uiTab.ifBlank { "基础" }
                append("\n            uiTab = \"${escapeKotlinString(tabName)}\"")
                append("\n        )")
            }
        }.groupBy { keyStr ->
            // 按Tab分组
            val k = keyStr.substringAfter("key = \"").substringBefore("\"")
            val main = settingGroups[k]?.first()
            main?.uiTab?.ifBlank { "基础" } ?: "基础"
        }.flatMap { (_, features) ->
            // 每个Tab内部按uiOrder排序
            features.sortedBy { keyStr ->
                val k = keyStr.substringAfter("key = \"").substringBefore("\"")
                val main = settingGroups[k]?.first()
                main?.uiOrder ?: 1000
            }
        }.joinToString(",\n")
    }

    private fun escapeKotlinString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("$", "\\$")
    }

    private data class SettingInfo(
        val key: String,
        val name: String,
        val type: SettingType,
        val defaultValue: String = "",
        val desc: String = "",
        val hasTextAreas: Boolean = false,
        val uiOrder: Int = 1000,
        val textAreaPlaceholder: String = "",
        val hidden: Boolean = false,
        val options: String = "",
        val uiTab: String = ""
    )
}
