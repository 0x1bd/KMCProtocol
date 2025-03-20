package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KProperty1

internal fun ByteWriteChannel.writeString(string: String) = runBlocking {
    writeVarInt(string.length)
    writeByteArray(string.toByteArray(Charsets.UTF_8))
}

internal fun ByteReadChannel.readString(): String = runBlocking {
    val length = readVarInt()
    val bytes = ByteArray(length)
    readFully(bytes)
    return@runBlocking bytes.toString(Charsets.UTF_8)
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