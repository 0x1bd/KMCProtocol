package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.double(
    property: KProperty1<T, Double>
) {
    addCodec<Double>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeDouble(value)
        },
        decoder = { channel -> channel.readDouble() }
    )
}