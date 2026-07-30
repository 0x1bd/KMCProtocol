package org.kvxd.kmcprotocol.extensions.chat

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import org.kvxd.kmcprotocol.core.KmcProtocolException

object ProfilePublicKeys {

    const val AuthoritySignatureAlgorithm: String = "SHA1withRSA"

    const val MessageSignatureAlgorithm: String = "SHA256withRSA"

    fun decode(encoded: ByteArray): PublicKey =
        try {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encoded))
        } catch (cause: InvalidKeySpecException) {
            throw KmcProtocolException("Chat session public key is not a valid X.509 RSA key.", cause)
        }

    fun encode(key: PublicKey): ByteArray {
        require(key.format == "X.509") { "Expected an X.509 encoded key but got ${key.format}" }
        return key.encoded
    }

    fun signableBytes(profileId: UUID, expiresAt: Long, encodedKey: ByteArray): ByteArray =
        ByteBuffer.allocate(16 + 8 + encodedKey.size)
            .putLong(profileId.mostSignificantBits)
            .putLong(profileId.leastSignificantBits)
            .putLong(expiresAt)
            .put(encodedKey)
            .array()

    fun verify(
        profileId: UUID,
        expiresAt: Long,
        encodedKey: ByteArray,
        signature: ByteArray,
        authority: PublicKey,
    ): Boolean =
        Signature.getInstance(AuthoritySignatureAlgorithm).run {
            initVerify(authority)
            update(signableBytes(profileId, expiresAt, encodedKey))
            runCatching { verify(signature) }.getOrDefault(false)
        }
}
