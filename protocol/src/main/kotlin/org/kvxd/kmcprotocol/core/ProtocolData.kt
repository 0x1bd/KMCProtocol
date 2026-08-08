package org.kvxd.kmcprotocol.core

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.kvxd.kmcprotocol.core.encoding.NbtFormat
import org.kvxd.kmcprotocol.core.encoding.ProtocolCodec
import org.kvxd.kmcprotocol.core.format.impl.MinecraftPacketFormat
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
}
