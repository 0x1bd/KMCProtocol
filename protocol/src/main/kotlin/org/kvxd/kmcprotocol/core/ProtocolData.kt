package org.kvxd.kmcprotocol.core

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.kvxd.kmcprotocol.core.encoding.NbtFormat
import org.kvxd.kmcprotocol.core.encoding.ProtocolCodec
import org.kvxd.kmcprotocol.core.format.impl.MinecraftPacketFormat
import org.kvxd.kmcprotocol.core.format.impl.RawPacketFrame
import org.kvxd.kmcprotocol.network.Direction

class ProtocolData(
    initialState: ProtocolState = ProtocolState.Handshake,
    val automaticProtocolFlow: Boolean = true,
    val codec: ProtocolCodec = ProtocolCodec.Empty,
    val serializersModule: SerializersModule = EmptySerializersModule(),
    val registry: PacketRegistry = PacketRegistry(),
    val limits: ProtocolLimits = ProtocolLimits.Default,
    val nbtFormat: NbtFormat = NbtFormat.Latest,
) {

    @Volatile
    var state: ProtocolState = initialState
        private set

    private val packetFormat = MinecraftPacketFormat(this)

    val isCompressionEnabled: Boolean
        get() = packetFormat.isCompressionEnabled

    val isEncryptionEnabled: Boolean
        get() = packetFormat.isEncryptionEnabled

    fun switchTo(state: ProtocolState): ProtocolData = apply {
        this.state = state
    }

    fun setCompression(threshold: Int): ProtocolData = apply {
        packetFormat.setCompression(threshold)
    }

    fun disableCompression(): ProtocolData = apply {
        packetFormat.disableCompression()
    }

    fun enableEncryption(sharedSecret: ByteArray): ProtocolData = apply {
        packetFormat.enableEncryption(sharedSecret)
    }

    fun disableEncryption(): ProtocolData = apply {
        packetFormat.disableEncryption()
    }

    suspend fun send(packet: MinecraftPacket, channel: ByteWriteChannel) {
        packetFormat.send(packet, channel)
    }

    suspend fun receive(channel: ByteReadChannel, expectedDirection: Direction): MinecraftPacket? =
        packetFormat.receive(channel, expectedDirection)

    internal fun encodePacketBytes(packet: MinecraftPacket): ByteArray =
        packetFormat.encodePacketBytes(packet)

    internal suspend fun sendPacketBytes(
        packetBytes: ByteArray,
        channel: ByteWriteChannel,
        sourcePacket: MinecraftPacket,
    ) {
        packetFormat.sendPacketBytes(packetBytes, channel, sourcePacket::class)
    }

    internal suspend fun receivePacketBytes(
        channel: ByteReadChannel,
        expectedDirection: Direction,
    ): RawPacketFrame = packetFormat.receivePacketBytes(channel, expectedDirection)

    internal fun decodePacketBytes(
        packetBytes: ByteArray,
        expectedDirection: Direction,
        frameLength: Int? = null,
    ): MinecraftPacket = packetFormat.decodePacketBytes(packetBytes, expectedDirection, frameLength)
}