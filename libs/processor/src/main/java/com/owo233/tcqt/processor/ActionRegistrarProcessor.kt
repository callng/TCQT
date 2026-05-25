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
        processActions(resolver)
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
            }
            """.trimIndent()
            )
        }
    }
}
