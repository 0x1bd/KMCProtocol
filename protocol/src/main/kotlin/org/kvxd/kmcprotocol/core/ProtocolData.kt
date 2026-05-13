package org.kvxd.kmcprotocol.core

import org.kvxd.kmcprotocol.core.format.impl.Uncompressed
import kotlinx.serialization.modules.EmptySerializersModule

class ProtocolData {

    val serializersModule = EmptySerializersModule()

    @Volatile
    var state = ProtocolState.Handshake

    var registry = PacketRegistry().also(PacketRegistry::initializePacketRegistry)

    var format = Uncompressed(this)

}