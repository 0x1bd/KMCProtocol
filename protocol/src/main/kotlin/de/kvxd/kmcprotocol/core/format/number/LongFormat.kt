package de.kvxd.kmcprotocol.core.format.number

import io.ktor.utils.io.*
import kotlinx.io.Sink
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
annotation class LongFormat(val format: LongFormatType)

enum class LongFormatType {
    /** Fixed 8-byte long */
    FIXED,

    /** Variable-length (1-10 bytes) long */
    VARIABLE
}

fun Sink.writeVarLong(value: Long) {
    var currentValue = value
    while (true) {
        if ((currentValue and Long.MAX_VALUE.inv()) == 0L) {
            writeByte(currentValue.toByte())
            return
        }
        writeByte(((currentValue and 0x7F) or 0x80).toByte())
        currentValue = currentValue ushr 7
    }
}

suspend fun ByteReadChannel.readVarLong(): Long {
    var result = 0L
    var shift = 0
    while (shift < 64) {
        val byte = readByte().toLong() and 0xFFL
        result = result or ((byte and 0x7F) shl shift)
        if ((byte and 0x80) == 0L) {
            return result
        }
        shift += 7
    }
    throw RuntimeException("VarLong too large (exceeds 64 bits)")
}