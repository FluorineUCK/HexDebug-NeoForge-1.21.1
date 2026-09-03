import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar

plugins {
    id("hexdebug.conventions.platform-base")
}

architectury {
    neoForge {
        platformPackage = "forge"
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

dependencies {
    neoForge(libs.neoforge)
    compileOnly(files(rootProject.file("libs/hexcasting-neoforge-1.21.1-0.12.0-devel-pre-39.jar")))
    runtimeOnly(files(rootProject.file("libs/hexcasting-neoforge-1.21.1-0.12.0-devel-pre-39.jar")))
}

val coreCommonSourceSets = project(":hexdebug-core-common").extensions.getByType<SourceSetContainer>()

tasks.named<Jar>("jar") {
    dependsOn(project(":hexdebug-core-common").tasks.named("classes"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(coreCommonSourceSets.named("main").map { it.output })
}

tasks.named<Jar>("shadowJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
