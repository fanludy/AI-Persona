// settings.gradle.kts 完整代码

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
//        // 阿里云镜像地址
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        gradlePluginPortal()
    }
    // 【关键新增】在这里定义插件的版本，供整个项目使用
    plugins {
        // 移除旧的 KSP 配置，改为 Kotlin Android 插件（Kapt 所需）
        id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false

        // 确保在这里定义了你的应用插件版本 (假设为 8.2.0)
        id("com.android.application") version "8.2.0" apply false
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Demo"
include(":app")