package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import kotlin.reflect.KProperty1

private val serializer = GsonComponentSerializer.gson()

internal suspend fun ByteWriteChannel.writeJsonComponent(component: Component) {
    writeString(serializer.serialize(component))
}

internal suspend fun ByteReadChannel.readJsonComponent(): Component {
    return serializer.deserialize(readString())
}

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.jsonComponent(
    property: KProperty1<T, Component>
) {
    addCodec<Component>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeJsonComponent(value)
        },
        decoder = { channel -> channel.readJsonComponent() }
    )
}