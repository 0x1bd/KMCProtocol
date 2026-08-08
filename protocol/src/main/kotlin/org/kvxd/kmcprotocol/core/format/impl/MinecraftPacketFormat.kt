package org.kvxd.kmcprotocol.core.format.impl

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private const val CompressionDisabled = -1

class MinecraftPacketFormat(private val data: ProtocolData) : PacketFormat {

    private class CipherPair(val encrypt: Cipher, val decrypt: Cipher)

    @Volatile
    private var compressionThreshold: Int = CompressionDisabled

    @Volatile
    private var ciphers: CipherPair? = null

    private val sendMutex = Mutex()
    private val receiveMutex = Mutex()

    private val decryptInput = ByteArray(1)
    private val decryptOutput = ByteArray(1)

    val isCompressionEnabled: Boolean
        get() = compressionThreshold >= 0

    val isEncryptionEnabled: Boolean
        get() = ciphers != null

    fun setCompression(threshold: Int) {
        compressionThreshold = if (threshold < 0) CompressionDisabled else threshold
    }

    fun disableCompression() {
        compressionThreshold = CompressionDisabled
    }

    fun enableEncryption(sharedSecret: ByteArray) {
        ciphers = CipherPair(
            createCipher(Cipher.ENCRYPT_MODE, sharedSecret),
            createCipher(Cipher.DECRYPT_MODE, sharedSecret),
        )
    }

    fun disableEncryption() {
        ciphers = null
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

        val threshold = compressionThreshold
        val payload = if (threshold >= 0) encodeCompressedPayload(packetBytes, threshold) else packetBytes

        if (payload.size > data.limits.maxFrameLength) {
            throw KmcPacketEncodeException(
                packet::class,
                data.state,
                "Encoded frame is ${payload.size} bytes, exceeding the " +
                    "${data.limits.maxFrameLength} byte frame limit.",
            )
        }

        val frame = PacketWriter(payload.size + 5).apply {
            writeVarInt(payload.size)
            writeBytes(payload)
        }.toByteArray()

        sendMutex.withLock {
            channel.writeFully(ciphers?.encrypt?.update(frame) ?: frame)
            channel.flush()
        }
    }

