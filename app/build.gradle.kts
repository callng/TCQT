import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.owo233.tcqt.build.DualApkTask
import com.owo233.tcqt.build.PrepareZygiskModule
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.SigningConfig
import com.android.build.api.variant.impl.VariantOutputImpl
import com.google.protobuf.gradle.proto
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val androidMinSdkVersion =
    rootProject.extra["androidMinSdkVersion"] as Int
val androidTargetSdkVersion =
    rootProject.extra["androidTargetSdkVersion"] as Int
val androidCompileSdkVersion =
    rootProject.extra["androidCompileSdkVersion"] as Int
val androidSourceCompatibility =
    rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility =
    rootProject.extra["androidTargetCompatibility"] as JavaVersion
val androidNdkVersion =
    rootProject.extra["androidNdkVersion"] as String
val androidCmakeVersion =
    rootProject.extra["androidCmakeVersion"] as String
val appVersionName =
    rootProject.extra["appVersionName"] as String
val appVersionCode =
    rootProject.extra["appVersionCode"] as Int
val kotlinJvmTarget =
    rootProject.extra["kotlinJvmTarget"] as JvmTarget

val keystorePath: String? = System.getenv("KEYSTORE_PATH")

val buildTimeDir =
    layout.buildDirectory.dir("generated/source/buildtime/main")

val generateBuildTimeSource = tasks.register("generateBuildTimeSource") {
    description = "BuildTime"

    outputs.upToDateWhen { false }

    val outputFile = buildTimeDir
        .get()
        .file("com/owo233/tcqt/data/BuildTime.kt")
        .asFile

    outputs.file(outputFile)

    doLast {
        outputFile.parentFile.mkdirs()

        val formattedTime =
            SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.CHINA
            ).apply {
                timeZone = TimeZone.getTimeZone("GMT+8")
            }.format(Date())

        outputFile.writeText(
            """
            package com.owo233.tcqt.data

            object BuildTime {
                const val TIMESTAMP = "$formattedTime"
            }
            """.trimIndent()
        )
    }
}

tasks.configureEach {
    if (name.contains("Kotlin", ignoreCase = true) || name.contains("ksp", ignoreCase = true)) {
        dependsOn(generateBuildTimeSource)
    }
}

