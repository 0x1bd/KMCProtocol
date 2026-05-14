plugins {
    alias(libs.plugins.fabric.loom)
}

group = "org.kvxd"
version = "1.0-SNAPSHOT"

base {
    archivesName.set("kmcprotocol-fabric-testmod")
}

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    mappings("net.fabricmc:yarn:${libs.versions.yarn.get()}:v2")
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

loom {
    runs {
        named("server") {
            server()
            runDir = providers.gradleProperty("kmcFabricServerRunDir").orElse("run").get()
            programArgs("--nogui", "--port", providers.gradleProperty("kmcFabricServerPort").orElse("25567").get())
        }
    }
}
