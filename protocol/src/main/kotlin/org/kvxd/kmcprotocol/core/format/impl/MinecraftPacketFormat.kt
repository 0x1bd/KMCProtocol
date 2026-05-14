package org.kvxd.kmcprotocol.core.format.impl

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.io.readByteArray
import org.kvxd.kmcprotocol.core.KmcPacketDecodeException
import org.kvxd.kmcprotocol.core.KmcPacketEncodeException
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolData
import org.kvxd.kmcprotocol.core.encoding.MinecraftDecoder
import org.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import org.kvxd.kmcprotocol.core.encoding.readVarInt
import org.kvxd.kmcprotocol.core.encoding.writeVarInt
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
                throw KmcPacketEncodeException(packet::class, data.state, cause.message ?: cause::class.simpleName.orEmpty(), cause)
            }
        val payload = compressionThreshold
            ?.let { encodeCompressedPayload(packetBytes, it) }
            ?: packetBytes
        val frame = ByteChannel().apply {
            writeVarInt(payload.size)
            writeFully(payload)
            flush()
            close()
        }.readRemaining().readByteArray()

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

            val packetChannel = ByteReadChannel(decodedPayload)
            packetId = packetChannel.readVarInt()
            return decodePacket(packetChannel, packetId, expectedDirection, length)
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

    private suspend fun encodePacket(packet: MinecraftPacket): ByteArray {
        val metadata = data.registry.getPacketMetadata(data.state, packet)
            ?: throw KmcPacketEncodeException(
                packetClass = packet::class,
                state = data.state,
                details = "No packet registration exists for this packet class in state ${data.state}.",
            )

        return ByteChannel().apply {
            writeVarInt(metadata.id)

            val serializer = data.registry.getPacketSerializerByClass(data.state, packet::class)
            serializer!!.serialize(MinecraftEncoder(data, this), packet)

            flush()
            close()
        }.readRemaining().readByteArray()
    }

    private fun encodeCompressedPayload(packetBytes: ByteArray, threshold: Int): ByteArray {
        val output = ByteArrayOutputStream()
        if (packetBytes.size < threshold) {
            output.writeVarInt(0)
            output.write(packetBytes)
            return output.toByteArray()
        }

        output.writeVarInt(packetBytes.size)
        output.write(deflate(packetBytes))
        return output.toByteArray()
    }

    private fun decodeCompressedPayload(payload: ByteArray, direction: Direction, frameLength: Int): ByteArray {
        val channel = ByteReadChannel(payload)
        val dataLength = kotlinx.coroutines.runBlocking { channel.readVarInt() }
        val compressedBytes = kotlinx.coroutines.runBlocking { channel.readRemaining().readByteArray() }

        if (dataLength == 0) return compressedBytes

        return inflate(compressedBytes, dataLength, direction, frameLength)
    }

    private fun decodePacket(
        packetChannel: ByteReadChannel,
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
                details = "No packet serializer is registered for packet id 0x${packetId.toString(16)} in ${data.state}/$expectedDirection.",
            )

        return serializer.deserialize(MinecraftDecoder(data, packetChannel))
    }

    private suspend fun readFrameLength(channel: ByteReadChannel, direction: Direction): Int {
        decryptCipher ?: return channel.readVarInt()

        var offset = 0
        var value = 0
        var byte: Byte

        do {
            if (offset == 35) {
                throw KmcPacketDecodeException(data.state, direction, null, null, "Encrypted frame length VarInt exceeded 5 bytes.")
            }

            byte = decryptByte(channel.readByte())
            value = value or ((byte.toInt() and 0x7f) shl offset)
            offset += 7
        } while ((byte.toInt() and 0x80) != 0)

        return value
    }

    private fun decryptByte(byte: Byte): Byte =
        decryptCipher!!.update(byteArrayOf(byte))[0]

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(bytes)
        deflater.finish()

        return ByteArrayOutputStream().use { output ->
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
                    throw KmcPacketDecodeException(data.state, direction, null, frameLength, "Compressed packet ended before inflater reached the declared uncompressed length.")
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

    private fun createCipher(mode: Int, sharedSecret: ByteArray): Cipher {
        val key = SecretKeySpec(sharedSecret, "AES")
        return Cipher.getInstance("AES/CFB8/NoPadding").apply {
            init(mode, key, IvParameterSpec(sharedSecret))
        }
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var current = value
        do {
            val byte = current and 0x7f
            current = current ushr 7
            write(if (current != 0) byte or 0x80 else byte)
        } while (current != 0)
    }
}
