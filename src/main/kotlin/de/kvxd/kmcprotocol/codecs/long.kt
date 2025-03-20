package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.long(
    property: KProperty1<T, Long>
) {
    addCodec(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeLong(value)
        },
        decoder = { channel -> channel.readLong() }
    )
}