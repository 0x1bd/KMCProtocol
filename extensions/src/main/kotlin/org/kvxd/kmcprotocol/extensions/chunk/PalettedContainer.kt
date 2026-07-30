package org.kvxd.kmcprotocol.extensions.chunk

import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.encoding.PacketReader
import org.kvxd.kmcprotocol.core.encoding.PacketWriter

sealed interface Palette {

    fun valueFor(raw: Int): Int

    data class Single(val value: Int) : Palette {
        override fun valueFor(raw: Int): Int = value
    }

    class Indirect(val values: IntArray) : Palette {
        override fun valueFor(raw: Int): Int =
            values.getOrElse(raw) {
                throw KmcProtocolException("Palette index $raw is out of bounds for ${values.size} entries.")
            }

        override fun equals(other: Any?): Boolean =
            this === other || (other is Indirect && values.contentEquals(other.values))

        override fun hashCode(): Int = values.contentHashCode()

        override fun toString(): String = "Indirect(${values.contentToString()})"
    }

    data object Direct : Palette {
        override fun valueFor(raw: Int): Int = raw
    }
}

class PalettedContainer(
    val kind: PaletteKind,
    val bitsPerEntry: Int,
    val palette: Palette,
    val data: LongArray,
) {
    init {
        require(bitsPerEntry in 0..64) { "bitsPerEntry must be in 0..64 but was $bitsPerEntry" }
        val expected = longArraySize(bitsPerEntry, kind.entryCount)
        require(data.size == expected) {
            "A $bitsPerEntry bit container over ${kind.entryCount} entries needs $expected longs but got ${data.size}"
        }
    }

    operator fun get(index: Int): Int {
        require(index in 0 until kind.entryCount) {
            "Index $index is out of bounds for ${kind.entryCount} entries"
        }
        if (bitsPerEntry == 0) return palette.valueFor(0)

        val valuesPerLong = 64 / bitsPerEntry
        val cellIndex = index / valuesPerLong
        val bitIndex = (index - cellIndex * valuesPerLong) * bitsPerEntry
        val mask = if (bitsPerEntry == 64) -1L else (1L shl bitsPerEntry) - 1L
        return palette.valueFor(((data[cellIndex] ushr bitIndex) and mask).toInt())
    }

    fun toIntArray(): IntArray = IntArray(kind.entryCount) { this[it] }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is PalettedContainer &&
                kind == other.kind &&
                bitsPerEntry == other.bitsPerEntry &&
                palette == other.palette &&
                data.contentEquals(other.data)
            )

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + bitsPerEntry
        result = 31 * result + palette.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    override fun toString(): String =
        "PalettedContainer($kind, bits=$bitsPerEntry, palette=$palette, longs=${data.size})"

    companion object {

        fun longArraySize(bitsPerEntry: Int, entryCount: Int): Int {
            if (bitsPerEntry == 0) return 0
            val valuesPerLong = 64 / bitsPerEntry
            return (entryCount + valuesPerLong - 1) / valuesPerLong
        }

        fun ofSingleValue(kind: PaletteKind, value: Int): PalettedContainer =
            PalettedContainer(kind, 0, Palette.Single(value), LongArray(0))

        fun read(reader: PacketReader, kind: PaletteKind, format: ChunkFormat): PalettedContainer {
            val bitsPerEntry = reader.readByte().toInt() and 0xff
            if (bitsPerEntry > 64) {
                throw KmcProtocolException("Palette container declares $bitsPerEntry bits per entry, which exceeds 64.")
            }

            val palette = when {
                bitsPerEntry == 0 -> Palette.Single(reader.readVarInt())
                bitsPerEntry <= kind.maxIndirectBits -> {
                    val count = reader.requireElementCount(reader.readVarInt(), 1, "$kind palette")
                    Palette.Indirect(IntArray(count) { reader.readVarInt() })
                }
                else -> Palette.Direct
            }

            val expected = longArraySize(bitsPerEntry, kind.entryCount)
            if (format.longArrayLengthPrefixed) {
                val declared = reader.readVarInt()
                if (declared != expected) {
                    throw KmcProtocolException(
                        "$kind container declares $declared longs but a $bitsPerEntry bit container " +
                            "over ${kind.entryCount} entries needs $expected."
                    )
                }
            }
            reader.requireElementCount(expected, 8, "$kind container data")

            return PalettedContainer(kind, bitsPerEntry, palette, LongArray(expected) { reader.readLong() })
        }
    }

    fun write(writer: PacketWriter, format: ChunkFormat) {
        writer.writeByte(bitsPerEntry.toByte())
        when (palette) {
            is Palette.Single -> writer.writeVarInt(palette.value)
            is Palette.Indirect -> {
                writer.writeVarInt(palette.values.size)
                palette.values.forEach(writer::writeVarInt)
            }
            Palette.Direct -> Unit
        }
        if (format.longArrayLengthPrefixed) writer.writeVarInt(data.size)
        data.forEach(writer::writeLong)
    }
}
