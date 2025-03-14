package de.kvxd.kmcprotocol.serialization

import io.ktor.utils.io.core.*
import kotlinx.io.*
import java.io.DataInput

class SourceDataInput(private val source: Source) : DataInput {

    override fun readFully(b: ByteArray) {
        source.readFully(b)
    }

    override fun readFully(b: ByteArray, off: Int, len: Int) {
        source.readFully(b, off, len)
    }

    // source.skip is not available since it does not return the actually skipped number of bytes
    override fun skipBytes(n: Int): Int {
        var bytesSkipped = 0
        try {
            while (bytesSkipped < n) {
                source.readByte()
                bytesSkipped++
            }
        } catch (e: EOFException) {
            // End of source reached
        }
        return bytesSkipped
    }

    override fun readBoolean(): Boolean {
        return source.readByte() != 0.toByte()
    }

    override fun readByte(): Byte {
        return source.readByte()
    }

    override fun readUnsignedByte(): Int {
        return source.readUByte().toInt()
    }

    override fun readShort(): Short {
        return source.readShort()
    }

    override fun readUnsignedShort(): Int {
        return source.readUShort().toInt()
    }

    override fun readChar(): Char {
        return source.readByte().toInt().toChar()
    }

    override fun readInt(): Int {
        return source.readInt()
    }

    override fun readLong(): Long {
        return source.readLong()
    }

    override fun readFloat(): Float {
        return source.readFloat()
    }

    override fun readDouble(): Double {
        return source.readDouble()
    }

    override fun readLine(): String {
        throw UnsupportedOperationException("readLine is not supported")
    }

    override fun readUTF(): String {
        val length = readUnsignedShort()
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}