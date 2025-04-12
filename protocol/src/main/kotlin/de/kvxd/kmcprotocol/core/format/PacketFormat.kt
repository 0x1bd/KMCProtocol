package de.kvxd.kmcprotocol.core.format

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.network.Direction
import kotlinx.io.Sink
import kotlinx.io.Source

interface PacketFormat {

    fun send(packet: MinecraftPacket, sink: Sink)
    fun receive(
        source: Source,
        expectedDirection: Direction
    ): MinecraftPacket?

}