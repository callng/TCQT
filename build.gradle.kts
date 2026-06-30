import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.protobuf) apply false
}

val appBaseVersionName = "3.6.5"

abstract class GitCommandValueSource :
    ValueSource<String, GitCommandValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        val args: ListProperty<String>
        val workingDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()

        execOperations.exec {
            commandLine("git", *parameters.args.get().toTypedArray())
            workingDir = parameters.workingDir.get().asFile
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }

        return output
            .toString(Charset.defaultCharset())
            .trim()
    }
}

fun git(vararg args: String) =
    providers.of(GitCommandValueSource::class) {
        parameters {
            this.args.set(args.toList())
            this.workingDir.set(layout.projectDirectory)
        }
    }

val gitCommitCountProvider = git("rev-list", "--count", "HEAD")
val gitCommitHashProvider  = git("rev-parse", "--short=7", "HEAD")

data class GitInfo(
    val commitCount: Int,
    val shortHash: String,
    val versionName: String
)

val gitInfoProvider: Provider<GitInfo> =
    gitCommitCountProvider
        .map { it.toIntOrNull() ?: 1 }
        .zip(gitCommitHashProvider.map { it.ifBlank { "unknown" } }) { count, hash ->
            GitInfo(
                commitCount = count,
                shortHash = hash,
                versionName = "$appBaseVersionName.r$count.$hash"
            )
        }

val gitInfo: GitInfo = gitInfoProvider.get()

val androidMinSdkVersion = 27
val androidTargetSdkVersion = 37
val androidCompileSdkVersion = 37
val androidSourceCompatibility = JavaVersion.VERSION_21
val androidTargetCompatibility = JavaVersion.VERSION_21
val kotlinJvmTarget = JvmTarget.JVM_21

val appVersionCode = gitInfo.commitCount
val appVersionName = gitInfo.versionName
val appGitHash = gitInfo.shortHash

extra.set("appBaseVersionName", appBaseVersionName)
extra.set("androidMinSdkVersion", androidMinSdkVersion)
extra.set("androidTargetSdkVersion", androidTargetSdkVersion)
extra.set("androidCompileSdkVersion", androidCompileSdkVersion)
extra.set("androidSourceCompatibility", androidSourceCompatibility)
extra.set("androidTargetCompatibility", androidTargetCompatibility)
extra.set("kotlinJvmTarget", kotlinJvmTarget)
extra.set("appVersionCode", appVersionCode)
extra.set("appVersionName", appVersionName)
extra.set("appGitHash", appGitHash)
