package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import kotlin.reflect.KProperty1

internal val jsonSerializer = GsonComponentSerializer.gson()

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.json(
    property: KProperty1<T, Component>
) {
    addCodec<Component>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeString(jsonSerializer.serialize(value))
        },
        decoder = { channel -> jsonSerializer.deserialize(channel.readString()) }
    )
}