    override suspend fun receive(channel: ByteReadChannel, expectedDirection: Direction): MinecraftPacket? =
        receiveMutex.withLock {
            var length: Int? = null
            var packetId: Int? = null
            try {
                val frameLength = readFrameLength(channel, expectedDirection)
                length = frameLength

                val payload = ByteArray(frameLength)
                if (frameLength > 0) channel.readFully(payload)

                val packetBytes = ciphers?.decrypt?.update(payload) ?: payload
                val threshold = compressionThreshold
                val decodedPayload = if (threshold >= 0) {
                    decodeCompressedPayload(packetBytes, expectedDirection, frameLength, threshold)
                } else {
                    packetBytes
                }

                val reader = PacketReader(decodedPayload, limits = data.limits, nbt = data.nbtFormat)
                packetId = reader.readVarInt()
                val packet = decodePacket(reader, packetId, expectedDirection, frameLength)

                if (reader.remaining > 0) {
                    throw KmcPacketDecodeException(
                        state = data.state,
                        direction = expectedDirection,
                        packetId = packetId,
                        frameLength = frameLength,
                        details = "Packet decoded successfully but left ${reader.remaining} trailing bytes unread.",
                    )
                }
                packet
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

        val writer = PacketWriter(nbt = data.nbtFormat)
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

    private fun decodeCompressedPayload(
        payload: ByteArray,
        direction: Direction,
        frameLength: Int,
        threshold: Int,
    ): ByteArray {
        val reader = PacketReader(payload, limits = data.limits)
        val dataLength = reader.readVarInt()
        val compressedBytes = reader.readRemaining()

        if (dataLength == 0) return compressedBytes

        if (data.limits.validateDecompressedSize && dataLength < threshold) {
            throw KmcPacketDecodeException(
                data.state,
                direction,
                null,
                frameLength,
                "Badly compressed packet: declared uncompressed length $dataLength is below the " +
                    "negotiated compression threshold of $threshold.",
            )
        }

        if (dataLength < 0 || dataLength > data.limits.maxUncompressedLength) {
            throw KmcPacketDecodeException(
                data.state,
                direction,
                null,
                frameLength,
                "Compressed packet declares an uncompressed length of $dataLength bytes, which is outside " +
                    "the permitted range of 1..${data.limits.maxUncompressedLength}.",
            )
        }

        return inflate(compressedBytes, dataLength, direction, frameLength)
    }

    private suspend fun readFrameLength(channel: ByteReadChannel, direction: Direction): Int {
        val length = if (ciphers == null) {
            channel.readVarIntFrame(direction)
        } else {
            channel.readEncryptedVarIntFrame(direction)
        }

        if (length <= 0 || length > data.limits.maxFrameLength) {
            throw KmcPacketDecodeException(
                data.state,
                direction,
                null,
                length,
                "Frame length $length is outside the permitted range of 1..${data.limits.maxFrameLength}.",
            )
        }
        return length
    }

    private suspend fun ByteReadChannel.readEncryptedVarIntFrame(direction: Direction): Int {
        var offset = 0
        var value = 0
        while (true) {
            if (offset == 35) {
                throw KmcPacketDecodeException(
                    data.state, direction, null, null, "Encrypted frame length VarInt exceeded 5 bytes.",
                )
            }
            val byte = decryptByte(readByte()).toInt()
            if (offset == 28 && byte and 0x70 != 0) {
                throw KmcPacketDecodeException(
                    data.state, direction, null, null, "Encrypted frame length VarInt overflows 32 bits.",
                )
            }
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
            if (offset == 28 && byte and 0x70 != 0) {
                throw KmcPacketDecodeException(
                    data.state, direction, null, null, "Frame length VarInt overflows 32 bits.",
                )
            }
            value = value or ((byte and 0x7f) shl offset)
            if (byte and 0x80 == 0) return value
            offset += 7
        }
    }

    private fun decryptByte(byte: Byte): Byte {
        val cipher = ciphers?.decrypt ?: return byte
        decryptInput[0] = byte
        cipher.update(decryptInput, 0, 1, decryptOutput, 0)
        return decryptOutput[0]
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        try {
            deflater.setInput(bytes)
            deflater.finish()

            val output = ByteArrayOutputStream(bytes.size)
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(bytes: ByteArray, expectedLength: Int, direction: Direction, frameLength: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(bytes)

            val output = ByteArrayOutputStream(expectedLength)
            val buffer = ByteArray(8192)
            var total = 0L
            while (!inflater.finished()) {
                val count = try {
                    inflater.inflate(buffer)
                } catch (cause: DataFormatException) {
                    throw decodeFailure(direction, frameLength, "Malformed compressed packet: ${cause.message}", cause)
                }

                if (count == 0) {
                    if (inflater.needsInput()) {
                        throw decodeFailure(
                            direction,
                            frameLength,
                            "Compressed packet ended before the inflater reached the declared uncompressed length.",
                        )
                    }
                    if (inflater.needsDictionary()) {
                        throw decodeFailure(
                            direction,
                            frameLength,
                            "Compressed packet requires a preset dictionary, which the protocol does not use.",
                        )
                    }
                }

                total += count
                if (total > expectedLength) {
                    throw decodeFailure(
                        direction,
                        frameLength,
                        "Compressed packet inflated past its declared length of $expectedLength bytes.",
                    )
                }
                output.write(buffer, 0, count)
            }

            val inflated = output.toByteArray()
            if (inflated.size != expectedLength) {
                throw decodeFailure(
                    direction,
                    frameLength,
                    "Inflated packet length ${inflated.size} did not match the declared length $expectedLength.",
                )
            }
            return inflated
        } finally {
            inflater.end()
        }
    }

    private fun decodeFailure(
        direction: Direction,
        frameLength: Int,
        details: String,
        cause: Throwable? = null,
    ): KmcPacketDecodeException =
        KmcPacketDecodeException(data.state, direction, null, frameLength, details, cause)

    private fun createCipher(mode: Int, sharedSecret: ByteArray): Cipher =
        Cipher.getInstance("AES/CFB8/NoPadding").apply {
            init(mode, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(sharedSecret))
        }
}
