// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Android 应用插件的类路径和版本
    alias(libs.plugins.android.application) apply false

    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false

    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}