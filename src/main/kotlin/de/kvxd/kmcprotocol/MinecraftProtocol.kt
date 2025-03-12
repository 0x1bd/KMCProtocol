package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.registry.PacketRegistry

class MinecraftProtocol {

    var state: ProtocolState = ProtocolState.HANDSHAKE

    var registry = PacketRegistry.create(this) { }

}