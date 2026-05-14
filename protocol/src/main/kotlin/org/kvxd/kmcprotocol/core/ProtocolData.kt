package org.kvxd.kmcprotocol.core

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.kvxd.kmcprotocol.core.format.impl.MinecraftPacketFormat
import org.kvxd.kmcprotocol.network.Direction

class ProtocolData(
    initialState: ProtocolState = ProtocolState.Handshake,
    val serializersModule: SerializersModule = EmptySerializersModule(),
    var registry: PacketRegistry = PacketRegistry().also(PacketRegistry::initializePacketRegistry),
) {

    @Volatile
    var state = initialState

    private val packetFormat = MinecraftPacketFormat(this)

    @Deprecated(
        message = "Use ProtocolData.send/receive and ProtocolData.enableCompression/enableEncryption instead.",
        replaceWith = ReplaceWith("this"),
    )
    val format: MinecraftPacketFormat
        get() = packetFormat

    fun switchTo(state: ProtocolState): ProtocolData = apply {
        this.state = state
    }

    fun enableCompression(threshold: Int): ProtocolData = apply {
        packetFormat.enableCompression(threshold)
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