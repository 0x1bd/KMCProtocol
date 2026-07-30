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

internal val SampleNbtCompound = NbtCompound(mapOf("text" to NbtString("sample")))

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

    if (compressed.size > Short.MAX_VALUE) {
        throw KmcProtocolException(
            "Compressed NBT is ${compressed.size} bytes, which exceeds the ${Short.MAX_VALUE} byte " +
                "maximum encodable by its length prefix."
        )
    }

    writeShort(compressed.size.toShort())
    writeBytes(compressed)
}

internal fun PacketReader.readCompressedNbtTag(): NbtTag? {
    val length = readShort().toInt()
    if (length < 0) return null

    val compressed = readBytes(length)
    val payload = compressed.gunzip(limits.maxCompressedNbtLength)
    return PacketReader(payload, limits = limits).readNamedNbtTag()
}

private fun ByteArray.gunzip(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(limit.toLong(), size * 4L).coerceAtLeast(32L).toInt())
    java.util.zip.GZIPInputStream(inputStream()).use { input ->
        val chunk = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw KmcProtocolException("Compressed NBT expands beyond the $limit byte limit.")
            }
            output.write(chunk, 0, read)
        }
    }
    return output.toByteArray()
}

internal fun PacketWriter.writeNamedNbtTag(tag: NbtTag) {
    writeByte(tag.nbtTypeId().toByte())
    writeNbtString("")
    writeNbtPayload(tag)
}

internal fun PacketReader.readNamedNbtTag(depth: Int = 0): NbtTag? {
    val type = readByte().toInt() and 0xff
    if (type == NbtType.End) return null
    readNbtString()
    return readNbtPayload(type, depth)
}

internal fun PacketReader.readNbtTag(depth: Int = 0): NbtTag? {
    val type = readByte().toInt() and 0xff
    return if (type == NbtType.End) null else readNbtPayload(type, depth)
}

internal fun PacketReader.readNbtPayload(type: Int, depth: Int = 0): NbtTag {
    if (depth > limits.maxNbtDepth) {
        throw KmcProtocolException("NBT nesting exceeded the maximum depth of ${limits.maxNbtDepth}.")
    }

    return when (type) {
        NbtType.Byte -> NbtByte(readByte())
        NbtType.Short -> NbtShort(readShort())
        NbtType.Int -> NbtInt(readInt())
        NbtType.Long -> NbtLong(readLong())
        NbtType.Float -> NbtFloat(readFloat())
        NbtType.Double -> NbtDouble(readDouble())
        NbtType.ByteArray -> NbtByteArray(ByteArray(readNbtCount(1, "NBT byte array")) { readByte() })
        NbtType.String -> NbtString(readNbtString())
        NbtType.List -> readNbtList(depth + 1)
        NbtType.Compound -> readNbtCompound(depth + 1)
        NbtType.IntArray -> NbtIntArray(IntArray(readNbtCount(4, "NBT int array")) { readInt() })
        NbtType.LongArray -> NbtLongArray(LongArray(readNbtCount(8, "NBT long array")) { readLong() })
        else -> throw KmcProtocolException("Unsupported NBT tag type id: $type.")
    }
}

private fun PacketReader.readNbtCount(minimumBytesPerElement: Int, what: String): Int =
    requireElementCount(readInt(), minimumBytesPerElement, what)

private fun nbtMinimumPayloadSize(elementType: Int): Int =
    when (elementType) {
        NbtType.Byte -> 1
        NbtType.Short -> 2
        NbtType.Int, NbtType.Float, NbtType.ByteArray, NbtType.IntArray, NbtType.LongArray -> 4
        NbtType.Long, NbtType.Double -> 8
        NbtType.String -> 2
        NbtType.List -> 5
        else -> 1
    }

private fun PacketReader.readNbtList(depth: Int): NbtList<*> {
    val elementType = readByte().toInt() and 0xff
    val declaredCount = readInt()

    if (elementType == NbtType.End) {
        if (declaredCount != 0) {
            throw KmcProtocolException("NBT list declares element type END but contains $declaredCount elements.")
        }
        return NbtList(emptyList<NbtByte>())
    }

    val count = requireElementCount(declaredCount, nbtMinimumPayloadSize(elementType), "NBT list")
    return List(count) { readNbtPayload(elementType, depth) }.toNbtList(elementType)
}

private fun PacketReader.readNbtCompound(depth: Int): NbtCompound {
    val values = linkedMapOf<String, NbtTag>()
    while (true) {
        val type = readByte().toInt() and 0xff
        if (type == NbtType.End) return NbtCompound(values)
        values[readNbtString()] = readNbtPayload(type, depth)
    }
}

private inline fun <reified T : NbtTag> List<NbtTag>.nbtElements(elementType: Int): List<T> =
    map { tag ->
        tag as? T ?: throw KmcProtocolException(
            "NBT list declares element type $elementType but contained a ${tag::class.simpleName}."
        )
    }

private fun List<NbtTag>.toNbtList(elementType: Int): NbtList<*> =
    when (elementType) {
        NbtType.Byte -> NbtList(nbtElements<NbtByte>(elementType))
        NbtType.Short -> NbtList(nbtElements<NbtShort>(elementType))
        NbtType.Int -> NbtList(nbtElements<NbtInt>(elementType))
        NbtType.Long -> NbtList(nbtElements<NbtLong>(elementType))
        NbtType.Float -> NbtList(nbtElements<NbtFloat>(elementType))
        NbtType.Double -> NbtList(nbtElements<NbtDouble>(elementType))
        NbtType.ByteArray -> NbtList(nbtElements<NbtByteArray>(elementType))
        NbtType.String -> NbtList(nbtElements<NbtString>(elementType))
        NbtType.List -> NbtList(nbtElements<NbtList<*>>(elementType))
        NbtType.Compound -> NbtList(nbtElements<NbtCompound>(elementType))
        NbtType.IntArray -> NbtList(nbtElements<NbtIntArray>(elementType))
        NbtType.LongArray -> NbtList(nbtElements<NbtLongArray>(elementType))
        else -> throw KmcProtocolException("Unsupported NBT list element type id: $elementType.")
    }

internal fun PacketWriter.writeNbtString(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    if (bytes.size > 0xFFFF) {
        throw KmcProtocolException(
            "NBT string is ${bytes.size} bytes, which exceeds the 65535 byte maximum encodable " +
                "by its length prefix."
        )
    }
    writeShort(bytes.size.toShort())
    writeBytes(bytes)
}

internal fun PacketReader.readNbtString(): String =
    readBytes(readShort().toUShort().toInt()).toString(Charsets.UTF_8)
