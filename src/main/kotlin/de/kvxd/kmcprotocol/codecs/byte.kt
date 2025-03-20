package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.byte(
    property: KProperty1<T, Byte>
) {
    addCodec<Byte>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeByte(value)
        },
        decoder = { channel -> channel.readByte() }
    )
}