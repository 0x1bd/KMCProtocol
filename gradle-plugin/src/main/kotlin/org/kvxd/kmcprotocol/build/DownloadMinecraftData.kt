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

        val missing = versions.filter { version ->
            refresh || !cachedFilesPresent(root, version, vendored)
        }
        if (missing.isEmpty()) {
            logger.lifecycle("minecraft-data is cached for ${versions.joinToString()}")
            return
        }

        val dataPaths = fetchDataPaths()
        missing.forEach { version -> downloadVersion(version, dataPaths, root, vendored) }
    }

    private fun cachedFilesPresent(root: File, version: String, vendored: File?): Boolean {
        if (root.protocolFile(version).isFile && root.versionFile(version).isFile) return true
        val source = vendored ?: return false
        if (!source.protocolFile(version).isFile || !source.versionFile(version).isFile) return false

        source.protocolFile(version).copyTo(root.protocolFile(version).ensureParent(), overwrite = true)
        source.versionFile(version).copyTo(root.versionFile(version).ensureParent(), overwrite = true)
        logger.lifecycle("Using vendored minecraft-data for $version")
        return true
    }

    private fun downloadVersion(version: String, dataPaths: JsonObject, root: File, vendored: File?) {
        val paths = dataPaths[version]?.jsonObject
            ?: throw IllegalArgumentException(
                "Unknown Minecraft version '$version'. Versions with protocol data: " +
                    dataPaths.keys.filter { dataPaths[it]?.jsonObject?.containsKey("protocol") == true }.joinToString()
            )

        val protocolDir = paths["protocol"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("minecraft-data has no protocol.json for Minecraft '$version'.")
        val versionDir = paths["version"]?.jsonPrimitive?.content

        logger.lifecycle("Downloading minecraft-data protocol for $version")
        copyUrl("${dataBaseUrl.get()}/$protocolDir/protocol.json", root.protocolFile(version))

        val versionFile = root.versionFile(version)
        if (versionDir != null) {
            copyUrl("${dataBaseUrl.get()}/$versionDir/version.json", versionFile)
        } else {
            vendored?.versionFile(version)?.takeIf { it.isFile }?.copyTo(versionFile.ensureParent(), overwrite = true)
                ?: throw IllegalArgumentException("minecraft-data has no version.json for Minecraft '$version'.")
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
}

internal fun File.protocolFile(version: String): File = resolve("pc/$version/protocol.json")

internal fun File.versionFile(version: String): File = resolve("pc/$version/version.json")

private fun File.ensureParent(): File = apply { parentFile.mkdirs() }
