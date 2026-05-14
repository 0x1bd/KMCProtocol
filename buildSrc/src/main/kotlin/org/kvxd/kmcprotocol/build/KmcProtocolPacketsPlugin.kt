package org.kvxd.kmcprotocol.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

abstract class KmcProtocolPacketsExtension {
    abstract val minecraftVersion: Property<String>
    abstract val refreshProtocolData: Property<Boolean>
    abstract val basePackage: Property<String>
    abstract val serverboundPrefix: Property<String>
    abstract val clientboundPrefix: Property<String>
    abstract val protocolDataFile: RegularFileProperty
    abstract val outputDirectory: DirectoryProperty
}

class KmcProtocolPacketsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<KmcProtocolPacketsExtension>("kmcProtocolPackets")

        extension.minecraftVersion.convention("1.21.4")
        extension.refreshProtocolData.convention(false)
        extension.basePackage.convention("${project.group}.kmcprotocol")
        extension.serverboundPrefix.convention("Serverbound")
        extension.clientboundPrefix.convention("Clientbound")
        extension.protocolDataFile.convention(
            project.layout.projectDirectory.file("src/main/minecraft-data/pc/${extension.minecraftVersion.get()}/protocol.json")
        )
        extension.outputDirectory.convention(project.layout.buildDirectory.dir("generated/sources/kmcPackets/kotlin"))

        val download = project.tasks.register("downloadMinecraftProtocolData") {
            inputs.property("minecraftVersion", extension.minecraftVersion)
            inputs.property("refreshProtocolData", extension.refreshProtocolData)
            outputs.file(extension.protocolDataFile)

            doLast {
                val output = extension.protocolDataFile.get().asFile
                if (output.exists() && !extension.refreshProtocolData.get()) {
                    project.logger.lifecycle("Using cached minecraft-data protocol source: ${output.relativeTo(project.projectDir)}")
                    return@doLast
                }

                output.parentFile.mkdirs()
                val sourceUrl =
                    "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/${extension.minecraftVersion.get()}/protocol.json"

                project.logger.lifecycle("Downloading minecraft-data protocol source from $sourceUrl")
                sourceUrl.toUrl().openStream().use { input ->
                    output.outputStream().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
            }
        }

        val generate = project.tasks.register<GenerateMinecraftPackets>("generateMinecraftPackets") {
            dependsOn(download)

            protocolDataFile.set(extension.protocolDataFile)
            outputDirectory.set(extension.outputDirectory)
            minecraftVersion.set(extension.minecraftVersion)
            basePackage.set(extension.basePackage)
            serverboundPrefix.set(extension.serverboundPrefix)
            clientboundPrefix.set(extension.clientboundPrefix)
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.tasks.named("compileKotlin") {
                dependsOn(generate)
            }
            project.tasks.named("processResources") {
                dependsOn(download)
            }
        }

        project.pluginManager.withPlugin("java") {
            project.extensions.getByType<SourceSetContainer>().named("main") {
                resources.srcDir("src/main/minecraft-data")
            }
        }
    }

    private fun String.toUrl(): java.net.URL = java.net.URI(this).toURL()
}
