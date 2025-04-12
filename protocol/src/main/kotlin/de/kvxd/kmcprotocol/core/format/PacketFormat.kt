package de.kvxd.kmcprotocol.core.format

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.network.Direction
import io.ktor.utils.io.*

interface PacketFormat {

    suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel)
    suspend fun receive(
        channel: ByteReadChannel,
        expectedDirection: Direction
    ): MinecraftPacket?

}