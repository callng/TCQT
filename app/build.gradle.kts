import com.android.build.api.dsl.ApplicationExtension
import com.google.protobuf.gradle.proto
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidCompileSdkVersion: Int by rootProject.extra
val androidBuildToolsVersion: String by rootProject.extra
val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra
val appVersionName: String by rootProject.extra
val appVersionCode: Int by rootProject.extra
val kotlinJvmTarget: JvmTarget by rootProject.extra
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

extensions.configure<ApplicationExtension> {
    namespace = "com.owo233.tcqt"
    compileSdk = androidCompileSdkVersion
    buildToolsVersion = androidBuildToolsVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "APP_NAME", "\"TCQT\"")
        buildConfigField("Long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
        buildConfigField("String", "OPEN_SOURCE", "\"https://github.com/callng/TCQT\"")
        buildConfigField("String", "TG_CHANNEL", "\"citcqt\"")
        buildConfigField("String", "TG_GROUP", "\"astcqt\"")
    }

    @Suppress("UnstableApiUsage")
    androidResources {
        localeFilters += listOf("zh-rCN")
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
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
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
        resources {
            excludes += "google/**"
            excludes += "kotlin/**"
            excludes += "META-INF/*.version"
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
        }
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

base {
    archivesName.set(
        "${rootProject.name}-${appVersionName}"
    )
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
    implementation(libs.androidx.appcompat)
    compileOnly(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.io.jvm)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.protobuf.java)
}
