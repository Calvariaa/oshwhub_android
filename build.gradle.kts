// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // AGP 9.1.1 自带的是 KGP 2.2.10，与 2026 年的 AndroidX / Compose / kotlinx 产物
        // (metadata 2.4.x) 不兼容，这里显式把 Kotlin 工具链抬到 2.4.21。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.21")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
