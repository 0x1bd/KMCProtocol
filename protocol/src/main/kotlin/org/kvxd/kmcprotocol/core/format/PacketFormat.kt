package org.kvxd.kmcprotocol.core.format

import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.network.Direction
import io.ktor.utils.io.*

interface PacketFormat {

    suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel)
    suspend fun receive(
        channel: ByteReadChannel,
        expectedDirection: Direction
    ): MinecraftPacket?

}