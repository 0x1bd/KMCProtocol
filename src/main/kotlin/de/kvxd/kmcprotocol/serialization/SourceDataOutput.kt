package de.kvxd.kmcprotocol.serialization

import kotlinx.io.Sink
import java.io.DataOutput
import java.io.UTFDataFormatException
import java.nio.charset.Charset

class SourceDataOutput(private val sink: Sink) : DataOutput {

    override fun write(b: Int) {
        sink.writeByte(b.toByte())
    }

    override fun write(b: ByteArray) {
        sink.write(b, 0, b.size)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        sink.write(b, off, len)
    }

    override fun writeBoolean(v: Boolean) {
        sink.writeByte(if (v) 1 else 0)
    }

    override fun writeByte(v: Int) {
        sink.writeByte(v.toByte())
    }

    override fun writeShort(v: Int) {
        sink.writeShort(v.toShort())
    }

    override fun writeChar(v: Int) {
        sink.writeByte((v ushr 8).toByte())
        sink.writeByte(v.toByte())
    }

    override fun writeInt(v: Int) {
        sink.writeInt(v)
    }

    override fun writeLong(v: Long) {
        sink.writeLong(v)
    }

    override fun writeFloat(v: Float) {
        sink.writeInt(v.toRawBits())
    }

    override fun writeDouble(v: Double) {
        sink.writeLong(v.toRawBits())
    }

    override fun writeBytes(s: String) {
        val bytes = s.toByteArray(Charset.defaultCharset())
        write(bytes)
    }

    override fun writeChars(s: String) {
        for (char in s) {
            writeChar(char.code)
        }
    }

    override fun writeUTF(s: String) {
        val utfBytes = s.toByteArray(Charsets.UTF_8)
        if (utfBytes.size > 65535) {
            throw UTFDataFormatException("encoded string too long: ${utfBytes.size} bytes")
        }
        writeShort(utfBytes.size)
        write(utfBytes)
    }
}