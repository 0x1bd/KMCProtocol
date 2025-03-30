package de.kvxd.kmcprotocol.packet.format

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketFormat
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.readByteArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

class Compressed(private val threshold: Int = 256) : PacketFormat {

    override suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel, protocol: MinecraftProtocol) {
        val (codec, metadata) = protocol.registry.getPacketData(packet)

        val content = ByteChannel(autoFlush = false).apply {
            VarIntCodec.encode(this, metadata.id)
            codec.encode(packet, this)
            flush()
            close()
        }

        val contentBytes = content.readRemaining().readByteArray()

        if (threshold >= 0) {
            val uncompressedSize = contentBytes.size
            if (uncompressedSize >= threshold) {
                val compressedData = zlibCompress(contentBytes)
                val dataLengthBytes = VarIntCodec.encodeToBytes(uncompressedSize)
                val packetLength = dataLengthBytes.size + compressedData.size
                VarIntCodec.encode(channel, packetLength)
                channel.writeFully(dataLengthBytes)
                channel.writeFully(compressedData)
            } else {
                // Encode 0 to indicate no compression
                val dataLengthBytes = VarIntCodec.encodeToBytes(0)
                val packetLength = dataLengthBytes.size + contentBytes.size
                VarIntCodec.encode(channel, packetLength)
                channel.writeFully(dataLengthBytes)
                channel.writeFully(contentBytes)
            }
        } else {
            VarIntCodec.encode(channel, contentBytes.size)
            channel.writeFully(contentBytes)
        }
        channel.flush()
    }

    override suspend fun receive(
        channel: ByteReadChannel,
        protocol: MinecraftProtocol,
        expectedDirection: Direction
    ): MinecraftPacket? {
        val packetLength = VarIntCodec.decodeOrNull(channel) ?: return null
        if (packetLength < 0) return null

        val dataBytes = try {
            channel.readBuffer(packetLength)
        } catch (e: Exception) {
            return null
        }

        val dataChannel = ByteReadChannel(dataBytes)
        val dataLength = VarIntCodec.decodeOrNull(dataChannel) ?: return null

        val compressedData = dataChannel.readRemaining().readByteArray()

        // Server-side check for compressed packets below threshold
        if (threshold >= 0 && expectedDirection == Direction.SERVERBOUND && dataLength > 0 && dataLength < threshold) {
            throw IOException("Compressed packet size $dataLength is below threshold $threshold")
        }

        val uncompressedData = if (dataLength > 0) {
            try {
                zlibDecompress(compressedData, dataLength)
            } catch (e: IOException) {
                return null
            }
        } else {
            compressedData
        }

        val uncompressedChannel = ByteReadChannel(uncompressedData)
        val id = VarIntCodec.decodeOrNull(uncompressedChannel) ?: return null
        val (codec, metadata) = protocol.registry.getPacketDataByIdOrNull(id, expectedDirection, protocol.state)
            ?: return null

        return codec.decode(uncompressedChannel)
    }

    private fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        return output.toByteArray()
    }

    @Throws(IOException::class)
    private fun zlibDecompress(data: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val output = ByteArrayOutputStream(expectedSize)
        val buffer = ByteArray(1024)
        try {
            while (!inflater.finished()) {
                val count = try {
                    inflater.inflate(buffer)
                } catch (e: DataFormatException) {
                    throw IOException("Invalid compressed data", e)
                }
                if (count == 0) {
                    if (inflater.needsInput()) {
                        throw IOException("Unexpected end of compressed data")
                    } else {
                        throw IOException("Failed to decompress data")
                    }
                }
                output.write(buffer, 0, count)
            }
        } finally {
            inflater.end()
        }
        val decompressed = output.toByteArray()
        if (decompressed.size != expectedSize) {
            throw IOException("Decompressed size ${decompressed.size} does not match expected $expectedSize")
        }
        return decompressed
    }
}