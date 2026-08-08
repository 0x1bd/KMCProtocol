package org.kvxd.kmcprotocol.core.encoding

import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolLimits
import java.util.UUID

private const val MaxBufferSize = Int.MAX_VALUE - 8

class PacketWriter(initialCapacity: Int = 64, val nbt: NbtFormat = NbtFormat.Latest) {

    private var buffer = ByteArray(initialCapacity.coerceAtLeast(16).coerceAtMost(MaxBufferSize))
    private var length = 0

    val size: Int
        get() = length

    fun writeByte(value: Byte) {
        ensure(1)
        buffer[length++] = value
    }

    fun writeShort(value: Short) {
        ensure(2)
        val v = value.toInt()
        buffer[length++] = (v ushr 8).toByte()
        buffer[length++] = v.toByte()
    }

    fun writeInt(value: Int) {
        ensure(4)
        buffer[length++] = (value ushr 24).toByte()
        buffer[length++] = (value ushr 16).toByte()
        buffer[length++] = (value ushr 8).toByte()
        buffer[length++] = value.toByte()
    }

    fun writeLong(value: Long) {
        ensure(8)
        for (shift in 56 downTo 0 step 8) {
            buffer[length++] = (value ushr shift).toByte()
        }
    }

    fun writeFloat(value: Float) = writeInt(value.toRawBits())

    fun writeDouble(value: Double) = writeLong(value.toRawBits())

    fun writeBytes(value: ByteArray, offset: Int = 0, count: Int = value.size - offset) {
        ensure(count)
        value.copyInto(buffer, length, offset, offset + count)
        length += count
    }

    fun writeVarInt(value: Int) {
        var current = value
        while (true) {
            if (current and 0x7f.inv() == 0) {
                writeByte(current.toByte())
                return
            }
            writeByte(((current and 0x7f) or 0x80).toByte())
            current = current ushr 7
        }
    }

    fun writeVarLong(value: Long) {
        var current = value
        while (true) {
            if (current and 0x7fL.inv() == 0L) {
                writeByte(current.toByte())
                return
            }
            writeByte(((current and 0x7f) or 0x80).toByte())
            current = current ushr 7
        }
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarInt(bytes.size)
        writeBytes(bytes)
    }

    fun writeUuid(value: UUID) {
        writeLong(value.mostSignificantBits)
        writeLong(value.leastSignificantBits)
    }

    fun writeNumber(encoding: NumberEncoding, value: Number) {
        when (encoding) {
            NumberEncoding.Byte -> writeByte(value.toByte())
            NumberEncoding.UnsignedByte -> writeByte(value.toInt().toByte())
            NumberEncoding.Short -> writeShort(value.toShort())
            NumberEncoding.UnsignedShort -> writeShort(value.toInt().toShort())
            NumberEncoding.Int -> writeInt(value.toInt())
            NumberEncoding.UnsignedInt -> writeInt(value.toLong().toInt())
            NumberEncoding.VarInt -> writeVarInt(value.toInt())
            NumberEncoding.Long -> writeLong(value.toLong())
            NumberEncoding.VarLong -> writeVarLong(value.toLong())
            NumberEncoding.Float -> writeFloat(value.toFloat())
            NumberEncoding.Double -> writeDouble(value.toDouble())
        }
    }

    fun writeSizedInteger(value: Long, bits: Int) {
        when {
            bits <= 8 -> writeByte(value.toByte())
            bits <= 16 -> writeShort(value.toShort())
            bits <= 32 -> writeInt(value.toInt())
            else -> writeLong(value)
        }
    }

    fun toByteArray(): ByteArray = buffer.copyOf(length)

    private fun ensure(extra: Int) {
        if (extra < 0) throw KmcProtocolException("Attempted to write a negative byte count: $extra.")

        val required = length.toLong() + extra
        if (required > MaxBufferSize) {
            throw KmcProtocolException("Packet would exceed the maximum encodable size of $MaxBufferSize bytes.")
        }
        if (required <= buffer.size) return

        var capacity = buffer.size.toLong()
        while (capacity < required) capacity = capacity shl 1
        buffer = buffer.copyOf(capacity.coerceAtMost(MaxBufferSize.toLong()).toInt())
    }
}

