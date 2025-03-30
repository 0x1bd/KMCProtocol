package de.kvxd.kmcprotocol.packet.format

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketFormat
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

class Encrypted(private val key: Key) : PacketFormat {

    private val encryptCipher = Cipher.getInstance("AES/CFB8/NoPadding")
        .apply { init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(key.encoded)) }

    private val decryptCipher = Cipher.getInstance("AES/CFB8/NoPadding")
        .apply { init(Cipher.DECRYPT_MODE, key, IvParameterSpec(key.encoded)) }

    private fun encrypt(data: ByteArray): ByteArray {
        return encryptCipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        return decryptCipher.doFinal(data)
    }

    override suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel, protocol: MinecraftProtocol) {
        val (codec, metadata) = protocol.registry.getPacketData(packet)

        val contentChannel = ByteChannel(autoFlush = false)
        VarIntCodec.encode(contentChannel, metadata.id)
        codec.encode(packet, contentChannel)
        contentChannel.flush()
        contentChannel.close()
        val contentBytes = contentChannel.readRemaining().readByteArray()

        val encryptedBytes = encrypt(contentBytes)

        VarIntCodec.encode(channel, encryptedBytes.size)
        channel.writeFully(encryptedBytes)
        channel.flush()
    }

    override suspend fun receive(
        channel: ByteReadChannel,
        protocol: MinecraftProtocol,
        expectedDirection: Direction
    ): MinecraftPacket? {
        val encryptedLength = VarIntCodec.decodeOrNull(channel) ?: return null
        if (encryptedLength < 0) return null

        val encryptedBytes = try {
            channel.readByteArray(encryptedLength)
        } catch (e: Exception) {
            return null
        }

        val contentBytes = try {
            decrypt(encryptedBytes)
        } catch (e: Exception) {
            return null
        }

        val contentChannel = ByteReadChannel(contentBytes)
        val id = VarIntCodec.decodeOrNull(contentChannel) ?: return null
        val (codec, metadata) = protocol.registry.getPacketDataByIdOrNull(id, expectedDirection, protocol.state)
            ?: return null

        return codec.decode(contentChannel)
    }
}