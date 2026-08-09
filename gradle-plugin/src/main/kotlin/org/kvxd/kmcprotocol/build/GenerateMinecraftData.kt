package org.kvxd.kmcprotocol.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GenerateMinecraftData : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataDirectory: DirectoryProperty

    @get:Input
    abstract val minecraftVersions: ListProperty<String>

    @get:Input
    abstract val runtimePackage: Property<String>

    @get:Input
    abstract val dataPackage: Property<String>

    @get:Input
    abstract val chunkSize: Property<Int>

    @get:Input
    abstract val generateNamedAccessors: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val root = dataDirectory.get().asFile
        minecraftVersions.get().forEach { version ->
            val spec = MinecraftDataSpec(
                minecraftVersion = version,
                blocksJson = root.dataPath(version, "blocks"),
                itemsJson = root.dataPath(version, "items"),
                entitiesJson = root.dataPath(version, "entities"),
            )

            val versionPackage = "${dataPackage.get()}.v${version.sanitizeVersion()}"
            val model = MinecraftDataParser(spec).parse()
            val report = MinecraftDataEmitter(
                runtimePackage = runtimePackage.get(),
                dataPackage = versionPackage,
                outputDirectory = output.toPath(),
                chunkSize = chunkSize.get(),
            ).emit(model)

            logger.lifecycle(
                "Generated ${report.blockCount} blocks (${report.blockStateCount} states), " +
                    "${report.itemCount} items and ${report.entityCount} entities for Minecraft $version"
            )

            if (!generateNamedAccessors.get()) return@forEach

            val accessors = MinecraftAccessorEmitter(
                runtimePackage = runtimePackage.get(),
                dataPackage = versionPackage,
                outputDirectory = output.toPath(),
            ).emit(model)

            logger.lifecycle(
                "  named accessors: ${report.blockCount + report.itemCount + report.entityCount} constants, " +
                    "${accessors.enumCount} state enums, ${accessors.stateFunctionCount} state functions"
            )
        }
    }

    private fun File.dataPath(version: String, dataSet: String) =
        dataFile(version, dataSet).takeIf(File::isFile)?.toPath()
}
