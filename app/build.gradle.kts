import com.google.protobuf.gradle.proto
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

abstract class GitCommitCount : ValueSource<Int, ValueSourceParameters.None> {
    @get:Inject abstract val execOperations: ExecOperations

    override fun obtain(): Int {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            standardOutput = output
        }
        return output.toString().trim().toInt()
    }
}

abstract class GitShortHash : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
            standardOutput = output
        }
        return output.toString().trim()
    }
}

val gitCommitCount = providers.of(GitCommitCount::class.java) {}
val gitShortHash = providers.of(GitShortHash::class.java) {}

android {
    namespace = "com.owo233.tcqt"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.owo233.tcqt"
        minSdk = 27
        targetSdk = 36
        versionCode = providers.provider { getBuildVersionCode(rootProject) }.get()
        versionName = "3.3.1"
        buildConfigField("String", "APP_NAME", "\"TCQT\"")
        buildConfigField("Long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            val gitSuffix = providers.provider { getGitHeadRefsSuffix(rootProject, "debug") }.get()
            versionNameSuffix = ".${gitSuffix}"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val gitSuffix = providers.provider { getGitHeadRefsSuffix(rootProject, "release") }.get()
            versionNameSuffix = ".${gitSuffix}"
        }
    }
    androidResources {
        additionalParameters += arrayOf(
            "--allow-reserved-package-id",
            "--package-id", "0x53"
        )
    }
    packaging {
        resources.excludes.addAll(
            arrayOf(
                "google/**",
                "kotlin/**",
                "META-INF/**",
                "WEB-INF/**",
                "**.bin",
                "kotlin-tooling-metadata.json"
            )
        )
    }

    sourceSets {
        named("main") {
            proto {
                srcDirs("src/main/proto")
            }
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName?.let { fileName ->
                if (fileName.endsWith(".apk")) {
                    val projectName = rootProject.name
                    val currentVersionName = versionName
                    output.outputFileName = "${projectName}-v${currentVersionName}.APK"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.set(listOf(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions"
            ))
        }

        sourceSets.configureEach { kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$name/kotlin")) }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.32.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

fun getGitHeadRefsSuffix(project: Project, buildType: String): String {
    val rootProject = project.rootProject
    val projectDir = rootProject.projectDir
    val headFile = File(projectDir, ".git" + File.separator + "HEAD")
    return if (headFile.exists()) {
        try {
            val commitCount = gitCommitCount.get()
            val hash = gitShortHash.get()
            val prefix = if (buildType == "debug") "d" else "r"
            "$prefix$commitCount.$hash"
        } catch (e: Exception) {
            println("Failed to get git info: ${e.message}")
            ".standalone"
        }
    } else {
        println("Git HEAD file not found")
        ".standalone"
    }
}

fun getBuildVersionCode(project: Project): Int {
    val rootProject = project.rootProject
    val projectDir = rootProject.projectDir
    val headFile = File(projectDir, ".git" + File.separator + "HEAD")
    return if (headFile.exists()) {
        try {
            gitCommitCount.get()
        } catch (e: Exception) {
            println("Failed to get git commit count: ${e.message}")
            1
        }
    } else {
        println("Git HEAD file not found")
        1
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    compileOnly(project(":qqinterface"))
    ksp(project(":processor"))
    implementation(project(":annotations"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.io.jvm)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.nanohttpd)
    implementation(libs.protobuf.java)
}
