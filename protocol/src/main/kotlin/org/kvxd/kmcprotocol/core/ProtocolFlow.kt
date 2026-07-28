package org.kvxd.kmcprotocol.core

interface HandshakeIntentPacket : MinecraftPacket {
    val nextProtocolStateId: Int
}

interface CompressionSetupPacket : MinecraftPacket {
    val compressionThreshold: Int
}

interface StateTransitionPacket : MinecraftPacket {
    val targetProtocolState: ProtocolState
}
