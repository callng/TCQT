import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.SigningConfig
import com.android.build.api.variant.impl.VariantOutputImpl
import com.google.protobuf.gradle.proto
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
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
    implementation(libs.compose.activity)
    implementation(libs.compose.animation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
}

// ── Zygisk 模块打包 ──────────────────────────────────────────────────────────
fun registerPrepareTask(
    taskName: String,
    variant: String,
    stagingDirName: String,
    strippedPath: String,
): TaskProvider<Task> {
    return tasks.register(taskName) {
        group = "zygisk"
        description = "组装 Zygisk 模块目录（$variant）"
        notCompatibleWithConfigurationCache("stages the Zygisk module ZIP")
        dependsOn("assemble${variant.replaceFirstChar { it.uppercase() }}")
        dependsOn("externalNativeBuild${variant.replaceFirstChar { it.uppercase() }}")

        val stageDirProvider = layout.buildDirectory.dir(stagingDirName)
        val templateDir = layout.projectDirectory.dir("src/main/zygisk-template")
        val apkFileProvider =
            layout.buildDirectory.file("outputs/apk/$variant/TCQT-${appVersionName}-$variant.apk")
        val soDirProvider = layout.buildDirectory.dir(strippedPath)

        inputs.dir(templateDir)
        inputs.file(apkFileProvider)
        inputs.dir(soDirProvider)
        outputs.dir(stageDirProvider)

        doLast {
            val stageDir = stageDirProvider.get().asFile
            stageDir.deleteRecursively()
            stageDir.mkdirs()

            // 模板
            templateDir.asFile.copyRecursively(stageDir)

            // 版本占位符
            val propFile = File(stageDir, "module.prop")
            propFile.writeText(
                propFile.readText()
                    .replace("@VERSION@", appVersionName)
                    .replace("@VERSION_CODE@", appVersionCode.toString())
            )

            // 注入器 so
            val soBytes = File(soDirProvider.get().asFile, "libtcqtzygisk.so").readBytes()
            File(stageDir, "zygisk/arm64-v8a.so").apply {
                parentFile.mkdirs()
                writeBytes(soBytes)
            }
            // customize.sh 从 ZIP 的 lib/<abi>/ 解压
            File(stageDir, "lib/arm64-v8a/libtcqtzygisk.so").apply {
                parentFile.mkdirs()
                writeBytes(soBytes)
            }

            // payload APK
            File(stageDir, "payload/tcqt.apk").apply {
                parentFile.mkdirs()
                apkFileProvider.get().asFile.copyTo(this, overwrite = true)
            }

            logger.lifecycle("Zygisk module ($variant) staged at $stageDir")
        }
    }
}

val prepareZygiskModuleRelease = registerPrepareTask(
    "prepareZygiskModuleRelease", "release", "zygisk-module-release",
    "intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/arm64-v8a")
val prepareZygiskModuleDebug = registerPrepareTask(
    "prepareZygiskModuleDebug", "debug", "zygisk-module-debug",
    "intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/arm64-v8a")

// 兼容旧引用：prepareZygiskModule = release 变体
val prepareZygiskModule = tasks.register("prepareZygiskModule") {
    group = "zygisk"
    description = "组装 Zygisk 模块目录（release，兼容旧名）"
    dependsOn(prepareZygiskModuleRelease)
}

val packageZygiskModule = tasks.register("packageZygiskModule", Zip::class.java) {
    group = "zygisk"
    description = "打包 TCQT Zygisk 模块 ZIP"
    notCompatibleWithConfigurationCache("packages the Zygisk module ZIP")
    dependsOn(prepareZygiskModuleRelease)

    val stageDirProvider = layout.buildDirectory.dir("zygisk-module-release")
    val outDirProvider = layout.buildDirectory.dir("outputs/zygisk")
    archiveFileName.set("TCQT-zygisk-${appVersionName}.zip")
    destinationDirectory.set(outDirProvider.get().asFile)
    from(stageDirProvider)

    doLast {
        logger.lifecycle("Zygisk module ZIP: ${File(outDirProvider.get().asFile, archiveFileName.get())}")
    }
}

tasks.named("assemble") {
    dependsOn(packageZygiskModule)
}

