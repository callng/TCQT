import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
}

extensions.configure<LibraryExtension> {
    namespace = "io.github.libxposed.api"
    compileSdk = 36

    sourceSets {
        val main by getting
        main.apply {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.directories += "src/main/java"
        }
    }

    defaultConfig {
        minSdk = 27
        lint.targetSdk = 36
        buildToolsVersion = "36.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    compileOnly(libs.androidx.annotation)
}
