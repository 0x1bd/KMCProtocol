package de.kvxd.kmcprotocol.datatypes

import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.experimental.and

/** From Gabi's medium post. [Source](https://aripiprazole.medium.com/writing-a-minecraft-protocol-implementation-in-kotlin-9276c584bd42) **/

internal fun ByteWriteChannel.writeVarInt(int: Int) = runBlocking {
    var value = int

    while (true) {
        if ((int and 0xFFFFFF80.toInt()) == 0) {
            writeByte(value.toByte())
            return@runBlocking
        }

        writeByte(((value and 0x7F) or 0x80).toByte())
        value = value ushr 7
    }
}

internal fun ByteReadChannel.readVarInt(): Int = runBlocking {
    var offset = 0
    var value = 0L
    var byte: Byte

    do {
        if (offset == 35) error("VarInt too long")

        byte = readByte()
        value = value or ((byte.toLong() and 0x7FL) shl offset)

        offset += 7
    } while ((byte and  0x80.toByte()) != 0.toByte())

    return@runBlocking value.toInt()
}