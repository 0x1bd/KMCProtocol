package de.kvxd.kmcprotocol.packet.format

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketFormat
import io.ktor.utils.io.*
import kotlinx.io.readByteArray

object Uncompressed : PacketFormat {

        override suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel, protocol: MinecraftProtocol) {
            val (codec, metadata) = protocol.registry.getPacketData(packet)

            val content = ByteChannel(autoFlush = false).apply {
                VarIntCodec.encode(this, metadata.id)
                codec.encode(packet, this)
                flush()
                close()
            }

            val contentBytes = content.readRemaining().readByteArray()

            VarIntCodec.encode(channel, contentBytes.size)
            channel.writeFully(contentBytes)
            channel.flush()
        }

        override suspend fun receive(
            channel: ByteReadChannel,
            protocol: MinecraftProtocol,
            expectedDirection: Direction
        ): MinecraftPacket? {
            val length = VarIntCodec.decodeOrNull(channel)
            val id = VarIntCodec.decodeOrNull(channel)

            if (length == null || id == null)
                return null

            val (codec, metadata) = protocol.registry.getPacketDataById(id, expectedDirection, protocol.state)

            return codec.decode(channel)
        }
    }