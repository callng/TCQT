plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(projects.libs.annotations)
    implementation(libs.ksp.api)
    testImplementation(libs.kotlin.test.junit)
}
