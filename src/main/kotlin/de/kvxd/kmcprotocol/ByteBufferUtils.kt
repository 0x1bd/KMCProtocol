package de.kvxd.kmcprotocol

import io.ktor.utils.io.core.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlin.text.toByteArray


object ByteBufferUtils {

    fun writeVarInt(sink: Sink, value: Int) {
        val bytes = VarInt.encode(value)
        sink.writeFully(bytes)
    }

    fun writeString(sink: Sink, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarInt(sink, bytes.size)
        sink.writeFully(bytes)
    }

    fun readVarInt(source: Source): Int {
        val bytes = ByteArray(5)
        var bytesRead = 0
        var result: Int

        do {
            bytes[bytesRead] = source.readByte()
            result = VarInt.decode(bytes, 0).first
            bytesRead++
        } while (bytesRead < 5 && bytes[bytesRead - 1].toInt() and 128 != 0)

        return result
    }

    fun readString(source: Source): String {
        val length = readVarInt(source)
        val bytes = source.readBytes(length)
        return bytes.toString(Charsets.UTF_8)
    }
}