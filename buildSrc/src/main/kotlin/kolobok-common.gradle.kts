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
    id("me.modmuss50.mod-publish-plugin")
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
// kolobok-neoforge-26.3-1.2.0.jar
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
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_$modId" }
    }
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

// ---------------------------------------------------------------------------------------- publishing
//
// `./gradlew publishMods` uploads this node's jar, one per loader, to every platform that has a
// project id in stonecutter.properties.toml. `-PdryRun` goes through it all without uploading and
// needs no tokens; a real run needs the release commit tagged v<mod.version>, a clean checkout and
// the tokens. RELEASING.md has the steps.
//
// Tokens never live in the repository: they come from the environment (MODRINTH_TOKEN,
// CURSEFORGE_TOKEN; in CI from its secrets) or from a git-ignored .env file in the project root.

val isDryRun = providers.gradleProperty("dryRun").isPresent
val dotEnv: Map<String, String> = rootProject.file(".env").takeIf { it.isFile }?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
    ?.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().removeSurrounding("\"") }
    ?: emptyMap()

fun secret(name: String): Provider<String> =
    providers.environmentVariable(name).orElse(providers.provider { dotEnv[name]?.takeIf { it.isNotBlank() } })

/** A platform's project id, or a stand-in on a dry run so that the whole configuration is exercised. */
fun projectId(name: String): String = prop(name).ifBlank { if (isDryRun) "dry-run" else "" }

val modrinthId = projectId("publish.modrinth")
val curseforgeId = projectId("publish.curseforge")
val loaderName = if (loader == "fabric") "Fabric" else "NeoForge"
// The optional integrations, by each platform's slug for them.
val modrinthOptional = listOf("jei", "rei", "jade", "modonomicon")
val curseforgeOptional = listOf("jei", "roughly-enough-items", "jade", "modonomicon")

publishMods {
    file = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    version = "$modVersion+$mcVersion-$loader"
    displayName = "$modName $modVersion for $loaderName $mcVersion"
    modLoaders.add(loader)
    type = when (prop("publish.type")) {
        "beta" -> BETA
        "alpha" -> ALPHA
        else -> STABLE
    }
    changelog = providers.provider { changelogFor(modVersion) }
    dryRun = isDryRun

    if (modrinthId.isNotEmpty()) {
        modrinth {
            projectId = modrinthId
            accessToken = secret("MODRINTH_TOKEN")
            minecraftVersions.add(mcVersion)
            if (loader == "fabric") {
                requires("fabric-api")
            }
            optional(*modrinthOptional.toTypedArray())
        }
    }
    if (curseforgeId.isNotEmpty()) {
        curseforge {
            projectId = curseforgeId
            accessToken = secret("CURSEFORGE_TOKEN")
            minecraftVersions.add(mcVersion)
            javaVersions.add(JavaVersion.toVersion(javaVersion))
            client = true
            server = true
            if (loader == "fabric") {
                requires("fabric-api")
            }
            optional(*curseforgeOptional.toTypedArray())
        }
    }
}

/** The section of CHANGELOG.md under "## <version>", which is what each platform shows for the release. */
fun changelogFor(version: String): String {
    val lines = rootProject.file("CHANGELOG.md").readLines()
    val start = lines.indexOfFirst { it.trim() == "## $version" || it.trim().startsWith("## $version ") }
    check(start >= 0) { "CHANGELOG.md has no \"## $version\" section" }
    val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
    return lines.subList(start + 1, end).joinToString("\n").trim()
}

// Before anything is uploaded for real: the jar is built and tested, the commit is the tagged
// release of this version, nothing is left uncommitted, and there is somewhere to publish to.
val checkRelease = tasks.register("checkRelease") {
    group = "publishing"
    description = "Checks the checkout is the tagged, clean release of this version before publishing."
    dependsOn("build")
    val tags = providers.exec { commandLine("git", "tag", "--points-at", "HEAD") }.standardOutput.asText
    val status = providers.exec { commandLine("git", "status", "--porcelain") }.standardOutput.asText
    val platforms = modrinthId.isNotEmpty() || curseforgeId.isNotEmpty()
    val version = modVersion
    doLast {
        check(platforms) { "No platform to publish to: set publish.modrinth or publish.curseforge in stonecutter.properties.toml" }
        check("v$version" in tags.get().lines().map { it.trim() }) {
            "HEAD is not tagged v$version. Tag the release commit first: git tag -a v$version -m \"Kolobok $version\""
        }
        check(status.get().isBlank()) { "The checkout has uncommitted changes; publish from a clean one" }
        changelogFor(version)
    }
}

if (!isDryRun) {
    tasks.named("publishMods") { dependsOn(checkRelease) }
    tasks.withType<me.modmuss50.mpp.PublishModTask>().configureEach { dependsOn(checkRelease) }
}
