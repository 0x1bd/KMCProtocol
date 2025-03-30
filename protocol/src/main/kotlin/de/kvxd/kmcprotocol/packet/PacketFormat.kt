package de.kvxd.kmcprotocol.packet

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import io.ktor.utils.io.*
import kotlinx.io.readByteArray

interface PacketFormat {

    suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel, protocol: MinecraftProtocol)
    suspend fun receive(
        channel: ByteReadChannel,
        protocol: MinecraftProtocol,
        expectedDirection: Direction
    ): MinecraftPacket?

}