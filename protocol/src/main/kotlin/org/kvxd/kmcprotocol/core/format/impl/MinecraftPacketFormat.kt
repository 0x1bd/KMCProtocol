package org.kvxd.kmcprotocol.core.format.impl

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.kvxd.kmcprotocol.core.KmcPacketDecodeException
import org.kvxd.kmcprotocol.core.KmcPacketEncodeException
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import org.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import org.kvxd.kmcprotocol.core.encoding.PacketReader
import org.kvxd.kmcprotocol.core.encoding.PacketWriter
import org.kvxd.kmcprotocol.core.format.PacketFormat
import org.kvxd.kmcprotocol.network.Direction

class MinecraftPacketFormat(private val data: ProtocolData) : PacketFormat {

    @Volatile
    private var compressionThreshold: Int? = null

    private var encryptCipher: Cipher? = null
    private var decryptCipher: Cipher? = null

    fun enableCompression(threshold: Int) {
        compressionThreshold = threshold
    }

    fun disableCompression() {
        compressionThreshold = null
    }

    fun enableEncryption(sharedSecret: ByteArray) {
        encryptCipher = createCipher(Cipher.ENCRYPT_MODE, sharedSecret)
        decryptCipher = createCipher(Cipher.DECRYPT_MODE, sharedSecret)
    }

    fun disableEncryption() {
        encryptCipher = null
        decryptCipher = null
    }

    override suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel) {
        val packetBytes = runCatching { encodePacket(packet) }
            .getOrElse { cause ->
                if (cause is KmcPacketEncodeException) throw cause
                throw KmcPacketEncodeException(
                    packet::class,
                    data.state,
                    cause.message ?: cause::class.simpleName.orEmpty(),
                    cause,
                )
            }

        val payload = compressionThreshold?.let { encodeCompressedPayload(packetBytes, it) } ?: packetBytes
        val frame = PacketWriter(payload.size + 5).apply {
            writeVarInt(payload.size)
            writeBytes(payload)
        }.toByteArray()

        channel.writeFully(encryptCipher?.update(frame) ?: frame)
        channel.flush()
    }

    override suspend fun receive(channel: ByteReadChannel, expectedDirection: Direction): MinecraftPacket? {
        var length: Int? = null
        var packetId: Int? = null
        try {
            length = readFrameLength(channel, expectedDirection)
            val payload = ByteArray(length)
            if (length > 0) channel.readFully(payload)

            val packetBytes = decryptCipher?.update(payload) ?: payload
            val decodedPayload = compressionThreshold
                ?.let { decodeCompressedPayload(packetBytes, expectedDirection, length) }
                ?: packetBytes

            val reader = PacketReader(decodedPayload)
            packetId = reader.readVarInt()
            return decodePacket(reader, packetId, expectedDirection, length)
        } catch (cause: Throwable) {
            if (cause is KmcPacketDecodeException) throw cause
            throw KmcPacketDecodeException(
                state = data.state,
                direction = expectedDirection,
                packetId = packetId,
                frameLength = length,
                details = cause.message ?: cause::class.simpleName.orEmpty(),
                cause = cause,
            )
        }
    }

    private fun encodePacket(packet: MinecraftPacket): ByteArray {
        val metadata = data.registry.getPacketMetadata(data.state, packet)
            ?: throw KmcPacketEncodeException(
                packetClass = packet::class,
                state = data.state,
                details = "No packet registration exists for this packet class in state ${data.state}.",
            )

        val writer = PacketWriter()
        writer.writeVarInt(metadata.id)
        metadata.serializer.serialize(MinecraftEncoder(writer, data.codec, data.serializersModule), packet)
        return writer.toByteArray()
    }

    private fun decodePacket(
        reader: PacketReader,
        packetId: Int,
        expectedDirection: Direction,
        frameLength: Int,
    ): MinecraftPacket {
        val serializer = data.registry.getPacketSerializerById(data.state, packetId, expectedDirection)
            ?: throw KmcPacketDecodeException(
                state = data.state,
                direction = expectedDirection,
                packetId = packetId,
                frameLength = frameLength,
                details = "No packet serializer is registered for packet id 0x${packetId.toString(16)} in " +
                    "${data.state}/$expectedDirection.",
            )

        return serializer.deserialize(MinecraftDecoder(reader, data.codec, data.serializersModule))
    }

    private fun encodeCompressedPayload(packetBytes: ByteArray, threshold: Int): ByteArray {
        val writer = PacketWriter(packetBytes.size + 5)
        if (packetBytes.size < threshold) {
            writer.writeVarInt(0)
            writer.writeBytes(packetBytes)
        } else {
            writer.writeVarInt(packetBytes.size)
            writer.writeBytes(deflate(packetBytes))
        }
        return writer.toByteArray()
    }

    private fun decodeCompressedPayload(payload: ByteArray, direction: Direction, frameLength: Int): ByteArray {
        val reader = PacketReader(payload)
        val dataLength = reader.readVarInt()
        val compressedBytes = reader.readRemaining()

        if (dataLength == 0) return compressedBytes
        return inflate(compressedBytes, dataLength, direction, frameLength)
    }

    private suspend fun readFrameLength(channel: ByteReadChannel, direction: Direction): Int {
        decryptCipher ?: return channel.readVarIntFrame(direction)

        var offset = 0
        var value = 0
        while (true) {
            if (offset == 35) {
                throw KmcPacketDecodeException(
                    data.state, direction, null, null, "Encrypted frame length VarInt exceeded 5 bytes.",
                )
            }
            val byte = decryptByte(channel.readByte()).toInt()
            value = value or ((byte and 0x7f) shl offset)
            if (byte and 0x80 == 0) return value
            offset += 7
        }
    }

    private suspend fun ByteReadChannel.readVarIntFrame(direction: Direction): Int {
        var offset = 0
        var value = 0
        while (true) {
            if (offset == 35) {
                throw KmcPacketDecodeException(
                    data.state, direction, null, null, "Frame length VarInt exceeded 5 bytes.",
                )
            }
            val byte = readByte().toInt()
            value = value or ((byte and 0x7f) shl offset)
            if (byte and 0x80 == 0) return value
            offset += 7
        }
    }

    private fun decryptByte(byte: Byte): Byte = decryptCipher!!.update(byteArrayOf(byte))[0]

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(bytes)
        deflater.finish()

        return ByteArrayOutputStream(bytes.size).use { output ->
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            deflater.end()
            output.toByteArray()
        }
    }

    private fun inflate(bytes: ByteArray, expectedLength: Int, direction: Direction, frameLength: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(bytes)

        return ByteArrayOutputStream(expectedLength).use { output ->
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) {
                    throw KmcPacketDecodeException(
                        data.state,
                        direction,
                        null,
                        frameLength,
                        "Compressed packet ended before inflater reached the declared uncompressed length.",
                    )
                }
                output.write(buffer, 0, count)
            }
            inflater.end()

            val inflated = output.toByteArray()
            check(inflated.size == expectedLength) {
                "Inflated packet length ${inflated.size} did not match declared length $expectedLength"
            }
            inflated
        }
    }

    private fun createCipher(mode: Int, sharedSecret: ByteArray): Cipher =
        Cipher.getInstance("AES/CFB8/NoPadding").apply {
            init(mode, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(sharedSecret))
        }
}
