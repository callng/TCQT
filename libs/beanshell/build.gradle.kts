import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
}

extensions.configure<LibraryExtension> {
    namespace = "com.owo233.beanshell"
    compileSdk = 37

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // BeanShell 的 Lambda / 脚本内定义类要用运行期 dex 转换器
    implementation(libs.dalvik.dx)
}
