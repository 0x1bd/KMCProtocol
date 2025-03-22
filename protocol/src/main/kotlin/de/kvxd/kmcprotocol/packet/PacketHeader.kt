package de.kvxd.kmcprotocol.packet

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import io.ktor.utils.io.*
import kotlinx.io.readByteArray

interface PacketHeader {

    suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel, protocol: MinecraftProtocol)
    suspend fun receive(channel: ByteReadChannel, protocol: MinecraftProtocol): MinecraftPacket?

    object Uncompressed : PacketHeader {

        override suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel, protocol: MinecraftProtocol) {
            val (packetId, codec) = protocol.registry.getPacketData(packet)

            val content = ByteChannel(autoFlush = false).apply {
                VarIntCodec.encode(this, packetId)
                codec.encode(packet, this)
                flush()
                close()
            }

            val contentBytes = content.readRemaining().readByteArray()

            VarIntCodec.encode(channel, contentBytes.size)
            channel.writeFully(contentBytes)
            channel.flush()
        }

        override suspend fun receive(channel: ByteReadChannel, protocol: MinecraftProtocol): MinecraftPacket? {
            val length = VarIntCodec.decodeOrNull(channel)
            val id = VarIntCodec.decodeOrNull(channel)

            if (length == null || id == null)
                return null

            val (codec, metadata) = protocol.registry.getPacketDataById(id)

            return codec.decode(channel)
        }
    }

}