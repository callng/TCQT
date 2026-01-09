@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        if (System.getProperty("user.country") == "CN") {
            maven("https://maven.aliyun.com/repository/public/")
            maven("https://maven.aliyun.com/repository/google/")
            maven("https://maven.aliyun.com/repository/gradle-plugin/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (System.getProperty("user.country") == "CN") {
            maven("https://maven.aliyun.com/repository/public/")
            maven("https://maven.aliyun.com/repository/google/")
        }
        maven("https://api.xposed.info")
        maven("https://jitpack.io")
        maven("https://repo1.maven.org/maven2")
    }
}

rootProject.name = "TCQT"
include(":app")
include(":qqinterface")
include(":annotations")
include(":processor")
