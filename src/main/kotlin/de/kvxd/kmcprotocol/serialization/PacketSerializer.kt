package de.kvxd.kmcprotocol.serialization

import de.kvxd.kmcprotocol.MinecraftTypes
import de.kvxd.kmcprotocol.Packet
import de.kvxd.kmcprotocol.datatypes.varint.VarInt
import io.ktor.utils.io.core.*
import kotlinx.serialization.serializer


object PacketSerializer {
    inline fun <reified T : Packet> serialize(packet: T): ByteArray {
        val encoder = MinecraftPacketEncoder()

        // Write packet ID first
        encoder.writeBytes(VarInt.encode(packet.packetId))

        // Serialize the packet content
        serializer<T>().serialize(encoder, packet)

        // Get the content bytes
        val contentBytes = encoder.getBytes()

        // Create the final packet with length
        val packetBytes = buildPacket {
            writeFully(VarInt.encode(contentBytes.size))
            writeFully(contentBytes)
        }

        return packetBytes.readBytes()
    }

    inline fun <reified T : Packet> deserialize(bytes: ByteArray): T {
        val packet = ByteReadPacket(bytes)
        val length = MinecraftTypes.readVarInt(packet)
        val packetId = MinecraftTypes.readVarInt(packet)

        val decoder = MinecraftPacketDecoder(packet)
        return kotlinx.serialization.serializer<T>().deserialize(decoder)
    }
}