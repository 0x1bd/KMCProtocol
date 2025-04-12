package de.kvxd.kmcprotocol.core.format.impl

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import de.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import de.kvxd.kmcprotocol.core.format.PacketFormat
import de.kvxd.kmcprotocol.core.variant.readVarInt
import de.kvxd.kmcprotocol.core.variant.writeVarInt
import de.kvxd.kmcprotocol.network.Direction
import io.ktor.utils.io.core.*
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray

class Uncompressed(private val data: ProtocolData) : PacketFormat {

    override fun send(packet: MinecraftPacket, sink: Sink) {
        val metadata = data.registry.getPacketMetadata(data.state, packet) ?: error("Packet not registered")

        val content = Buffer().apply {
            writeVarInt(metadata.id)

            // serialize the actual packet using the registry
            val serializer = data.registry.getPacketSerializerByClass(data.state, packet::class)
            serializer!!.serialize(MinecraftEncoder(data, this), packet)

            flush()
            close()
        }

        sink.writeVarInt(content.size.toInt())

        sink.writeFully(content.readByteArray())
        sink.flush()
    }

    override fun receive(source: Source, expectedDirection: Direction): MinecraftPacket? {
        val length = source.readVarInt()
        val id = source.readVarInt()

        val serializer = data.registry.getPacketSerializerById(data.state, id, expectedDirection)

        return serializer!!.deserialize(MinecraftDecoder(data, source))
    }

}
