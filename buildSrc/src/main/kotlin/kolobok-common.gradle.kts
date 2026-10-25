import org.gradle.language.jvm.tasks.ProcessResources

// Conventions shared by every build node. Applied from build.fabric.gradle.kts and
// build.neoforge.gradle.kts *after* the loader plugin and after Stonecutter's property tags, so
// per-target properties such as deps.minecraft_range are already resolvable here.
//
// The node name carries the target, e.g. "26.3-neoforge", which is where the Minecraft version
// and loader come from. Reading it from the project name rather than from the Stonecutter extension
// keeps buildSrc free of a Stonecutter dependency.

plugins {
    java
}

fun prop(name: String): String = project.property(name).toString()

val nodeName: String = project.name
val mcVersion: String = nodeName.substringBeforeLast('-')
val loader: String = nodeName.substringAfterLast('-')
val foreignLoader: String = if (loader == "fabric") "neoforge" else "fabric"

val modId = prop("mod.id")
val modName = prop("mod.name")
val modVersion = prop("mod.version")
val modAuthor = prop("mod.author")
val modLicense = prop("mod.license")
val modDescription = prop("mod.description")
val javaVersion = prop("java.version")
val minecraftRange = prop("deps.minecraft_range")
val loaderRange = if (loader == "fabric") ">=${prop("deps.fabric_loader")}" else prop("deps.neoforge_range")

group = prop("mod.group")
version = modVersion
// The target is already in the artifact name, so the version stays clean:
// kolobok-neoforge-26.3-0.1.0.jar
base.archivesName.set("$modId-$loader-$mcVersion")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
}

repositories {
    mavenCentral()
    // Soft integrations only: JEI, REI (and what its API needs), Jade and Modonomicon.
    maven("https://maven.blamejared.com/") {
        name = "BlameJared"
        content {
            includeGroup("mezz.jei")
            includeGroupAndSubgroups("net.mezzdev")
        }
    }
    maven("https://maven.shedaniel.me/") {
        name = "Shedaniel"
        content {
            includeGroupAndSubgroups("me.shedaniel")
            includeGroup("dev.architectury")
        }
    }
    maven("https://maven.architectury.dev/") {
        name = "Architectury"
        content { includeGroup("dev.architectury") }
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

// Tests get whatever the mod compiles against — Minecraft's bundled libraries such as Gson live
// there, and the loader plugins do not put them on the test classpath by themselves. Wired in
// afterEvaluate so the loader plugin has finished building main's classpath first.
afterEvaluate {
    val main = sourceSets["main"]
    val test = sourceSets["test"]
    test.compileClasspath += main.compileClasspath
    test.runtimeClasspath += main.compileClasspath
}

dependencies {
    // Compiled against, never shipped: each integration only runs when its mod is installed.
    "compileOnly"("mezz.jei:jei-$mcVersion-common-api:${prop("deps.jei")}")
    "compileOnly"("me.shedaniel:RoughlyEnoughItems-api-$loader:${prop("deps.rei")}") {
        isTransitive = false
    }
    "compileOnly"("me.shedaniel:RoughlyEnoughItems-default-plugin-$loader:${prop("deps.rei")}") {
        isTransitive = false
    }
    // What REI's API shows in its signatures: Cloth's points and rectangles, Architectury's fluids.
    "compileOnly"("me.shedaniel.cloth:basic-math:${prop("deps.cloth_math")}")
    "compileOnly"("dev.architectury:architectury-$loader:${prop("deps.architectury")}") {
        isTransitive = false
    }
    "compileOnly"("maven.modrinth:jade:${prop("deps.jade")}")

    // `-Pcompat=jei,jade,modonomicon` (or rei instead of jei) puts the real mods into the dev runs
    // and the client gametests, to look at the integrations. Never part of a build.
    val compat = (findProperty("compat") ?: "").toString().split(',').map { it.trim() }.toSet()
    if ("jei" in compat) {
        "runtimeOnly"("mezz.jei:jei-$mcVersion-$loader:${prop("deps.jei")}")
    }
    if ("rei" in compat) {
        "runtimeOnly"("me.shedaniel:RoughlyEnoughItems-$loader:${prop("deps.rei")}")
        // REI asks for Architectury 22.0.3, whose mixins fail on NeoForge 26.3.0.85; a newer one is picked.
        "runtimeOnly"("dev.architectury:architectury-$loader:${prop("deps.architectury")}")
    }
    if ("jade" in compat) {
        "runtimeOnly"("maven.modrinth:jade:${prop("deps.jade")}")
    }
    if ("modonomicon" in compat) {
        "runtimeOnly"("maven.modrinth:modonomicon:${prop("deps.modonomicon")}")
    }

    "testImplementation"("org.junit.jupiter:junit-jupiter:${prop("deps.junit")}")
    // Gradle 9 no longer adds the launcher implicitly.
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Windows consoles mangle Cyrillic assertion output otherwise, and the song tests are Cyrillic.
    defaultCharacterEncoding = "UTF-8"
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// One source tree holds both loaders' code. Only this node's loader is compiled and packaged, so
// no Fabric class ever reaches a NeoForge jar or vice versa.
//
// Stonecutter's own `filters` are not the tool for this: they only skip preprocessing, the files
// still land in the generated sources.
sourceSets["main"].java.exclude("**/platform/$foreignLoader/**")
sourceSets["main"].resources.exclude(
    if (loader == "fabric") "META-INF/neoforge.mods.toml" else "fabric.mod.json"
)

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to modVersion,
                "Implementation-Title" to "$modName ($loader)",
                "Implementation-Version" to modVersion,
                "Built-On-Minecraft" to mcVersion
            )
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    val expandProps = mapOf(
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_author" to modAuthor,
        "mod_license" to modLicense,
        "mod_description" to modDescription,
        "mod_version" to modVersion,
        "java_version" to javaVersion,
        "minecraft_range" to minecraftRange,
        "loader_range" to loaderRange
    )

    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
        expand(expandProps)
    }
    inputs.properties(expandProps)
}
