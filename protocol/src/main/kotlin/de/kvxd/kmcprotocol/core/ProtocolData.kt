package de.kvxd.kmcprotocol.core

import de.kvxd.kmcprotocol.core.format.impl.Uncompressed
import kotlinx.serialization.modules.EmptySerializersModule

class ProtocolData {

    val serializersModule = EmptySerializersModule()

    var state = ProtocolState.Handshake

    var registry = PacketRegistry().also(PacketRegistry::initializePacketRegistry)

    var format = Uncompressed(this)

}