import com.github.megatronking.stringfog.plugin.StringFogExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "2.2.0"
    id("stringfog")
}

configure<StringFogExtension> {
    // 加解密库的实现类路径
    implementation = "com.github.megatronking.stringfog.xor.StringFogImpl"

    // StringFog会自动尝试获取packageName
    packageName = "com.owo233.tcqt"

    // 加密开关
    enable = true

    // 指定需加密的代码包路径，可配置多个，未指定将默认全部加密
    // fogPackages = ['com.xxx.xxx']

    // 指定密钥生成器，默认使用长度8的随机密钥
    kg = com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator()

    // 控制字符串加密后在字节码中的存在形式
    mode = com.github.megatronking.stringfog.plugin.StringFogMode.bytes
}

android {
    namespace = "com.owo233.tcqt"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.owo233.tcqt"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "APP_NAME", "\"TCQT\"")
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName?.let { fileName ->
                if (fileName.endsWith(".apk")) {
                    val projectName = rootProject.name
                    val versionName = defaultConfig.versionName
                    output.outputFileName = "${projectName}_v${versionName}.apk"
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
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    compileOnly(project(":qqinterface"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.io.jvm)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.protobuf.java)
    implementation(libs.stringfog.xor)
}
