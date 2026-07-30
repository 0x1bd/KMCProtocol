package org.kvxd.kmcprotocol.extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.extensions.chat.ProfilePublicKeys
import org.kvxd.kmcprotocol.extensions.payload.PluginChannels
import org.kvxd.kmcprotocol.extensions.payload.PluginMessageRegistry
import org.kvxd.kmcprotocol.extensions.payload.PluginMessages
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class HelloPayload(val name: String, val level: Int)

class PayloadExtensionTest {

    @Test
    fun `the brand channel round trips`() {
        val encoded = PluginMessages.encodeBrand("vanilla")
        assertEquals("vanilla", PluginMessages.decodeBrand(encoded))
    }

    @Test
    fun `the register channel list round trips`() {
        val channels = listOf("minecraft:brand", "fabric:registry_sync", "voicechat:secret")
        val encoded = PluginMessages.encodeChannels(channels)
        assertEquals(channels, PluginMessages.decodeChannels(encoded))
        assertEquals(PluginChannels.Register, "minecraft:register")
    }

    @Test
    fun `registered plugin message payloads round trip through the packet codec`() {
        val registry = PluginMessageRegistry().register("kmc:hello", serializer<HelloPayload>())

        val value = HelloPayload("kvxd", 42)
        val bytes = registry.encode("kmc:hello", value)

        assertEquals(value, registry.decodeAs<HelloPayload>("kmc:hello", bytes))
        assertTrue(registry.isRegistered("kmc:hello"))
        assertEquals(setOf("kmc:hello"), registry.channels())
    }

    @Test
    fun `an unregistered channel is reported rather than guessed at`() {
        val registry = PluginMessageRegistry()
        val failure = assertFailsWith<KmcProtocolException> { registry.decode("kmc:nope", ByteArray(0)) }
        assertTrue(failure.message!!.contains("kmc:nope"), failure.message!!)
    }

    @Test
    fun `a plugin message with trailing bytes is rejected`() {
        val registry = PluginMessageRegistry().register("kmc:hello", serializer<HelloPayload>())
        val bytes = registry.encode("kmc:hello", HelloPayload("a", 1)) + byteArrayOf(0, 0)

        val failure = assertFailsWith<KmcProtocolException> { registry.decode("kmc:hello", bytes) }
        assertTrue(failure.message!!.contains("trailing bytes"), failure.message!!)
    }

    @Test
    fun `registering the same channel twice fails loudly`() {
        val registry = PluginMessageRegistry().register("kmc:hello", serializer<HelloPayload>())
        assertFailsWith<IllegalArgumentException> { registry.register("kmc:hello", serializer<HelloPayload>()) }
    }

    @Test
    fun `chat session public keys round trip through X509`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val encoded = ProfilePublicKeys.encode(pair.public)
        val decoded = ProfilePublicKeys.decode(encoded)

        assertEquals(pair.public, decoded)
        assertContentEquals(encoded, ProfilePublicKeys.encode(decoded))
    }

    @Test
    fun `a malformed public key is reported as a protocol error`() {
        assertFailsWith<KmcProtocolException> { ProfilePublicKeys.decode(byteArrayOf(1, 2, 3, 4)) }
    }

    @Test
    fun `the signable payload verifies against a matching signature`() {
        val profile = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val authority = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val playerId = UUID.fromString("00000000-0000-0000-0000-0000000000ff")
        val expiresAt = 1_800_000_000_000L
        val encodedKey = ProfilePublicKeys.encode(profile.public)

        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(authority.private)
            update(ProfilePublicKeys.signableBytes(playerId, expiresAt, encodedKey))
            sign()
        }

        assertTrue(ProfilePublicKeys.verify(playerId, expiresAt, encodedKey, signature, authority.public))
        assertFalse(ProfilePublicKeys.verify(playerId, expiresAt + 1, encodedKey, signature, authority.public))
        assertFalse(ProfilePublicKeys.verify(playerId, expiresAt, encodedKey, signature, profile.public))
    }
}
