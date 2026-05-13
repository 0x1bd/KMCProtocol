package org.kvxd.kmcprotocol.core.format.impl

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

class Uncompressed(private val data: ProtocolData) : PacketFormat {

    override suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel) {
        val metadata = data.registry.getPacketMetadata(data.state, packet) ?: error("Packet not registered")

        val content = ByteChannel().apply {
            writeVarInt(metadata.id)

            val serializer = data.registry.getPacketSerializerByClass(data.state, packet::class)
            serializer!!.serialize(MinecraftEncoder(data, this), packet)

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
    }

}
