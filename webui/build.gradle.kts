import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins { base }

abstract class CheckWebUiSize : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val files = sourceDirectory.get().asFile.walkTopDown().filter { it.isFile }.toList()
        val unexpected = files.filter { it.extension !in setOf("html", "css", "js", "svg") }
        check(unexpected.isEmpty()) { "WebUI only permits lightweight browser assets: $unexpected" }
        val bytes = files.sumOf { it.length() }
        check(bytes <= 64 * 1024) { "WebUI size budget exceeded: $bytes bytes (maximum 64 KiB)" }
        logger.lifecycle("WebUI: $bytes bytes in ${files.size} files (64 KiB maximum)")
    }
}

val webAssetsDirectory = layout.projectDirectory.dir("../app/src/main/zygisk-template/webroot")
val checkWebUiSize = tasks.register<CheckWebUiSize>("checkWebUiSize") {
    sourceDirectory.set(webAssetsDirectory)
}

val assembleWebUi = tasks.register<Sync>("assembleWebUi") {
    group = "build"
    description = "Stage the offline DOM WebUI within a 64 KiB size budget"
    dependsOn(checkWebUiSize)
    from(webAssetsDirectory)
    into(layout.buildDirectory.dir("dist"))
    // Sync removes stale Kotlin/JS/Skiko outputs when migrating an existing build.
}
tasks.named("assemble") { dependsOn(assembleWebUi) }
tasks.named("check") { dependsOn(checkWebUiSize) }
