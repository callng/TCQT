package com.owo233.tcqt.processor

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.owo233.tcqt.annotations.RegisterAction

class ActionRegistrarProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val table = RegistrationTable()
    private val inputs = linkedMapOf<String, KSFile>()
    private var pending = emptyList<String>()
    private var failed = false
    private var emitted = false
    private var ownsSpec = false
    private val annotationName = RegisterAction::class.qualifiedName!!

    private fun error(message: String, symbol: KSNode) {
        failed = true
        logger.error(message, symbol)
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val spec = resolver.getClassDeclarationByName(resolver.getKSNameFromString("com.owo233.tcqt.core.action.ActionSpec"))
        ownsSpec = ownsSpec || spec?.containingFile != null
        for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
            val declaration = symbol as? KSClassDeclaration
            if (declaration == null) { error("@RegisterAction requires a class or object", symbol); continue }
            declaration.containingFile?.let { inputs[it.filePath] = it }
            val annotations = declaration.annotations.filter {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
            }.toList()
            if (annotations.size != 1) { error("Duplicate @RegisterAction annotation", declaration); continue }
            val args = annotations.single().arguments.associate { it.name?.asString() to it.value }
            if (args["enabled"] == false) continue
            if (!declaration.validate(enableNewFeatures = true) || spec == null) { deferred += declaration; continue }
            val name = declaration.qualifiedName?.asString()
            if (name == null) { error("Registered action must have a qualified name", declaration); continue }
            val inaccessible = generateSequence<KSDeclaration>(declaration) { it.parentDeclaration }
                .any { Modifier.PRIVATE in it.modifiers || Modifier.PROTECTED in it.modifiers }
            val constraint = constructionError(
                declaration.classKind.name, inaccessible, Modifier.INNER in declaration.modifiers,
                declaration.typeParameters.isNotEmpty(), Modifier.ABSTRACT in declaration.modifiers || Modifier.SEALED in declaration.modifiers,
                declaration.isCompanionObject,
                declaration.getConstructors().any { constructor -> constructor.parameters.all { it.hasDefault } }
            )
            if (constraint != null) { error(constraint, declaration); continue }
            if (!spec.asStarProjectedType().isAssignableFrom(declaration.asStarProjectedType())) {
                error("Registered action must implement ActionSpec (normally through Feature or InfraTask)", declaration); continue
            }
            val processes = (args["processes"] as? List<*>)?.map { it.toString().substringAfterLast('.') } ?: listOf("MAIN")
            if (processes.isEmpty() || processes.distinct().size != processes.size) {
                error("Registered action processes must be non-empty and contain no duplicates", declaration); continue
            }
            val normalized = if ("ALL" in processes) listOf("ALL") else processes.sorted()
            val priority = args["priority"]?.toString()?.substringAfterLast('.') ?: "DEFERRED"
            val entry = ActionRegistration(name, normalized, priority)
            if (!table.add(declaration.containingFile?.filePath ?: "<classpath>", entry)) {
                error("Duplicate action registration: $name", declaration)
            }
        }
        pending = deferred.map { (it as KSClassDeclaration).qualifiedName?.asString() ?: "<anonymous>" }
        return deferred
    }

    override fun finish() {
        if (emitted || failed) return
        // Test and consumer compilations with only a classpath ActionSpec must not shadow the
        // application's registry with an empty object of the same qualified name.
        if (inputs.isEmpty() && !ownsSpec) return
        if (pending.isNotEmpty()) { logger.error("Unresolved action registrations: ${pending.joinToString()}"); return }
        emitted = true
        // This final registry has no annotations for other processors. Accumulate all rounds before
        // writing it once; Kotlin compilation consumes the generated source after KSP finishes.
        codeGenerator.createNewFile(Dependencies(true, *inputs.values.toTypedArray()),
            "com.owo233.tcqt.generated", "GeneratedActionList").bufferedWriter().use { writer ->
            writer.appendLine("package com.owo233.tcqt.generated")
            writer.appendLine("import com.owo233.tcqt.core.action.ActionSpec")
            writer.appendLine("import com.owo233.tcqt.core.action.ActionDescriptor")
            writer.appendLine("import com.owo233.tcqt.core.action.ActionProcess")
            writer.appendLine("import com.owo233.tcqt.core.action.ActionPriority")
            writer.appendLine("internal object GeneratedActionList {")
            writer.appendLine("    val ACTIONS: Array<Class<out ActionSpec>> = arrayOf(")
            table.values().forEach { writer.appendLine("        ${it.name}::class.java,") }
            writer.appendLine("    )")
            writer.appendLine("    val DESCRIPTORS: List<ActionDescriptor> = listOf(")
            table.values().forEach {
                writer.appendLine("        ActionDescriptor(${it.name}::class.java, setOf(${it.processes.joinToString { p -> "ActionProcess.$p" }}), ActionPriority.${it.priority}),")
            }
            writer.appendLine("    )")
            writer.appendLine("}")
        }
    }
}
