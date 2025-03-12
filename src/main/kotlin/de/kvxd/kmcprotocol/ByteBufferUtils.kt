package de.kvxd.kmcprotocol

import java.nio.ByteBuffer

object ByteBufferUtils {
    fun writeVarInt(buffer: ByteBuffer, value: Int) {
        val bytes = VarInt.encode(value)
        buffer.put(bytes)
    }

    fun writeString(buffer: ByteBuffer, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarInt(buffer, bytes.size)
        buffer.put(bytes)
    }

    fun readVarInt(buffer: ByteBuffer): Int {
        val bytes = ByteArray(5)
        var bytesRead = 0
        var result: Int
        
        do {
            bytes[bytesRead] = buffer.get()
            result = VarInt.decode(bytes, 0).first
            bytesRead++
        } while (bytesRead < 5 && bytes[bytesRead - 1].toInt() and 128 != 0)

        return result
    }

    fun readString(buffer: ByteBuffer): String {
        val length = readVarInt(buffer)
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}