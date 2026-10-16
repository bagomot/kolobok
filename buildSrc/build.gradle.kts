// Hosts the precompiled convention plugin shared by every build node.
// Kotlin here is build logic only — the mod itself is Java, and no Kotlin runtime ships in any jar.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
