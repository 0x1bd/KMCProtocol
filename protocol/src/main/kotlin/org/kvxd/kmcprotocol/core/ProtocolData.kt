package org.kvxd.kmcprotocol.core

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.kvxd.kmcprotocol.core.format.impl.MinecraftPacketFormat
import org.kvxd.kmcprotocol.network.Direction

/**
 * Runtime protocol context used by packet encoders, decoders, clients, and servers.
 */
class ProtocolData(
    /**
     * Initial protocol state for this context.
     */
    initialState: ProtocolState = ProtocolState.Handshake,

    /**
     * Whether connections should update state and compression from known flow-control packets.
     */
    val automaticProtocolFlow: Boolean = true,

    /**
     * Classpath resource path for the protocol.json file used by dynamic packet fields.
     */
    val protocolResourcePath: String? = null,

    /**
     * Serialization module used when encoding and decoding packet classes.
     */
    val serializersModule: SerializersModule = EmptySerializersModule(),

    /**
     * Packet registry used to resolve packet classes and ids.
     */
    var registry: PacketRegistry = PacketRegistry(),
) {

    /**
     * Current protocol state for this context.
     */
    @Volatile
    var state = initialState

    private val packetFormat = MinecraftPacketFormat(this)

    /**
     * Legacy access to the packet format owned by this protocol context.
     */
    @Deprecated(
        message = "Use ProtocolData.send/receive and ProtocolData.enableCompression/enableEncryption instead.",
        replaceWith = ReplaceWith("this"),
    )
    val format: MinecraftPacketFormat
        get() = packetFormat

    /**
     * Switches the context to a new protocol state.
     */
    fun switchTo(state: ProtocolState): ProtocolData = apply {
        this.state = state
    }

    /**
     * Enables Minecraft packet compression using the supplied threshold.
     */
    fun enableCompression(threshold: Int): ProtocolData = apply {
        packetFormat.enableCompression(threshold)
    }

    /**
     * Disables Minecraft packet compression.
     */
    fun disableCompression(): ProtocolData = apply {
        packetFormat.disableCompression()
    }

    /**
     * Enables AES/CFB8 stream encryption with the negotiated shared secret.
     */
    fun enableEncryption(sharedSecret: ByteArray): ProtocolData = apply {
        packetFormat.enableEncryption(sharedSecret)
    }

    /**
     * Disables stream encryption.
     */
    fun disableEncryption(): ProtocolData = apply {
        packetFormat.disableEncryption()
    }

    /**
     * Encodes and writes a packet to the supplied channel.
     */
    suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel) {
        packetFormat.send(packet, channel)
    }

    /**
     * Reads and decodes the next packet from the supplied channel.
     */
    suspend fun receive(channel: ByteReadChannel, expectedDirection: Direction): MinecraftPacket? =
        packetFormat.receive(channel, expectedDirection)

}
