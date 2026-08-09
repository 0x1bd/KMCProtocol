package org.kvxd.kmcprotocol.core

import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.kvxd.kmcprotocol.core.encoding.NbtFormat
import org.kvxd.kmcprotocol.core.encoding.ProtocolCodec
import org.kvxd.kmcprotocol.data.MinecraftData
import org.kvxd.kmcprotocol.data.MinecraftDataException
import org.kvxd.kmcprotocol.data.MinecraftDataSource

class ProtocolVersion(
    val minecraftVersion: String,
    val protocolVersion: Int,
    val protocolResourcePath: String,
    val registrySource: PacketRegistrySource,
    val dataSource: MinecraftDataSource? = null,
) {

    val codec: ProtocolCodec by lazy { ProtocolCodec.fromResource(protocolResourcePath) }

    val minecraftData: MinecraftData? get() = dataSource?.data

    fun requireMinecraftData(): MinecraftData = minecraftData ?: throw MinecraftDataException(
        "No generated block, item and entity data for Minecraft $minecraftVersion. " +
            "Enable it with kmcProtocol { generateMinecraftData.set(true) }."
    )

    val nbtFormat: NbtFormat = NbtFormat.forMinecraftVersion(minecraftVersion)

    val packetRegistry: PacketRegistry by lazy { newPacketRegistry() }

    fun newPacketRegistry(): PacketRegistry = PacketRegistry(listOf(registrySource))

    fun protocolData(
        initialState: ProtocolState = ProtocolState.Handshake,
        automaticProtocolFlow: Boolean = true,
        serializersModule: SerializersModule = EmptySerializersModule(),
        registry: PacketRegistry = packetRegistry,
        limits: ProtocolLimits = ProtocolLimits.Default,
        nbtFormat: NbtFormat = this.nbtFormat,
    ): ProtocolData = ProtocolData(
        initialState = initialState,
        automaticProtocolFlow = automaticProtocolFlow,
        codec = codec,
        serializersModule = serializersModule,
        registry = registry,
        limits = limits,
        nbtFormat = nbtFormat,
    )

    override fun toString(): String = "ProtocolVersion($minecraftVersion, protocol $protocolVersion)"
}

interface ProtocolVersionCatalog {

    val versions: List<ProtocolVersion>

    fun byMinecraftVersion(minecraftVersion: String): ProtocolVersion? =
        versions.firstOrNull { it.minecraftVersion == minecraftVersion }

    fun byProtocolVersion(protocolVersion: Int): ProtocolVersion? =
        allByProtocolVersion(protocolVersion).firstOrNull()

    fun allByProtocolVersion(protocolVersion: Int): List<ProtocolVersion> =
        versions.filter { it.protocolVersion == protocolVersion }

    fun requireMinecraftVersion(minecraftVersion: String): ProtocolVersion =
        byMinecraftVersion(minecraftVersion) ?: throw KmcProtocolException(
            "No generated protocol for Minecraft $minecraftVersion. Generated versions: ${versionSummary()}."
        )

    fun requireProtocolVersion(protocolVersion: Int): ProtocolVersion =
        byProtocolVersion(protocolVersion) ?: throw KmcProtocolException(
            "No generated protocol for protocol version $protocolVersion. Generated versions: ${versionSummary()}."
        )

    private fun versionSummary(): String =
        versions.joinToString { "${it.minecraftVersion} (${it.protocolVersion})" }.ifEmpty { "none" }
}
