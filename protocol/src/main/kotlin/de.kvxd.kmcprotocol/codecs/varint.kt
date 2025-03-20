package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.experimental.and
import kotlin.reflect.KProperty1

/** From Gabi's medium post. [Source](https://aripiprazole.medium.com/writing-a-minecraft-protocol-implementation-in-kotlin-9276c584bd42) **/

internal suspend fun ByteWriteChannel.writeVarInt(value: Int) {
    var current = value
    do {
        val byte = (current and 0x7F).toByte()
        current = current ushr 7
        writeByte(if (current != 0) (byte.toInt() or 0x80).toByte() else byte)
    } while (current != 0)
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

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.varInt(
    property: KProperty1<T, Int>
) {
    addCodec<Int>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeVarInt(value)
        },
        decoder = { channel -> channel.readVarInt() }
    )
}