// ── 双格式 APK（release）──────────────────────────────────────────────────────
val buildDualApkRelease = tasks.register("buildDualApkRelease") {
    group = "zygisk"
    description = "构建双格式 APK（release：.apk = XP 模块，.zip = Zygisk 模块）"
    notCompatibleWithConfigurationCache("stages the dual-format APK")
    dependsOn("assembleRelease")
    dependsOn(prepareZygiskModuleRelease)

    val apkName = "TCQT-${appVersionName}-release.apk"
    val srcApkProvider = layout.buildDirectory.file("outputs/apk/release/$apkName")
    val stageDirProvider = layout.buildDirectory.dir("zygisk-module-release")
    val outApkProvider = layout.buildDirectory.file("outputs/apk/release/$apkName")

    // 配置期捕获 SDK 路径（doLast 闭包里 android 扩展不可见）。
    val sdkDir: File = run {
        val fromLocalProps = project.rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.let { f ->
                Properties().apply { f.inputStream().use { load(it) } }.getProperty("sdk.dir")
            }
            ?.trim()
        fromLocalProps?.let { File(it) }
            ?: System.getenv("ANDROID_HOME")?.let { File(it) }
            ?: error("无法定位 Android SDK：请设置 ANDROID_HOME 或 local.properties 的 sdk.dir")
    }
    val btDir = File(sdkDir, "build-tools")
        .listFiles()?.maxByOrNull { it.name }
        ?: error("no build-tools found under $sdkDir")

    inputs.file(srcApkProvider)
    inputs.dir(stageDirProvider)
    outputs.file(outApkProvider)

    // 运行外部工具（zipalign / apksigner），回显输出，失败抛错。
    fun runCmd(vararg args: String) {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().use { r ->
            r.forEachLine { line -> if (line.isNotBlank()) logger.lifecycle("  $line") }
        }
        val code = p.waitFor()
        if (code != 0) error("command failed (exit=$code): ${args.joinToString(" ")}")
    }

    doLast {
        val workDir = File(layout.buildDirectory.get().asFile, "dual-apk-work-release")
        workDir.deleteRecursively()
        workDir.mkdirs()
        val unpacked = File(workDir, "unpacked").apply { mkdirs() }

        // 1. 解压签名 APK
        ZipFile(srcApkProvider.get().asFile).use { zip ->
            zip.entries().asSequence().forEach { e ->
                val out = File(unpacked, e.name)
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile.mkdirs()
                    zip.getInputStream(e).use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                }
            }
        }

        // 2. 注入 zygisk 模块内容
        stageDirProvider.get().asFile.copyRecursively(unpacked, overwrite = true)

        // 3. 重打包为未对齐 APK
        val unaligned = File(workDir, "dual-unaligned.apk")
        ZipOutputStream(unaligned.outputStream()).use { zos ->
            unpacked.walkTopDown().forEach { f ->
                if (f.isFile) {
                    val rel = f.relativeTo(unpacked).path.replace('\\', '/')
                    val stored = rel.startsWith("lib/") && rel.endsWith(".so") ||
                        rel == "resources.arsc"
                    if (stored) {
                        val entry = ZipEntry(rel)
                        entry.method = ZipEntry.STORED
                        entry.size = f.length()
                        entry.compressedSize = f.length()
                        val crc = CRC32()
                        f.inputStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                crc.update(buf, 0, n)
                            }
                        }
                        entry.crc = crc.value
                        zos.putNextEntry(entry)
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(rel))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }

        // 4. zipalign（4 字节 + .so 页对齐）→ apksigner 签名
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val zipalignExe = File(btDir, if (isWindows) "zipalign.exe" else "zipalign")
        val aligned = File(workDir, "dual-aligned.apk")
        runCmd(zipalignExe.absolutePath, "-f", "-p", "4", unaligned.absolutePath, aligned.absolutePath)

        val outFile = outApkProvider.get().asFile
        outFile.parentFile.mkdirs()
        val ksPath = System.getenv("KEYSTORE_PATH")
        val ksPass = System.getenv("KEYSTORE_PASSWORD")
        val ksAlias = System.getenv("KEY_ALIAS")
        val ksKeyPass = System.getenv("KEY_PASSWORD")
        val signArgs = if (ksPath.isNullOrBlank()) {
            // 回退 debug keystore（本地无签名环境时也能产出可安装 APK）
            val debugKs = File(System.getProperty("user.home"), ".android/debug.keystore")
            listOf(
                "--ks", debugKs.absolutePath,
                "--ks-pass", "pass:android",
                "--ks-key-alias", "androiddebugkey",
                "--key-pass", "pass:android"
            )
        } else {
            listOf(
                "--ks", ksPath,
                "--ks-pass", "pass:$ksPass",
                "--ks-key-alias", ksAlias,
                "--key-pass", "pass:$ksKeyPass"
            )
        }
        runCmd(
            "java", "-jar", File(btDir, "lib/apksigner.jar").absolutePath, "sign",
            *signArgs.toTypedArray(),
            "--v1-signing-enabled", "false",
            "--v3-signing-enabled", "false",
            "--out", outFile.absolutePath, aligned.absolutePath
        )
        logger.lifecycle("Dual-format APK (release): $outFile")
    }
}

