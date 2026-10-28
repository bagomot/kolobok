// Hosts the precompiled convention plugin shared by every build node.
// Kotlin here is build logic only — the mod itself is Java, and no Kotlin runtime ships in any jar.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Uploads each loader's jar to Modrinth and CurseForge.
    implementation("me.modmuss50:mod-publish-plugin:2.2.1")
}
