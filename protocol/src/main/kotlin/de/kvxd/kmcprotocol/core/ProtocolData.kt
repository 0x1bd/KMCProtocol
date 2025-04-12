package de.kvxd.kmcprotocol.core

import de.kvxd.kmcprotocol.core.format.impl.Uncompressed
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule

class ProtocolData {

    val serializersModule = EmptySerializersModule()
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var state = ProtocolState.Handshake

    var registry = PacketRegistry().also(PacketRegistry::initializePacketRegistry)

    var format = Uncompressed(this)

}