package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.float(
    property: KProperty1<T, Float>
) {
    addCodec<Float>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeFloat(value)
        },
        decoder = { channel -> channel.readFloat() }
    )
}