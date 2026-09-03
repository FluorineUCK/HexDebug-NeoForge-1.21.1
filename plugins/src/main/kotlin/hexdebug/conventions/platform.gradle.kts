package hexdebug.conventions

plugins {
    id("hexdebug.conventions.platform-base")
}

val platform: String by project

architectury {
    platformSetupLoomIde()
}

dependencies {
    // include, not shadow
    localRuntime(project(":hexdebug-core-common", "namedElements"))
    project(":hexdebug-core-$platform", "namedElements").also {
        localRuntime(it)
        compileOnly(it)
    }
    include(project(":hexdebug-core-$platform"))
}

tasks {
    processIncludeJars {

    }
}

