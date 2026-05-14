package org.kvxd.kmcprotocol.core.format.impl

import org.kvxd.kmcprotocol.core.KmcPacketDecodeException
import org.kvxd.kmcprotocol.core.KmcPacketEncodeException
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import org.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import org.kvxd.kmcprotocol.core.format.PacketFormat
import org.kvxd.kmcprotocol.core.encoding.readVarInt
import org.kvxd.kmcprotocol.core.encoding.writeVarInt
import org.kvxd.kmcprotocol.network.Direction
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer

/**
 * Legacy uncompressed Minecraft packet format.
 */
class Uncompressed(private val data: ProtocolData) : PacketFormat {

    override suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel) {
        val metadata = data.registry.getPacketMetadata(data.state, packet)
            ?: throw KmcPacketEncodeException(
                packetClass = packet::class,
                state = data.state,
                details = "No packet registration exists for this packet class in state ${data.state}.",
            )

        val content = ByteChannel().apply {
            writeVarInt(metadata.id)

            @Suppress("UNCHECKED_CAST")
            val serializer = metadata.serializer as KSerializer<MinecraftPacket>
            serializer.serialize(MinecraftEncoder(data, this), packet)

            flush()
            close()
        }

        val contentBytes = content.readRemaining().readByteArray()

        channel.writeVarInt(contentBytes.size)

        channel.writeFully(contentBytes)
        channel.flush()
    }

    override suspend fun receive(channel: ByteReadChannel, expectedDirection: Direction): MinecraftPacket? {
        val length = channel.readVarInt()
        val packetBytes = ByteArray(length)
        channel.readFully(packetBytes)

        val packetChannel = ByteReadChannel(packetBytes)
        val id = packetChannel.readVarInt()

        val serializer = data.registry.getPacketSerializerById(data.state, id, expectedDirection)

        return serializer?.deserialize(MinecraftDecoder(data, packetChannel))
            ?: throw KmcPacketDecodeException(
                state = data.state,
                direction = expectedDirection,
                packetId = id,
                frameLength = length,
                details = "No packet serializer is registered for packet id 0x${id.toString(16)} in ${data.state}/$expectedDirection.",
            )
    }

}
