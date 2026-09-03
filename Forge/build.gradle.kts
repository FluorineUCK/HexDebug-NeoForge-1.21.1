import hexdebug.utils.kotlinForgeRuntimeLibrary
import org.gradle.jvm.tasks.Jar

plugins {
    id("hexdebug.conventions.platform")
    id("hexdebug.utils.kotlin-forge-runtime-library")
}

architectury {
    neoForge {
        platformPackage = "forge"
    }
}

kotlin {
    sourceSets.named("main") {
        kotlin.exclude("gay/object/hexdebug/forge/datagen/**")
    }
}

loom {
    runs {
        for ((name, outputProject) in arrayOf(
            // we're using forge to do the common datagen because fabric's datagen kind of sucks
            "common" to project(":Common"),
            "forge" to project,
        )) {
            register("${name}Datagen") {
                data()
                programArgs(
                    "--mod", hexdebugProperties.modId,
                    "--all",
                    "--output", outputProject.file("src/generated/resources").absolutePath,
                    "--existing", file("src/main/resources").absolutePath,
                    "--existing", project(":Common").file("src/main/resources").absolutePath,
                    "--existing-mod", "hexcasting",
                )
                property("hexdebug.apply-datagen-mixin", "true")
                property("hexdebug.$name-datagen", "true")
            }
        }
    }
}

hexdebugModDependencies {
    filesMatching.add("META-INF/neoforge.mods.toml")

    anyVersion = ""
    mapVersions {
        replace(Regex("""\](\S+)"""), "($1")
        replace(Regex("""(\S+)\["""), "$1)")
    }
}

val hexcastingNeoForgeJar = rootProject.file("libs/hexcasting-neoforge-1.21.1-0.12.0-devel-pre-39.jar")
val hexcastingNeoForgeRuntimeJar = rootProject.file("libs/hexcasting-neoforge-1.21.1-0.12.0-devel-pre-39.jar")
val paucalNeoForgeJar = rootProject.file("libs/paucal-0.7.1-pre-27+1.21.1-neoforge.jar")
val inlineNeoForgeJar = rootProject.file("libs/inline-1.21.1-1.2.2-neoforge-devruntime.jar")
val caelusNeoForgeJar = rootProject.file("libs/caelus-neoforge-7.0.1+1.21.1.jar")
val lsp4jRuntimeJars = files(
    rootProject.file("libs/org.eclipse.lsp4j-0.23.0-unsigned.jar"),
    rootProject.file("libs/org.eclipse.lsp4j.debug-0.23.0-unsigned.jar"),
    rootProject.file("libs/org.eclipse.lsp4j.jsonrpc-0.23.0-unsigned.jar"),
    rootProject.file("libs/org.eclipse.lsp4j.jsonrpc.debug-0.23.0-unsigned.jar"),
)

val enableTagProbe = providers.gradleProperty("hexdebugTagProbe").isPresent
val enableRuntimeProbe = providers.gradleProperty("hexdebugRuntimeProbe").isPresent
val enableClientProbe = providers.gradleProperty("hexdebugClientProbe").isPresent

loom {
    if (enableTagProbe) {
        runs.named("server") {
            property("hexdebug.probe.validateTags", "true")
        }
    }
    if (enableRuntimeProbe) {
        runs.named("server") {
            property("hexdebug.probe.validateRuntime", "true")
        }
    }
    if (enableClientProbe) {
        runs.named("client") {
            property("hexdebug.probe.validateClient", "true")
            property("hexdebug.probe.exitAfterClientStartup", "true")
            programArgs("--quickPlaySingleplayer", "HexDebugProbePre2")
        }
    }
}

dependencies {
    neoForge(libs.neoforge)
    modApi(libs.architectury.neoforge)

    runtimeOnly(libs.kotlin.forge)

    compileOnly(files(hexcastingNeoForgeJar))
    runtimeOnly(files(hexcastingNeoForgeRuntimeJar))
    compileOnly(files(paucalNeoForgeJar))
    runtimeOnly(files(paucalNeoForgeJar))
    compileOnly(libs.patchouli.neoforge)
    runtimeOnly(libs.patchouli.neoforge)
    runtimeOnly(files(caelusNeoForgeJar))
    runtimeOnly(files(inlineNeoForgeJar))

    modApi(libs.clothConfig.neoforge)

    libs.mixinExtras.common.also {
        compileOnly(it)
        annotationProcessor(it)
    }

    libs.mixinExtras.neoforge.also {
        implementation(it)
        include(it)
    }

    libs.bundles.lsp4j.also {
        api(it)
        include(it)
    }

    runtimeOnly(lsp4jRuntimeJars)
    localRuntime(lsp4jRuntimeJars)
    add("developmentNeoForge", lsp4jRuntimeJars)

    libs.bundles.ktor.also {
        implementation(it)
        include(it)
        kotlinForgeRuntimeLibrary(it)
    }

    libs.emi.neoforge.also {
        modCompileOnly(it)
        runtimeOnly(it)
    }

}

tasks.named<Jar>("shadowJar") {
    exclude("gay/object/hexdebug/forge/probe/**")
}
