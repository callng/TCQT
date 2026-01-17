import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.AppExtension
import com.google.protobuf.gradle.proto
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
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

val gitCommitCount: Provider<Int> = providers.of(GitCommitCount::class.java) {}
val gitShortHash: Provider<String> = providers.of(GitShortHash::class.java) {}
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

extensions.configure<ApplicationExtension> {
    namespace = "com.owo233.tcqt"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.owo233.tcqt"
        minSdk = 27
        targetSdk = 36
        versionCode = providers.provider { getBuildVersionCode(rootProject) }.get()
        versionName = "3.6.1"
        buildConfigField("String", "APP_NAME", "\"TCQT\"")
        buildConfigField("Long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
        buildConfigField("String", "OPEN_SOURCE", "\"https://github.com/callng/TCQT\"")
        buildConfigField("String", "TG_CHANNEL", "\"citcqt\"")
        buildConfigField("String", "TG_GROUP", "\"astcqt\"")
    }

    signingConfigs {
        create("release") {
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV2Signing = true
            }
        }

        getByName("debug") {
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV2Signing = true
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            versionNameSuffix = providers.provider {
                getGitHeadRefsSuffix(rootProject, "debug")
            }.get()
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            versionNameSuffix = providers.provider {
                getGitHeadRefsSuffix(rootProject, "release")
            }.get()
            signingConfig = signingConfigs.getByName("release")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

configure<AppExtension> {
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
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

extensions.configure(KotlinAndroidProjectExtension::class.java) {
    compilerOptions {
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            listOf(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions"
            )
        )
    }

    sourceSets.configureEach {
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$name/kotlin"))
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
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
}

fun getGitHeadRefsSuffix(project: Project, buildType: String): String {
    val rootProject = project.rootProject
    val projectDir = rootProject.projectDir
    val headFile = File(projectDir, ".git" + File.separator + "HEAD")
    return if (headFile.exists()) {
        try {
            val commitCount = gitCommitCount.get()
            val hash = gitShortHash.get()
            val prefix = if (buildType == "debug") ".d" else ".r"
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

tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }
    .configureEach {
        val variantName = name.removePrefix("ksp").removeSuffix("Kotlin")
        val protoTaskName = "generate${variantName}Proto"
        tasks.findByName(protoTaskName)?.let { protoTask ->
            dependsOn(protoTask)
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
    implementation(libs.protobuf.java)
}
