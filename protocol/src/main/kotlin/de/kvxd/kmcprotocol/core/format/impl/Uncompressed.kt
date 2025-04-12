package de.kvxd.kmcprotocol.core.format.impl

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import de.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import de.kvxd.kmcprotocol.core.format.PacketFormat
import de.kvxd.kmcprotocol.core.variant.readVarInt
import de.kvxd.kmcprotocol.core.variant.writeVarInt
import de.kvxd.kmcprotocol.network.Direction
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
        val id = channel.readVarInt()

        val serializer = data.registry.getPacketSerializerById(data.state, id, expectedDirection)

        return serializer?.deserialize(MinecraftDecoder(data, channel))
    }

}
