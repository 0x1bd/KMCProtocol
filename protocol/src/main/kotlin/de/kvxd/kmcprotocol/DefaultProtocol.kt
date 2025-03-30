package de.kvxd.kmcprotocol

import de.kvxd.kmcprotocol.packets.handshake.serverbound.ServerboundHandshakePacket
import de.kvxd.kmcprotocol.packets.login.clientbound.ClientboundEncryptionRequestPacket
import de.kvxd.kmcprotocol.packets.login.serverbound.ServerboundEncryptionResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.ClientboundPongResponsePacket
import de.kvxd.kmcprotocol.packets.status.clientbound.ClientboundStatusResponsePacket
import de.kvxd.kmcprotocol.packets.status.serverbound.ServerboundPingRequestPacket
import de.kvxd.kmcprotocol.packets.status.serverbound.ServerboundStatusRequestPacket

const val LATEST_PROTOCOL_NAME = "1.21.4"
const val LATEST_PROTOCOL_VERSION = 769

fun defaultProtocol() = MinecraftProtocol {
    registerPacket(ServerboundHandshakePacket::class, ServerboundHandshakePacket.CODEC)
    registerPacket(ClientboundPongResponsePacket::class, ClientboundPongResponsePacket.CODEC)
    registerPacket(ClientboundStatusResponsePacket::class, ClientboundStatusResponsePacket.CODEC)
    registerPacket(ServerboundPingRequestPacket::class, ServerboundPingRequestPacket.CODEC)
    registerPacket(ServerboundStatusRequestPacket::class, ServerboundStatusRequestPacket.CODEC)
    registerPacket(ClientboundEncryptionRequestPacket::class, ClientboundEncryptionRequestPacket.CODEC)
    registerPacket(ServerboundEncryptionResponsePacket::class, ServerboundEncryptionResponsePacket.CODEC)
}