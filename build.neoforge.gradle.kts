plugins {
    id("net.neoforged.moddev") version "2.0.148"
}

fun prop(name: String): String = project.property(name).toString()

val nodeName: String = project.name
val (mcVersion, loader) = nodeName.split('-', limit = 2)

stonecutter {
    properties.tags(mcVersion, loader)
    constants.match(loader, "fabric", "neoforge")
}

// Applied after the property tags above, which the conventions read.
apply(plugin = "kolobok-common")

neoForge {
    version = prop("deps.neoforge")

    runs {
        configureEach {
            ideName = "NeoForge ${name.replaceFirstChar { it.uppercase() }} ($nodeName)"
        }
        // One directory per node, so a NeoForge world is never opened by Fabric by accident.
        create("client") {
            client()
            gameDirectory = rootProject.file("run/$nodeName/client")
        }
        create("server") {
            server()
            gameDirectory = rootProject.file("run/$nodeName/server")
            programArgument("--nogui")
        }
    }

    mods {
        create(prop("mod.id")) {
            sourceSet(sourceSets["main"])
        }
    }
}

// ModDevGradle resolves Minecraft artifacts before Stonecutter has written this node's
// sources, unless told otherwise.
tasks.named("createMinecraftArtifacts") {
    dependsOn(tasks.named("stonecutterGenerate"))
}
