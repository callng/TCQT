@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        if (System.getProperty("user.country") == "CN") {
            maven("https://maven.aliyun.com/repository/public/")
            maven("https://maven.aliyun.com/repository/google/")
            maven("https://maven.aliyun.com/repository/gradle-plugin/")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getProperty("user.country") == "CN") {
            maven("https://maven.aliyun.com/repository/public/")
            maven("https://maven.aliyun.com/repository/google/")
        }
        google()
        mavenCentral()
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
