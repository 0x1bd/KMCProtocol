package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

internal suspend fun ByteWriteChannel.writeShort(short: Short) {
    writeInt(short.toInt())
}

internal suspend fun ByteReadChannel.readShort(): Short {
    return readInt().toShort()
}


fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.short(
    property: KProperty1<T, Short>
) {
    addCodec<Short>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeShort(value)
        },
        decoder = { channel -> channel.readShort() }
    )
}