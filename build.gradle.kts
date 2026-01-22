import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.protobuf) apply false
}

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
                versionName = "v3.6.1-$count-$hash"
            )
        }

val gitInfo: GitInfo = gitInfoProvider.get()

val androidMinSdkVersion         by extra(27)
val androidTargetSdkVersion      by extra(36)
val androidCompileSdkVersion     by extra(36)
val androidBuildToolsVersion     by extra("36.1.0")
val androidSourceCompatibility   by extra(JavaVersion.VERSION_21)
val androidTargetCompatibility   by extra(JavaVersion.VERSION_21)

val appVersionCode: Int    by extra(gitInfo.commitCount)
val appVersionName: String by extra(gitInfo.versionName)
val appGitHash: String     by extra(gitInfo.shortHash)
