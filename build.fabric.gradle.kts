plugins {
    // The non-remapping Loom: 26.x ships unobfuscated, so there are no mappings, dependencies go to
    // `implementation` rather than `modImplementation`, and the output is the plain `jar`.
    id("net.fabricmc.fabric-loom") version "1.19.1"
}

fun prop(name: String): String = project.property(name).toString()

val nodeName: String = project.name
val (mcVersion, loader) = nodeName.split('-', limit = 2)

stonecutter {
    // Flattens [fabric] and [fabric."<mc>"] from stonecutter.properties.toml into plain
    // Gradle properties for this node.
    properties.tags(mcVersion, loader)
    // Lets source code branch on the loader with //? if fabric {...}
    constants.match(loader, "fabric", "neoforge")
}

// Applied after the property tags above, which the conventions read.
apply(plugin = "kolobok-common")

dependencies {
    "minecraft"("com.mojang:minecraft:${prop("deps.minecraft")}")
    "implementation"("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    "implementation"("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
}

loom {
    runs {
        // Loom pre-registers both, so these are reconfigured rather than created.
        // One directory per node, so a Fabric world is never opened by NeoForge by accident.
        named("client") {
            displayName = "Fabric Client $nodeName"
            generateRunConfig = true
            runDirectory = rootProject.layout.projectDirectory.dir("run/$nodeName/client")
        }
        named("server") {
            displayName = "Fabric Server $nodeName"
            generateRunConfig = true
            runDirectory = rootProject.layout.projectDirectory.dir("run/$nodeName/server")
            programArguments.add("--nogui")
        }
    }
}

// Client gametests: a real client walks through a scripted scene and saves screenshots, which is how
// the model, the faces, the bubble and the shoulder are checked without a person at the screen.
// `gradlew :26.3-fabric:runClientGameTest`; screenshots land in build/run/clientGameTest/screenshots.
// Rendering is common code, so one loader is enough for this.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "kolobok-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
        clearRunDirectory = true
    }
}

// `-Pshowcase` also stages the README's screenshots (ShowcaseVisualTest) into media/screenshots.
if (providers.gradleProperty("showcase").isPresent) {
    tasks.named<JavaExec>("runClientGameTest") {
        systemProperty("kolobok.showcase", rootProject.file("media/screenshots").absolutePath)
    }
}
