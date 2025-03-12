package de.kvxd.kmcprotocol

import kotlinx.serialization.serializer
import java.nio.ByteBuffer
import kotlin.reflect.KClass

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
        val packetBytes = ByteBuffer.allocate(VarInt.encode(contentBytes.size).size + contentBytes.size)
        packetBytes.put(VarInt.encode(contentBytes.size))
        packetBytes.put(contentBytes)
        
        return packetBytes.array()
    }

    inline fun <reified T : Packet> deserialize(bytes: ByteArray): T {
        val buffer = ByteBuffer.wrap(bytes)
        val length = ByteBufferUtils.readVarInt(buffer)
        val packetId = ByteBufferUtils.readVarInt(buffer)
        
        val decoder = MinecraftPacketDecoder(buffer)
        return kotlinx.serialization.serializer<T>().deserialize(decoder)
    }
}