class PacketReader(
    private val buffer: ByteArray,
    private var position: Int = 0,
    val limits: ProtocolLimits = ProtocolLimits.Default,
    val nbt: NbtFormat = NbtFormat.Latest,
) {

    val remaining: Int
        get() = buffer.size - position

    fun requireElementCount(count: Int, minimumBytesPerElement: Int, what: String): Int {
        if (count < 0) throw KmcProtocolException("$what declares a negative element count: $count.")

        val needed = count.toLong() * minimumBytesPerElement.coerceAtLeast(1)
        if (needed > remaining) {
            throw KmcProtocolException(
                "$what declares $count elements requiring at least $needed bytes, " +
                    "but only $remaining bytes remain in the packet."
            )
        }
        return count
    }

    fun readByte(): Byte {
        require(1)
        return buffer[position++]
    }

    fun peekUnsignedByte(): Int {
        require(1)
        return buffer[position].toInt() and 0xff
    }

    fun readShort(): Short {
        require(2)
        val value = ((buffer[position].toInt() and 0xff) shl 8) or (buffer[position + 1].toInt() and 0xff)
        position += 2
        return value.toShort()
    }

    fun readInt(): Int {
        require(4)
        var value = 0
        repeat(4) { value = (value shl 8) or (buffer[position++].toInt() and 0xff) }
        return value
    }

    fun readLong(): Long {
        require(8)
        var value = 0L
        repeat(8) { value = (value shl 8) or (buffer[position++].toLong() and 0xff) }
        return value
    }

    fun readFloat(): Float = Float.fromBits(readInt())

    fun readDouble(): Double = Double.fromBits(readLong())

    fun readBytes(count: Int): ByteArray {
        require(count)
        val value = buffer.copyOfRange(position, position + count)
        position += count
        return value
    }

    fun readInto(destination: ByteArray, offset: Int, count: Int) {
        require(count)
        buffer.copyInto(destination, offset, position, position + count)
        position += count
    }

    fun readRemaining(): ByteArray = readBytes(remaining)

    fun readVarInt(): Int {
        var offset = 0
        var value = 0
        while (true) {
            if (offset == 35) throw KmcProtocolException("VarInt exceeded 5 bytes.")
            val byte = readByte().toInt()
            if (offset == 28 && byte and 0x70 != 0) {
                throw KmcProtocolException("VarInt final byte 0x${byte.toString(16)} sets bits beyond 32.")
            }
            value = value or ((byte and 0x7f) shl offset)
            if (byte and 0x80 == 0) return value
            offset += 7
        }
    }

    fun readVarLong(): Long {
        var offset = 0
        var value = 0L
        while (true) {
            if (offset == 70) throw KmcProtocolException("VarLong exceeded 10 bytes.")
            val byte = readByte().toInt()
            if (offset == 63 && byte and 0x7e != 0) {
                throw KmcProtocolException("VarLong final byte 0x${byte.toString(16)} sets bits beyond 64.")
            }
            value = value or ((byte.toLong() and 0x7f) shl offset)
            if (byte and 0x80 == 0) return value
            offset += 7
        }
    }

    fun readString(): String = readBytes(readVarInt()).toString(Charsets.UTF_8)

    fun readUuid(): UUID = UUID(readLong(), readLong())

    fun readNumber(encoding: NumberEncoding): Number =
        when (encoding) {
            NumberEncoding.Byte -> readByte()
            NumberEncoding.UnsignedByte -> readByte().toUByte().toShort()
            NumberEncoding.Short -> readShort()
            NumberEncoding.UnsignedShort -> readShort().toUShort().toInt()
            NumberEncoding.Int -> readInt()
            NumberEncoding.UnsignedInt -> readInt().toUInt().toLong()
            NumberEncoding.VarInt -> readVarInt()
            NumberEncoding.Long -> readLong()
            NumberEncoding.VarLong -> readVarLong()
            NumberEncoding.Float -> readFloat()
            NumberEncoding.Double -> readDouble()
        }

    fun readSizedInteger(bits: Int): Long =
        when {
            bits <= 8 -> readByte().toLong() and 0xff
            bits <= 16 -> readShort().toLong() and 0xffff
            bits <= 32 -> readInt().toLong() and 0xffffffffL
            else -> readLong()
        }

    private fun require(count: Int) {
        if (count < 0) throw KmcProtocolException("Attempted to read a negative byte count: $count.")
        if (remaining < count) {
            throw KmcProtocolException("Packet ended early: needed $count more bytes but only $remaining remain.")
        }
    }
}
