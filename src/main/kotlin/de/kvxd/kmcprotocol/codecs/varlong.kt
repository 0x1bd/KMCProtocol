package de.kvxd.kmcprotocol.codecs

import de.kvxd.kmcprotocol.packet.MinecraftPacket
import de.kvxd.kmcprotocol.packet.PacketCodec
import io.ktor.utils.io.*
import kotlin.experimental.and
import kotlin.reflect.KProperty1

/** From Gabi's medium post. [Source](https://aripiprazole.medium.com/writing-a-minecraft-protocol-implementation-in-kotlin-9276c584bd42) **/

internal suspend fun ByteWriteChannel.writeVarLong(value: Long) {
    var current = value
    do {
        val byte = (current and 0x7F).toByte()
        current = current ushr 7
        writeByte(if (current != 0L) (byte.toInt() or 0x80).toByte() else byte)
    } while (current != 0L)
}

internal suspend fun ByteReadChannel.readVarLong(): Long {
    var value = 0L
    var bytesRead = 0
    var offset = 0
    var byte: Byte

    do {
        if (bytesRead >= 10) error("VarLong too long")
        byte = readByte()
        val segment = byte.toLong() and 0x7F
        value = value or (segment shl offset)
        offset += 7
        bytesRead++
    } while ((byte and 0x80.toByte()) != 0.toByte())

    return value
}

fun <T : MinecraftPacket> PacketCodec.PacketCodecBuilder<T>.varLong(
    property: KProperty1<T, Long>
) {
    addCodec<Long>(
        encoder = { packet, channel ->
            val value = property.get(packet)
            channel.writeVarLong(value)
        },
        decoder = { channel -> channel.readVarLong() }
    )
}