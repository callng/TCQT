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

    @Suppress("UnstableApiUsage")
    androidResources {
        localeFilters += listOf("zh-rCN")
        additionalParameters += arrayOf(
            "--allow-reserved-package-id",
            "--package-id", "0x53"
        )
    }

    packaging {
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
    compileOnly(libs.libxposed.api)
    compileOnly(libs.xposed.api)
    compileOnly(libs.androidx.constraintlayout)
    compileOnly(projects.libs.qqinterface)

    ksp(projects.libs.processor)

    implementation(projects.libs.annotations)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.dexkit)
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
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.activity)
    implementation(libs.compose.animation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
}
