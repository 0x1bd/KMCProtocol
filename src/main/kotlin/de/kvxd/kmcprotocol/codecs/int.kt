package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.int(
    property: KProperty1<T, Int>
) {
    addCodec<Int>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeInt(value)
        },
        decoder = { channel -> channel.readInt() }
    )
}