package org.kvxd.kmcprotocol.core.encoding

import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtByteArray
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtDouble
import net.benwoodworth.knbt.NbtFloat
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtIntArray
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtLong
import net.benwoodworth.knbt.NbtLongArray
import net.benwoodworth.knbt.NbtShort
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import org.kvxd.kmcprotocol.core.KmcProtocolException

internal object NbtType {
    const val End = 0
    const val Byte = 1
    const val Short = 2
    const val Int = 3
    const val Long = 4
    const val Float = 5
    const val Double = 6
    const val ByteArray = 7
    const val String = 8
    const val List = 9
    const val Compound = 10
    const val IntArray = 11
    const val LongArray = 12
}

internal val EmptyNbtCompound = NbtCompound(emptyMap())

internal fun NbtTag.nbtTypeId(): Int =
    when (this) {
        is NbtByte -> NbtType.Byte
        is NbtShort -> NbtType.Short
        is NbtInt -> NbtType.Int
        is NbtLong -> NbtType.Long
        is NbtFloat -> NbtType.Float
        is NbtDouble -> NbtType.Double
        is NbtByteArray -> NbtType.ByteArray
        is NbtString -> NbtType.String
        is NbtList<*> -> NbtType.List
        is NbtCompound -> NbtType.Compound
        is NbtIntArray -> NbtType.IntArray
        is NbtLongArray -> NbtType.LongArray
    }

internal fun PacketWriter.writeNbtTag(tag: NbtTag) {
    writeByte(tag.nbtTypeId().toByte())
    writeNbtPayload(tag)
}

internal fun PacketWriter.writeNbtPayload(tag: NbtTag) {
    when (tag) {
        is NbtByte -> writeByte(tag.value)
        is NbtShort -> writeShort(tag.value)
        is NbtInt -> writeInt(tag.value)
        is NbtLong -> writeLong(tag.value)
        is NbtFloat -> writeFloat(tag.value)
        is NbtDouble -> writeDouble(tag.value)
        is NbtByteArray -> {
            writeInt(tag.size)
            tag.forEach { writeByte(it) }
        }
        is NbtString -> writeNbtString(tag.value)
        is NbtList<*> -> {
            writeByte((tag.firstOrNull()?.nbtTypeId() ?: NbtType.End).toByte())
            writeInt(tag.size)
            tag.forEach { writeNbtPayload(it) }
        }
        is NbtCompound -> {
            tag.forEach { (name, value) ->
                writeByte(value.nbtTypeId().toByte())
                writeNbtString(name)
                writeNbtPayload(value)
            }
            writeByte(NbtType.End.toByte())
        }
        is NbtIntArray -> {
            writeInt(tag.size)
            tag.forEach { writeInt(it) }
        }
        is NbtLongArray -> {
            writeInt(tag.size)
            tag.forEach { writeLong(it) }
        }
    }
}

internal fun PacketWriter.writeCompressedNbtTag(tag: NbtTag?) {
    if (tag == null) {
        writeShort(-1)
        return
    }

    val payload = PacketWriter().apply { writeNamedNbtTag(tag) }.toByteArray()
    val compressed = java.io.ByteArrayOutputStream().also { sink ->
        java.util.zip.GZIPOutputStream(sink).use { it.write(payload) }
    }.toByteArray()

    writeShort(compressed.size.toShort())
    writeBytes(compressed)
}

internal fun PacketReader.readCompressedNbtTag(): NbtTag? {
    val length = readShort().toInt()
    if (length < 0) return null

    val compressed = readBytes(length)
    val payload = java.util.zip.GZIPInputStream(compressed.inputStream()).use { it.readBytes() }
    return PacketReader(payload).readNamedNbtTag()
}

internal fun PacketWriter.writeNamedNbtTag(tag: NbtTag) {
    writeByte(tag.nbtTypeId().toByte())
    writeNbtString("")
    writeNbtPayload(tag)
}

internal fun PacketReader.readNamedNbtTag(): NbtTag? {
    val type = readByte().toInt() and 0xff
    if (type == NbtType.End) return null
    readNbtString()
    return readNbtPayload(type)
}

internal fun PacketReader.readNbtTag(): NbtTag? {
    val type = readByte().toInt() and 0xff
    return if (type == NbtType.End) null else readNbtPayload(type)
}

internal fun PacketReader.readNbtPayload(type: Int): NbtTag =
    when (type) {
        NbtType.Byte -> NbtByte(readByte())
        NbtType.Short -> NbtShort(readShort())
        NbtType.Int -> NbtInt(readInt())
        NbtType.Long -> NbtLong(readLong())
        NbtType.Float -> NbtFloat(readFloat())
        NbtType.Double -> NbtDouble(readDouble())
        NbtType.ByteArray -> NbtByteArray(ByteArray(readInt()) { readByte() })
        NbtType.String -> NbtString(readNbtString())
        NbtType.List -> readNbtList()
        NbtType.Compound -> readNbtCompound()
        NbtType.IntArray -> NbtIntArray(IntArray(readInt()) { readInt() })
        NbtType.LongArray -> NbtLongArray(LongArray(readInt()) { readLong() })
        else -> throw KmcProtocolException("Unsupported NBT tag type id: $type.")
    }

private fun PacketReader.readNbtList(): NbtList<*> {
    val elementType = readByte().toInt() and 0xff
    val count = readInt()
    return List(count) { readNbtPayload(elementType) }.toNbtList(elementType)
}

private fun PacketReader.readNbtCompound(): NbtCompound {
    val values = linkedMapOf<String, NbtTag>()
    while (true) {
        val type = readByte().toInt() and 0xff
        if (type == NbtType.End) return NbtCompound(values)
        values[readNbtString()] = readNbtPayload(type)
    }
}

private fun List<NbtTag>.toNbtList(elementType: Int): NbtList<*> =
    when (elementType) {
        NbtType.Byte, NbtType.End -> NbtList(filterIsInstance<NbtByte>())
        NbtType.Short -> NbtList(filterIsInstance<NbtShort>())
        NbtType.Int -> NbtList(filterIsInstance<NbtInt>())
        NbtType.Long -> NbtList(filterIsInstance<NbtLong>())
        NbtType.Float -> NbtList(filterIsInstance<NbtFloat>())
        NbtType.Double -> NbtList(filterIsInstance<NbtDouble>())
        NbtType.ByteArray -> NbtList(filterIsInstance<NbtByteArray>())
        NbtType.String -> NbtList(filterIsInstance<NbtString>())
        NbtType.List -> NbtList(filterIsInstance<NbtList<*>>())
        NbtType.Compound -> NbtList(filterIsInstance<NbtCompound>())
        NbtType.IntArray -> NbtList(filterIsInstance<NbtIntArray>())
        NbtType.LongArray -> NbtList(filterIsInstance<NbtLongArray>())
        else -> throw KmcProtocolException("Unsupported NBT list element type id: $elementType.")
    }

internal fun PacketWriter.writeNbtString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeShort(bytes.size.toShort())
    writeBytes(bytes)
}

internal fun PacketReader.readNbtString(): String =
    readBytes(readShort().toUShort().toInt()).toString(Charsets.UTF_8)