// ── 双格式 APK（debug）───────────────────────────────────────────────────────
val buildDualApkDebug = tasks.register("buildDualApkDebug") {
    group = "zygisk"
    description = "构建双格式 APK（debug：.apk = XP 模块，.zip = Zygisk 模块）"
    notCompatibleWithConfigurationCache("stages the dual-format APK")
    dependsOn("assembleDebug")
    dependsOn(prepareZygiskModuleDebug)

    val apkName = "TCQT-${appVersionName}-debug.apk"
    val srcApkProvider = layout.buildDirectory.file("outputs/apk/debug/$apkName")
    val stageDirProvider = layout.buildDirectory.dir("zygisk-module-debug")
    val outApkProvider = layout.buildDirectory.file("outputs/apk/debug/$apkName")

    // 配置期捕获 SDK 路径（doLast 闭包里 android 扩展不可见）。
    val sdkDir: File = run {
        val fromLocalProps = project.rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.let { f ->
                Properties().apply { f.inputStream().use { load(it) } }.getProperty("sdk.dir")
            }
            ?.trim()
        fromLocalProps?.let { File(it) }
            ?: System.getenv("ANDROID_HOME")?.let { File(it) }
            ?: error("无法定位 Android SDK：请设置 ANDROID_HOME 或 local.properties 的 sdk.dir")
    }
    val btDir = File(sdkDir, "build-tools")
        .listFiles()?.maxByOrNull { it.name }
        ?: error("no build-tools found under $sdkDir")

    inputs.file(srcApkProvider)
    inputs.dir(stageDirProvider)
    outputs.file(outApkProvider)

    // 运行外部工具（zipalign / apksigner），回显输出，失败抛错。
    fun runCmd(vararg args: String) {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().use { r ->
            r.forEachLine { line -> if (line.isNotBlank()) logger.lifecycle("  $line") }
        }
        val code = p.waitFor()
        if (code != 0) error("command failed (exit=$code): ${args.joinToString(" ")}")
    }

    doLast {
        val workDir = File(layout.buildDirectory.get().asFile, "dual-apk-work-debug")
        workDir.deleteRecursively()
        workDir.mkdirs()
        val unpacked = File(workDir, "unpacked").apply { mkdirs() }

        // 1. 解压签名 APK
        ZipFile(srcApkProvider.get().asFile).use { zip ->
            zip.entries().asSequence().forEach { e ->
                val out = File(unpacked, e.name)
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile.mkdirs()
                    zip.getInputStream(e).use { input -> out.outputStream().use { output -> input.copyTo(output) } }
                }
            }
        }

        // 2. 注入 zygisk 模块内容
        stageDirProvider.get().asFile.copyRecursively(unpacked, overwrite = true)

        // 3. 重打包为未对齐 APK
        val unaligned = File(workDir, "dual-unaligned.apk")
        ZipOutputStream(unaligned.outputStream()).use { zos ->
            unpacked.walkTopDown().forEach { f ->
                if (f.isFile) {
                    val rel = f.relativeTo(unpacked).path.replace('\\', '/')
                    val stored = rel.startsWith("lib/") && rel.endsWith(".so") ||
                        rel == "resources.arsc"
                    if (stored) {
                        val entry = ZipEntry(rel)
                        entry.method = ZipEntry.STORED
                        entry.size = f.length()
                        entry.compressedSize = f.length()
                        val crc = CRC32()
                        f.inputStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                crc.update(buf, 0, n)
                            }
                        }
                        entry.crc = crc.value
                        zos.putNextEntry(entry)
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(rel))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }

        // 4. zipalign（4 字节 + .so 页对齐）→ apksigner 签名
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
        val zipalignExe = File(btDir, if (isWindows) "zipalign.exe" else "zipalign")
        val aligned = File(workDir, "dual-aligned.apk")
        runCmd(zipalignExe.absolutePath, "-f", "-p", "4", unaligned.absolutePath, aligned.absolutePath)

        val outFile = outApkProvider.get().asFile
        outFile.parentFile.mkdirs()
        val ksPath = System.getenv("KEYSTORE_PATH")
        val ksPass = System.getenv("KEYSTORE_PASSWORD")
        val ksAlias = System.getenv("KEY_ALIAS")
        val ksKeyPass = System.getenv("KEY_PASSWORD")
        val signArgs = if (ksPath.isNullOrBlank()) {
            // 回退 debug keystore（本地无签名环境时也能产出可安装 APK）
            val debugKs = File(System.getProperty("user.home"), ".android/debug.keystore")
            listOf(
                "--ks", debugKs.absolutePath,
                "--ks-pass", "pass:android",
                "--ks-key-alias", "androiddebugkey",
                "--key-pass", "pass:android"
            )
        } else {
            listOf(
                "--ks", ksPath,
                "--ks-pass", "pass:$ksPass",
                "--ks-key-alias", ksAlias,
                "--key-pass", "pass:$ksKeyPass"
            )
        }
        runCmd(
            "java", "-jar", File(btDir, "lib/apksigner.jar").absolutePath, "sign",
            *signArgs.toTypedArray(),
            "--v1-signing-enabled", "false",
            "--v3-signing-enabled", "false",
            "--out", outFile.absolutePath, aligned.absolutePath
        )
        logger.lifecycle("Dual-format APK (debug): $outFile")
    }
}

// 兼容旧名：buildDualApk = release 变体
val buildDualApk = tasks.register("buildDualApk") {
    group = "zygisk"
    description = "构建双格式 APK（release，兼容旧名）"
    dependsOn("buildDualApkRelease")
}
