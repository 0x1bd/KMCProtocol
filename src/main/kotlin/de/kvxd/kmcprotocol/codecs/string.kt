package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.reflect.KProperty1

internal suspend fun ByteWriteChannel.writeString(string: String) {
    writeVarInt(string.length)
    writeByteArray(string.toByteArray(Charsets.UTF_8))
}

internal suspend fun ByteReadChannel.readString(): String {
    val length = readVarInt()
    val bytes = ByteArray(length)
    readFully(bytes)
    return bytes.toString(Charsets.UTF_8)
}

fun <T : MinecraftPacket, P> PacketCodec.PacketCodecBuilder<T>.string(
    property: KProperty1<T, P>
) {
    addCodec(
        encoder = { packet, channel ->
            val value = property.get(packet) as String
            channel.writeString(value)
        },
        decoder = { channel -> channel.readString() }
    )
}