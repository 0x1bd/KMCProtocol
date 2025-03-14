package de.kvxd.kmcprotocol.serialization

import de.kvxd.kmcprotocol.MinecraftPacket
import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.datatypes.UuidSerializer
import de.kvxd.kmcprotocol.datatypes.VarInt
import de.kvxd.kmcprotocol.datatypes.component.ComponentSerializer
import io.ktor.utils.io.core.*
import kotlinx.io.readByteArray
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.serializer


object PacketSerializer {

    val serializersModule = SerializersModule {
        contextual(UuidSerializer)
        contextual(ComponentSerializer)
    }

    inline fun <reified T : MinecraftPacket> serialize(protocol: MinecraftProtocol, packet: T): ByteArray {
        val encoder = MinecraftPacketEncoder()

        val id = protocol.registry.getPacketID(packet::class)

        // Write packet ID first
        encoder.encodeVarInt(id)

        // Serialize the packet content
        serializer<T>().serialize(encoder, packet)

        // Get the content bytes
        val contentBytes = encoder.getBytes()

        // Create the final packet with length
        val packetBytes = buildPacket {
            writeFully(VarInt.encode(contentBytes.size))
            writeFully(contentBytes)
        }

        return packetBytes.readByteArray()
    }

    @OptIn(InternalSerializationApi::class)
    fun deserialize(protocol: MinecraftProtocol, bytes: ByteArray): MinecraftPacket? {
        val packet = ByteReadPacket(bytes)
        val length = VarInt.decode(packet)
        val packetId = VarInt.decode(packet)

        val packetClass = protocol.registry.getPacketClassById(packetId).serializer()

        val decoder = MinecraftPacketDecoder(packet)
        val p = packetClass.deserialize(decoder)

        if (protocol.registry.getPacketMetadata(p::class).state != protocol.state) {
            return null
        }

        return p
    }
}