extensions.configure<ApplicationExtension> {
    namespace = "com.owo233.tcqt"
    ndkVersion = androidNdkVersion

    compileSdk {
        version = release(androidCompileSdkVersion) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.owo233.tcqt"
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += "arm64-v8a"
        }

        buildConfigField("String", "APP_NAME", "\"TCQT\"")
        buildConfigField("String", "OPEN_ISSUES", "\"https://github.com/callng/TCQT/issues\"")
        buildConfigField("String", "OPEN_SOURCE", "\"https://github.com/callng/TCQT\"")
        buildConfigField("String", "TG_CHANNEL", "\"citcqt\"")
        buildConfigField("String", "TG_GROUP", "\"astcqt\"")
    }

    fun SigningConfig.applyEnvKeystore() {
        if (!keystorePath.isNullOrBlank()) {
            storeFile = file(keystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    signingConfigs {
        create("ci") {
            applyEnvKeystore()
            enableV2Signing = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            signingConfig =
                if (keystorePath.isNullOrBlank()) signingConfigs.getByName("debug")
                else signingConfigs.getByName("ci")
        }
        release {
            signingConfig =
                if (keystorePath.isNullOrBlank()) null else signingConfigs.getByName("ci")
            optimization {
                enable = true
                keepRules {
                    includeDefault = false
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = androidCmakeVersion
        }
    }

    @Suppress("UnstableApiUsage")
    androidResources {
        localeFilters += listOf("zh-rCN")
        additionalParameters += arrayOf(
            "--allow-reserved-package-id",
            "--package-id", "0x53"
        )
    }

    packaging {
        jniLibs {
            excludes += "**/libtcqtzygisk.so"
        }
        resources {
            excludes += "google/**"
            excludes += "kotlin/**"
            excludes += "META-INF/androidx/**"
            excludes += "META-INF/org/**"
            excludes += "META-INF/androidx*"
            excludes += "META-INF/kotlinx*"
            excludes += "WEB-INF/**"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    sourceSets {
        named("debug") {
            // Compile the documented example without registering it or shipping it in release.
            kotlin.directories += rootProject.layout.projectDirectory.dir("examples").asFile.absolutePath
        }
        named("main") {
            proto {
                srcDirs("src/main/proto")
            }

            kotlin.directories += "generated/ksp/$name/kotlin"
            kotlin.directories += buildTimeDir.get().asFile.absolutePath
        }
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                output.outputFileName =
                    "${rootProject.name}-${appVersionName}-${variant.buildType}.apk"
            }
        }
    }
}

extensions.configure(KotlinAndroidProjectExtension::class.java) {
    compilerOptions {
        jvmTarget.set(kotlinJvmTarget)
        freeCompilerArgs.addAll(
            listOf(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions"
            )
        )
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
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

dependencies {
    val sharedDeps = listOf(
        libs.androidx.constraintlayout,
        projects.libs.qqinterface,
    )

    sharedDeps.forEach {
        compileOnly(it)
        testImplementation(it)
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)

    compileOnly(libs.libxposed.api)
    compileOnly(libs.xposed.api)

    ksp(projects.libs.processor)

    implementation(projects.libs.annotations)
    implementation(projects.libs.core)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.dexkit)
    implementation(libs.dexmaker)
    implementation(libs.fastkv)
    implementation(libs.kotlinx.io.jvm)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.protobuf.java)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.animation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.kyant0.backdrop)
    implementation(libs.kyant0.shapes)
}

abstract class TransformDualApkTask : DualApkTask() {
    @get:Internal abstract val transformationRequest: Property<ArtifactTransformationRequest<TransformDualApkTask>>
    @TaskAction fun transform() {
        initializeOutput()
        transformationRequest.get().submit(this) { packArtifact(File(it.outputFile)) }
    }
}

// AGP owns SDK/tool versions and the raw APK; all variants share typed packaging tasks.
val sdkDirectory = androidComponents.sdkComponents.sdkDirectory
val androidDsl = extensions.getByType<ApplicationExtension>()
val osName = providers.systemProperty("os.name").get().lowercase()
val executableSuffix = if (osName.contains("win")) ".exe" else ""
// Android NDK's macOS bundle is darwin-x86_64 (also on Apple Silicon).
val ndkHost = when { osName.contains("win") -> "windows-x86_64"; osName.contains("mac") -> "darwin-x86_64"; else -> "linux-x86_64" }
val toolDirectory = sdkDirectory.map { it.dir("build-tools/${androidDsl.buildToolsVersion}") }
val injectedStore = providers.gradleProperty("android.injected.signing.store.file").orNull
val envStore = providers.environmentVariable("KEYSTORE_PATH").orNull
val signingStore = (injectedStore ?: envStore)?.takeIf { it.isNotBlank() }
val debugStore = File(providers.systemProperty("user.home").get(), ".android/debug.keystore")
val packagingKeystore = signingStore?.let { file(it) } ?: debugStore
fun signingValue(injectedName: String, envName: String, fallback: String): String = when {
    injectedStore != null -> providers.gradleProperty(injectedName).getOrElse("")
    envStore != null -> providers.environmentVariable(envName).getOrElse("")
    else -> fallback
}

androidComponents.onVariants { variant ->
    val capitalized = variant.name.replaceFirstChar { it.uppercase() }
    val prepare = tasks.register<PrepareZygiskModule>("prepareZygiskModule$capitalized") {
        group = "zygisk"
        dependsOn("externalNativeBuild$capitalized", ":webui:assembleWebUi")
        templateDirectory.set(layout.projectDirectory.dir("src/main/zygisk-template"))
        webUiDirectory.set(rootProject.layout.projectDirectory.dir("webui/build/dist"))
        nativeLibrary.set(layout.buildDirectory.file("intermediates/cmake/${variant.name}/obj/arm64-v8a/libtcqtzygisk.so"))
        stripExecutable.set(sdkDirectory.map { it.file("ndk/$androidNdkVersion/toolchains/llvm/prebuilt/$ndkHost/bin/llvm-strip$executableSuffix") })
        versionName.set(appVersionName)
        versionCode.set(appVersionCode)
        outputDirectory.set(layout.buildDirectory.dir("zygisk-module-${variant.name}"))
    }
    val dual = tasks.register<TransformDualApkTask>("buildDualApk$capitalized") {
        group = "zygisk"
        description = "Build signed dual-format ${variant.name} APK"
        moduleDirectory.set(prepare.flatMap { it.outputDirectory })
        zipalign.set(toolDirectory.map { it.file("zipalign$executableSuffix") })
        apksignerJar.set(toolDirectory.map { it.file("lib/apksigner.jar") })
        javaExecutable.set(File(System.getProperty("java.home"), "bin/java$executableSuffix"))
        keystore.set(packagingKeystore)
        keyAlias.set(signingValue("android.injected.signing.key.alias", "KEY_ALIAS", "androiddebugkey"))
        storePassword.set(signingValue("android.injected.signing.store.password", "KEYSTORE_PASSWORD", "android"))
        keyPassword.set(signingValue("android.injected.signing.key.password", "KEY_PASSWORD", "android"))
    }
    val transformation = variant.artifacts.use(dual)
        .wiredWithDirectories(DualApkTask::getInputApkDirectory, DualApkTask::getOutputApkDirectory)
        .toTransformMany(SingleArtifact.APK)
    dual.configure {
        transformationRequest.set(transformation)
    }
}

tasks.register("prepareZygiskModule") { group = "zygisk"; dependsOn("prepareZygiskModuleRelease") }
tasks.register("buildDualApk") { group = "zygisk"; dependsOn("buildDualApkRelease") }
val packageZygiskModule = tasks.register<Copy>("packageZygiskModule") {
    group = "zygisk"
    description = "Copy the dual-format release APK byte-for-byte to a flashable ZIP"
    dependsOn("buildDualApkRelease")
    from(layout.buildDirectory.file("outputs/apk/release/TCQT-$appVersionName-release.apk"))
    into(layout.buildDirectory.dir("outputs/zygisk"))
    rename(".*[.]apk", "TCQT-zygisk-$appVersionName.zip")
}
tasks.named("assemble") { dependsOn(packageZygiskModule) }
