import com.google.protobuf.gradle.proto
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    dependencies {
        classpath(libs.eclipse.jgit)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.owo233.tcqt"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.owo233.tcqt"
        minSdk = 27
        targetSdk = 36
        versionCode = providers.provider { getBuildVersionCode(rootProject) }.get()
        versionName = "2.9"
        buildConfigField("String", "APP_NAME", "\"TCQT\"")
        buildConfigField("Long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
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
                    val versionName = defaultConfig.versionName
                    val gitSuffix = providers.provider { getGitHeadRefsSuffix(rootProject) }.get()
                    output.outputFileName = "${projectName}_v${versionName}_${gitSuffix}.apk"
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
        artifact = "com.google.protobuf:protoc:3.25.3"
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

fun getGitHeadRefsSuffix(project: Project): String {
    val rootProject = project.rootProject
    val headFile = File(rootProject.projectDir, ".git" + File.separator + "HEAD")
    return if (headFile.exists()) {
        FileRepository(rootProject.file(".git")).use { repo ->
            val refId = repo.resolve("HEAD")
            val commitCount = Git(repo).log().add(refId).call().count()
            ".r" + commitCount + "." + refId.name.substring(0, 7)
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
        FileRepository(rootProject.file(".git")).use { repo ->
            val refId = repo.resolve("HEAD")
            Git(repo).log().add(refId).call().count()
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
    implementation(libs.hutool.core)
    implementation(libs.kotlinx.io.jvm)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.nanohttpd)
    implementation(libs.protobuf.java)
}
