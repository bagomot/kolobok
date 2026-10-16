pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.8"
}

rootProject.name = "Kolobok"

// One source tree, one build node per (Minecraft version x loader), the same layout as Geomorph.
// Only 26.3 for now; the next game version becomes one more entry in the outer list.
stonecutter {
    create(rootProject) {
        for (mcVersion in listOf("26.3")) {
            for (loader in listOf("fabric", "neoforge")) {
                version("$mcVersion-$loader", mcVersion).buildscript("build.$loader.gradle.kts")
            }
        }
        vcsVersion.set("26.3-neoforge")
    }
}
