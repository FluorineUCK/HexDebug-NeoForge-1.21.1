package hexdebug.conventions

import hexdebug.hexdebugProperties
import hexdebug.libs

plugins {
    id("hexdebug.conventions.architectury-base")
    id("hexdebug.utils.OTJFPOPKPCPBP")
}

val platform: String by project

base.archivesName = "${hexdebugProperties.modId}-$platform"

loom {
    mixin {
        useLegacyMixinAp.set(true)
        // the default name includes both archivesName and the subproject, resulting in the platform showing up twice
        // default: hexdebug-common-Common-refmap.json
        // fixed:   hexdebug-common.refmap.json
        defaultRefmapName = "${base.archivesName.get()}.refmap.json"
    }
}

dependencies {
    mappings(loom.layered {
        officialMojangMappings()
    })
}

// Architectury Plugin 3.4.164 injects net.fabricmc:fabric-loader:+ into the
// common-source transformer classpath. Pin only that build-time dependency so
// Gradle does not need remote Maven metadata to choose a version.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (
            requested.group == "net.fabricmc"
            && requested.name == "fabric-loader"
            && requested.version == "+"
        ) {
            useVersion("0.16.14")
            because("avoid dynamic Architectury transformer metadata resolution")
        }
    }
}

sourceSets {
    main {
        kotlin {
            srcDir(file("src/main/java"))
        }
        resources {
            srcDir(file("src/generated/resources"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
}
