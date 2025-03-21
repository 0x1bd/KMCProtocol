package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

internal suspend fun ByteWriteChannel.writeBoolean(boolean: Boolean) {
    writeByte(if (boolean) 0x01 else 0x00)
}

internal suspend fun ByteReadChannel.readBoolean(): Boolean {
    return readByte() == 0x01.toByte()
}

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.boolean(
    property: KProperty1<T, Boolean>
) {
    addCodec<Boolean>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeBoolean(value)
        },
        decoder = { channel -> channel.readBoolean() }
    )
}