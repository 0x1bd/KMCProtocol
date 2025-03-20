package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.experimental.and
import kotlin.reflect.KProperty1

/** From Gabi's medium post. [Source](https://aripiprazole.medium.com/writing-a-minecraft-protocol-implementation-in-kotlin-9276c584bd42) **/

internal suspend fun ByteWriteChannel.writeVarInt(int: Int) {
    var value = int

    while (true) {
        if ((int and 0xFFFFFF80.toInt()) == 0) {
            writeByte(value.toByte())
            return
        }

        writeByte(((value and 0x7F) or 0x80).toByte())
        value = value ushr 7
    }
}

internal suspend fun ByteReadChannel.readVarInt(): Int {
    var offset = 0
    var value = 0L
    var byte: Byte

    do {
        if (offset == 35) error("VarInt too long")

        byte = readByte()
        value = value or ((byte.toLong() and 0x7FL) shl offset)

        offset += 7
    } while ((byte and 0x80.toByte()) != 0.toByte())

    return value.toInt()
}

fun <T : MinecraftPacket, P> PacketCodec.PacketCodecBuilder<T>.varInt(
    property: KProperty1<T, P>
) {
    addCodec(
        encoder = { packet, channel ->
            val value = property.get(packet) as Int
            channel.writeVarInt(value)
        },
        decoder = { channel -> channel.readVarInt() }
    )
}