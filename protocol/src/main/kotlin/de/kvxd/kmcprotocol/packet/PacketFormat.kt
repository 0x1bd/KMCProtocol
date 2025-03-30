package de.kvxd.kmcprotocol.packet

import de.kvxd.kmcprotocol.MinecraftProtocol
import io.ktor.utils.io.*

interface PacketFormat {

    suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel, protocol: MinecraftProtocol)
    suspend fun receive(
        channel: ByteReadChannel,
        protocol: MinecraftProtocol,
        expectedDirection: Direction
    ): MinecraftPacket?

}