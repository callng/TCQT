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
        generateFeaturesData(resolver)
        generateCategoryTree(resolver)
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

            import com.owo233.tcqt.ext.IAction

            internal object GeneratedActionList {

                val ACTIONS: Array<Class<out IAction>> = arrayOf(
            """.trimIndent()
            )
            writer.write("\n")

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

            actions.forEach { classDecl ->
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
                        "com.owo233.tcqt.ext.AlwaysRunAction" -> true
                else -> decl.isAlwaysRunAction()
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

                val categoryPath = parseCategoryPath(uiTab)

                SettingInfo(
                    actualKey, name, type, formattedDefaultValue,
                    desc, hasTextAreas, uiOrder, textAreaPlaceholder,
                    hidden, options, uiTab, categoryPath
                )
            }
    }

    private fun parseCategoryPath(uiTab: String): List<String> {
        val trimmed = uiTab.trim()
        if (trimmed.isEmpty()) return listOf("基础")
        return trimmed.split("/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("基础") }
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

    private fun generateFeaturesData(resolver: Resolver) {
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

        val allSettings = annotatedClasses
            .flatMap { extractAllSettings(it) }
            .toList()

        val settingGroups = allSettings.groupBy { setting ->
            if (setting.key.contains('.')) {
                setting.key.substringBefore('.')
            } else {
                setting.key
            }
        }

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
        val uiTab: String = "基础",
        val categoryPath: List<String> = listOf("基础")
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
                append("\n            \"uiTab\": \"${escapeJsonString(feature.uiTab)}\",")
                append("\n            \"categoryPath\": [")
                feature.categoryPath.forEachIndexed { ci, segment ->
                    if (ci > 0) append(",")
                    append("\"${escapeJsonString(segment)}\"")
                }
                append("]")
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

            if (mainSetting.hidden) return@mapNotNull null

            val subSettings = settings.filter { it.key != mainKey && it.key.startsWith("$mainKey.") }

            buildString {
                append("        FeatureConfig(")
                append("\n            key = \"${mainSetting.key}\",")
                append("\n            label = \"${escapeKotlinString(mainSetting.name)}\",")
                append("\n            desc = \"${escapeKotlinString(mainSetting.desc)}\",")

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

                val optionSetting = subSettings.find {
                    (it.type == SettingType.INT || it.type == SettingType.INT_MULTI) &&
                    it.options.isNotBlank() && !it.hidden
                }
                if (optionSetting != null) {
                    val optionList = optionSetting.options.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (optionList.isNotEmpty()) {
                        append("\n            options = listOf(")
                        optionList.forEachIndexed { index, label ->
                            if (index > 0) append(",")
                            append("\n                OptionConfig(")
                            append("\n                    key = \"${optionSetting.key}\",")
                            append("\n                    label = \"${escapeKotlinString(label)}\",")
                            append("\n                    value = ${index + 1},")
                            append("\n                    isMulti = ${optionSetting.type == SettingType.INT_MULTI}")
                            append("\n                )")
                        }
                        append("\n            ),")
                    }
                }

                val effectiveOrder = mainSetting.uiOrder
                append("\n            uiOrder = $effectiveOrder,")

                val tabName = mainSetting.uiTab.ifBlank { "基础" }
                append("\n            uiTab = \"${escapeKotlinString(tabName)}\",")

                val pathStr = mainSetting.categoryPath.joinToString("\", \"") { escapeKotlinString(it) }
                append("\n            categoryPath = listOf(\"$pathStr\")")
                append("\n        )")
            }
        }.joinToString(",\n")
    }

    // ───── Category Tree Generation ─────

    private class TreeNode {
        var pathSegment: String = ""
        var fullPath: String = ""
        var depth: Int = 0
        var label: String = ""
        var uiOrder: Int = 1000
        val children: MutableList<TreeNode> = mutableListOf()
        val featureKeys: MutableList<String> = mutableListOf()
    }

    private fun generateCategoryTree(resolver: Resolver) {
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

        val allSettings = annotatedClasses
            .flatMap { extractAllSettings(it) }
            .toList()

        val settingGroups = allSettings.groupBy { setting ->
            if (setting.key.contains('.')) {
                setting.key.substringBefore('.')
            } else {
                setting.key
            }
        }

        data class FeatureEntry(
            val key: String,
            val label: String,
            val desc: String,
            val uiOrder: Int,
            val categoryPath: List<String>
        )

        val features = settingGroups.mapNotNull { (mainKey, settings) ->
            val main = settings.find { it.key == mainKey } ?: settings.first()
            if (main.hidden) return@mapNotNull null
            FeatureEntry(
                key = mainKey,
                label = main.name,
                desc = main.desc,
                uiOrder = main.uiOrder,
                categoryPath = main.categoryPath
            )
        }

        val root = TreeNode()

        for (feat in features) {
            var current = root
            for (i in feat.categoryPath.indices) {
                val segment = feat.categoryPath[i]
                val isLast = i == feat.categoryPath.lastIndex
                val existing = current.children.find { it.pathSegment == segment }
                val node = existing
                    ?: TreeNode().apply {
                        pathSegment = segment
                        fullPath = if (current == root) segment else "${current.fullPath}/$segment"
                        depth = i
                        label = segment
                    }.also { current.children.add(it) }
                if (isLast) {
                    node.featureKeys.add(feat.key)
                    node.uiOrder = feat.uiOrder
                }
                current = node
            }
        }

        fun sortTree(node: TreeNode) {
            node.children.sortWith(compareBy<TreeNode> { it.depth }.thenBy { it.uiOrder })
            node.children.forEach { sortTree(it) }
        }
        sortTree(root)

        val file = codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            "com.owo233.tcqt.generated",
            "GeneratedCategoryTree"
        )

        file.bufferedWriter().use { writer ->
            val sb = StringBuilder()
            sb.appendLine("package com.owo233.tcqt.generated")
            sb.appendLine()
            sb.appendLine("/**")
            sb.appendLine(" * Auto-generated category tree from @RegisterSetting(uiTab=...) paths.")
            sb.appendLine(" * Paths like \"高级/过检测\" become nested CategoryNode entries.")
            sb.appendLine(" */")
            sb.appendLine("object GeneratedCategoryTree {")
            sb.appendLine()
            sb.appendLine("    data class CategoryNode(")
            sb.appendLine("        val name: String,")
            sb.appendLine("        val fullPath: String,")
            sb.appendLine("        val depth: Int,")
            sb.appendLine("        val label: String,")
            sb.appendLine("        val uiOrder: Int,")
            sb.appendLine("        val featureKeys: List<String>,")
            sb.appendLine("        val children: List<CategoryNode>")
            sb.appendLine("    )")
            sb.appendLine()

            sb.appendLine("    val ROOTS: List<CategoryNode> = listOf(")
            root.children.forEachIndexed { i, child ->
                if (i > 0) sb.append(",\n")
                appendTreeNode(sb, child, indent = "        ")
            }
            sb.appendLine()
            sb.appendLine("    )")
            sb.appendLine()

            sb.appendLine("    val FEATURE_CATEGORY_MAP: Map<String, List<String>> = mapOf(")
            features.sortedBy { it.key }.forEach { feat ->
                val pathStr = feat.categoryPath.joinToString("\", \"") { escapeKotlinStringForTree(it) }
                sb.appendLine("        \"${feat.key}\" to listOf(\"$pathStr\"),")
            }
            sb.appendLine("    )")
            sb.appendLine("}")

            writer.write(sb.toString())
        }
    }

    private fun appendTreeNode(sb: StringBuilder, node: TreeNode, indent: String) {
        val featureKeys = node.featureKeys.map { "\"$it\"" }

        sb.append("${indent}CategoryNode(")
        sb.append("\n${indent}    name = \"${escapeKotlinStringForTree(node.pathSegment)}\",")
        sb.append("\n${indent}    fullPath = \"${escapeKotlinStringForTree(node.fullPath)}\",")
        sb.append("\n${indent}    depth = ${node.depth},")
        sb.append("\n${indent}    label = \"${escapeKotlinStringForTree(node.label)}\",")
        sb.append("\n${indent}    uiOrder = ${node.uiOrder},")
        sb.append("\n${indent}    featureKeys = listOf(${featureKeys.joinToString(", ")}),")
        if (node.children.isEmpty()) {
            sb.append("\n${indent}    children = emptyList()")
        } else {
            sb.append("\n${indent}    children = listOf(")
            node.children.forEachIndexed { i, child ->
                if (i > 0) sb.append(",\n")
                appendTreeNode(sb, child, indent = "$indent        ")
            }
            sb.append("\n${indent}    )")
        }
        sb.append("\n$indent)")
    }

    private fun escapeKotlinStringForTree(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("$", "\\$")
    }

    // ───── Helpers ─────

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
        val uiTab: String = "",
        val categoryPath: List<String> = listOf("基础")
    )
}
