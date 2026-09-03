// minimal set of classes required to implement debug support in another mod
// this is intended to be included via JiJ to allow HexDebug to be an optional dependency

plugins {
    id("hexdebug.conventions.architectury")
}

architectury {
    common("neoforge") {
        platformPackage("neoforge", "forge")
    }
}

val hexcastingNeoForgeJar = rootProject.file("libs/hexcasting-neoforge-1.21.1-0.12.0-devel-pre-39.jar")

dependencies {
    compileOnly(files(hexcastingNeoForgeJar))
}
