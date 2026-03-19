import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.impl.VariantOutputImpl
import com.google.protobuf.gradle.proto
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidCompileSdkVersion: Int by rootProject.extra
val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra
val appVersionName: String by rootProject.extra
val appVersionCode: Int by rootProject.extra
val kotlinJvmTarget: JvmTarget by rootProject.extra
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

extensions.configure<ApplicationExtension> {
    namespace = "com.owo233.tcqt"
    compileSdk {
        version = release(androidCompileSdkVersion) {
            minorApiLevel = 1
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

    fun com.android.build.api.dsl.SigningConfig.applyEnvKeystore() {
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
            signingConfig = if (keystorePath.isNullOrBlank()) null else signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = if (keystorePath.isNullOrBlank()) null else signingConfigs.getByName("ci")
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

    sourceSets.configureEach {
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$name/kotlin"))
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
    compileOnly(libs.androidx.constraintlayout)
    compileOnly(libs.xposed.api)
    compileOnly(project(":qqinterface"))

    ksp(project(":processor"))

    implementation(project(":annotations"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
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
