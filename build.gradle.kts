buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9.5 bundles Kotlin 2.2.x, but the AndroidX and kotlinx libraries used
        // here are compiled with Kotlin 2.4 metadata. AGP supports a higher Kotlin
        // Gradle plugin through this classpath entry.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
