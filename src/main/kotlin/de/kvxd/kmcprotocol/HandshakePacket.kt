package de.kvxd.kmcprotocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HandshakePacket(
    @Serializable(with = VarIntSerializer::class)
    val protocolVersion: Int,
    val serverAddress: String,
    val serverPort: Short,
    @Serializable(with = VarIntSerializer::class)
    val nextState: Int
) : Packet {
    override val packetId: Int = 0x00
}