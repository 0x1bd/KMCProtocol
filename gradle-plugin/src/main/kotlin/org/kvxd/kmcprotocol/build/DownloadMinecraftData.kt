package org.kvxd.kmcprotocol.build

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI

abstract class DownloadMinecraftData : DefaultTask() {

    @get:Input
    abstract val minecraftVersions: ListProperty<String>

    @get:Input
    abstract val refreshProtocolData: Property<Boolean>

    @get:Input
    abstract val dataBaseUrl: Property<String>

    @get:Input
    abstract val dataSets: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val vendoredDataDirectory: Property<String>

    @get:OutputDirectory
    abstract val dataDirectory: DirectoryProperty

    @TaskAction
    fun download() {
        val versions = minecraftVersions.get()
        require(versions.isNotEmpty()) {
            "kmcProtocol.minecraftVersions is empty. Set at least one Minecraft version to generate packets for."
        }

        val root = dataDirectory.get().asFile
        val refresh = refreshProtocolData.get()
        val vendored = vendoredDataDirectory.orNull?.let(::File)

        val incomplete = versions.filter { version ->
            refresh || missingDataSets(root, version, vendored).isNotEmpty()
        }
        if (incomplete.isEmpty()) {
            logger.lifecycle("minecraft-data is cached for ${versions.joinToString()}")
            return
        }

        val dataPaths = fetchDataPaths()
        incomplete.forEach { version -> downloadVersion(version, dataPaths, root, refresh) }
    }

    private fun requiredDataSets(): List<String> =
        listOf(ProtocolDataSet, VersionDataSet) + dataSets.get().filter { it != ProtocolDataSet && it != VersionDataSet }

    private fun missingDataSets(root: File, version: String, vendored: File?): List<String> =
        requiredDataSets().filter { dataSet ->
            if (root.dataFile(version, dataSet).isFile) return@filter false

            val source = vendored?.dataFile(version, dataSet)?.takeIf(File::isFile) ?: return@filter true
            source.copyTo(root.dataFile(version, dataSet).ensureParent(), overwrite = true)
            logger.lifecycle("Using vendored minecraft-data $dataSet for $version")
            false
        }

    private fun downloadVersion(version: String, dataPaths: JsonObject, root: File, refresh: Boolean) {
        val paths = dataPaths[version]?.jsonObject
            ?: throw IllegalArgumentException(
                "Unknown Minecraft version '$version'. Versions with protocol data: " +
                    dataPaths.keys.filter { dataPaths[it]?.jsonObject?.containsKey(ProtocolDataSet) == true }
                        .joinToString()
            )

        requiredDataSets().forEach { dataSet ->
            val target = root.dataFile(version, dataSet)
            if (target.isFile && !refresh) return@forEach

            val directory = paths[dataSet]?.jsonPrimitive?.content
            if (directory == null) {
                if (dataSet == ProtocolDataSet) {
                    throw IllegalArgumentException("minecraft-data has no protocol.json for Minecraft '$version'.")
                }
                logger.warn("minecraft-data has no $dataSet.json for Minecraft $version, skipping it.")
                return@forEach
            }

            logger.lifecycle("Downloading minecraft-data $dataSet for $version")
            copyUrl("${dataBaseUrl.get()}/$directory/$dataSet.json", target)
        }
    }

    private fun fetchDataPaths(): JsonObject {
        val url = "${dataBaseUrl.get()}/dataPaths.json"
        val text = URI(url).toURL().openStream().use { it.reader().readText() }
        return Json.parseToJsonElement(text).jsonObject["pc"]?.jsonObject
            ?: throw IllegalStateException("minecraft-data dataPaths.json did not contain a 'pc' section.")
    }

    private fun copyUrl(url: String, target: File) {
        URI(url).toURL().openStream().use { input ->
            target.ensureParent().outputStream().use(input::copyTo)
        }
    }

    private companion object {
        const val ProtocolDataSet = "protocol"
        const val VersionDataSet = "version"
    }
}

internal fun File.dataFile(version: String, dataSet: String): File = resolve("pc/$version/$dataSet.json")

internal fun File.protocolFile(version: String): File = dataFile(version, "protocol")

internal fun File.versionFile(version: String): File = dataFile(version, "version")

private fun File.ensureParent(): File = apply { parentFile.mkdirs() }
