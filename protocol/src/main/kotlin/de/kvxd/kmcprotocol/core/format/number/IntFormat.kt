package de.kvxd.kmcprotocol.core.format.number

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.Sink
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
annotation class IntFormat(val format: IntFormatType)

enum class IntFormatType {
    /* Fixed 4-byte integer */
    FIXED,

    /* Variable-length (1-5) byte integer */
    VARIABLE
}

fun Sink.writeVarInt(value: Int) {
    var currentValue = value
    while (true) {
        if ((currentValue and Int.MAX_VALUE.inv()) == 0) {
            writeByte(currentValue.toByte())
            return
        }
        writeByte(((currentValue and 0x7F or 0x80).toByte()))
        currentValue = currentValue ushr 7
    }
}

suspend fun ByteReadChannel.readVarInt(): Int {
    var result = 0
    var shift = 0
    while (true) {
        val byte = readByte().toInt()
        result = result or (byte and 0x7F shl shift)
        shift += 7
        if (byte and 0x80 == 0) {
            return result
        }
        if (shift >= 32) {
            throw RuntimeException("VarInt is too big")
        }
    }
}
