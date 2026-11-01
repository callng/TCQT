plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    api(libs.protobuf.java)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test.junit)
}
