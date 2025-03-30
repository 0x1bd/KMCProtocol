package de.kvxd.kmcprotocol.packet.format

import de.kvxd.kmcprotocol.MinecraftProtocol
import de.kvxd.kmcprotocol.codec.codecs.VarIntCodec
import de.kvxd.kmcprotocol.packet.Direction
import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketFormat
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class Encrypted(
    private val key: Key,
    private val inner: PacketFormat = Uncompressed
) : PacketFormat {

    constructor(
        keyBytes: ByteArray,
        inner: PacketFormat = Uncompressed
    ) : this(SecretKeySpec(keyBytes, "AES"), inner)

    private val encryptCipher = Cipher.getInstance("AES/CFB8/NoPadding")
        .apply { init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(key.encoded.copyOf(16))) }

    private val decryptCipher = Cipher.getInstance("AES/CFB8/NoPadding")
        .apply { init(Cipher.DECRYPT_MODE, key, IvParameterSpec(key.encoded.copyOf(16))) }

    private fun encrypt(data: ByteArray): ByteArray {
        return encryptCipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        return decryptCipher.doFinal(data)
    }

    override suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel, protocol: MinecraftProtocol) {
        // Use pipeTo for non-blocking forwarding
        val innerChannel = ByteChannel()

        inner.send(packet, innerChannel, protocol)
        innerChannel.close()

        // Get the encrypted data as a flow
        val data = innerChannel.readRemaining().readByteArray()

        val encryptedBytes = encrypt(data)

        // Write with proper suspending operations
        VarIntCodec.encode(channel, encryptedBytes.size)
        channel.writeFully(encryptedBytes)
        channel.flush()
    }

    override suspend fun receive(
        channel: ByteReadChannel,
        protocol: MinecraftProtocol,
        expectedDirection: Direction
    ): MinecraftPacket? {
        // Read length first
        val encryptedLength = VarIntCodec.decodeOrNull(channel) ?: return null

        // Read exactly encryptedLength bytes using suspending read
        val encryptedBytes = ByteArray(encryptedLength).apply {
            channel.readFully(this, 0, encryptedLength)
        }

        // Decrypt and process
        val decryptedBytes = decrypt(encryptedBytes)
        return inner.receive(ByteReadChannel(decryptedBytes), protocol, expectedDirection)
    }


    companion object {

        fun generateKey(keySize: Int = 1024): KeyPair =
            KeyPairGenerator.getInstance("RSA").apply {
                initialize(keySize)
            }.genKeyPair()

        fun generateVerifyToken() = ByteArray(4).apply { Random.nextBytes(this) }.toTypedArray()

        fun decryptSecret(key: KeyPair, sharedSecret: Array<Byte>): ByteArray {
            val cipher = Cipher.getInstance("RSA")
            cipher.init(Cipher.DECRYPT_MODE, key.private)

            val decryptedSecret = cipher.doFinal(sharedSecret.toByteArray())

            return decryptedSecret
        }

        fun decryptCombo(
            secret: ByteArray,
            verifyToken: Array<Byte>,
            publicKey: Array<Byte>
        ): Pair<Array<Byte>, Array<Byte>> {

            val cipher = Cipher.getInstance("RSA")
            cipher.init(
                Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA")
                    .generatePublic(X509EncodedKeySpec(publicKey.toByteArray()))
            )


            // Encrypt secret and token
            val encryptedSecret = cipher.doFinal(secret).toTypedArray()
            val encryptedToken = cipher.doFinal(verifyToken.toByteArray()).toTypedArray()

            return encryptedSecret to encryptedToken
        }

        fun generateSecret() = ByteArray(16).apply { Random.nextBytes(this) }

    }
}

