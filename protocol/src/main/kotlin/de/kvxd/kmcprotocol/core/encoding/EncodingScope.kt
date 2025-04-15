package de.kvxd.kmcprotocol.core.encoding

import de.kvxd.kmcprotocol.core.MinecraftPacket
import de.kvxd.kmcprotocol.core.ProtocolData
import de.kvxd.kmcprotocol.core.format.number.readVarInt
import de.kvxd.kmcprotocol.core.format.number.writeVarInt
import de.kvxd.kmcprotocol.network.Direction
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.jetbrains.annotations.ApiStatus.Internal

class EncodingScope(val channel: ByteChannel) {

    @Internal
    val protocolData = ProtocolData()

    @Internal
    val encoder = MinecraftBytePacketEncoder(this)

    @Internal
    val decoder = MinecraftBytePacketDecoder(this, channel)

    inline fun <reified T : MinecraftPacket> getPacketId(): Int {
        return protocolData.registry.getPacketInfo(T::class).metadata.id
    }

    suspend inline fun <reified T : MinecraftPacket> encode(packet: T) {
        val payloadBytes = encoder.encodePacket(packet)

        val packetId = getPacketId<T>()

        val completePacket = buildPacket {
            writeVarInt(packetId)
            writeFully(payloadBytes)
        }

        channel.writePacket(buildPacket {
            writeVarInt(completePacket.remaining.toInt())
            writePacket(completePacket)
        })

        channel.flush()
    }

    suspend fun decode(direction: Direction): Pair<Int, MinecraftPacket> {
        val packetLength = channel.readVarInt()
        val packetId = channel.readVarInt()

        val packet = decoder.decodePacket(packetId, direction)

        return packetLength to packet
    }

    suspend fun decodeDirect(direction: Direction): MinecraftPacket = decode(direction).second

    companion object {

        suspend fun encoding(channel: ByteChannel, block: suspend EncodingScope.() -> Unit = {}): EncodingScope {
            return EncodingScope(channel).apply {
                block()
            }
        }
    }

}