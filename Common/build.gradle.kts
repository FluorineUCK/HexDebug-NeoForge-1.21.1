plugins {
    id("hexdebug.conventions.architectury")
}

architectury {
    common("neoforge") {
        platformPackage("neoforge", "forge")
    }
}

kotlin {
    sourceSets.named("main") {
        kotlin.exclude("gay/object/hexdebug/datagen/**")
    }
}

val hexcastingNeoForgeJar = rootProject.file("libs/hexcasting-neoforge-1.21.1-0.12.0-devel-pre-39.jar")

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(kotlin("reflect"))

    modApi(libs.architectury)

    compileOnly(files(hexcastingNeoForgeJar))

    modApi(libs.clothConfig.common)

    libs.mixinExtras.common.also {
        implementation(it)
        annotationProcessor(it)
    }

    implementation(libs.bundles.lsp4j)

    implementation(libs.bundles.ktor)

    modCompileOnly(libs.emi.xplat)

    api(project(":hexdebug-core-common", "namedElements"))
}
