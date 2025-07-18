package com.owo233.tcqt.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.owo233.tcqt.annotations.RegisterAction

class ActionRegistrarProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = resolver.getSymbolsWithAnnotation(RegisterAction::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        if (!annotated.iterator().hasNext()) return emptyList()

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

        return emptyList()
    }
}
