package org.kvxd.kmcprotocol.core.format

import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.network.Direction
import io.ktor.utils.io.*

/**
 * Encodes and decodes framed Minecraft packets over byte channels.
 */
interface PacketFormat {

    /**
     * Writes a framed packet to the supplied channel.
     */
    suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel)

    /**
     * Reads the next framed packet expected in the supplied direction.
     */
    suspend fun receive(
        channel: ByteReadChannel,
        expectedDirection: Direction
    ): MinecraftPacket?

}
