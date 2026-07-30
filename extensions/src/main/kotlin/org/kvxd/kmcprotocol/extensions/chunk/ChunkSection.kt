package org.kvxd.kmcprotocol.extensions.chunk

import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.ProtocolLimits
import org.kvxd.kmcprotocol.core.encoding.PacketReader
import org.kvxd.kmcprotocol.core.encoding.PacketWriter

class ChunkSection(
    val blockCount: Int,
    val fluidCount: Int,
    val blockStates: PalettedContainer,
    val biomes: PalettedContainer,
) {
    init {
        require(blockStates.kind == PaletteKind.BlockStates) {
            "blockStates must be a BlockStates container but was ${blockStates.kind}"
        }
        require(biomes.kind == PaletteKind.Biomes) {
            "biomes must be a Biomes container but was ${biomes.kind}"
        }
    }

    fun blockStateAt(x: Int, y: Int, z: Int): Int = blockStates[blockIndex(x, y, z)]

    fun biomeAt(x: Int, y: Int, z: Int): Int = biomes[biomeIndex(x, y, z)]

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ChunkSection &&
                blockCount == other.blockCount &&
                fluidCount == other.fluidCount &&
                blockStates == other.blockStates &&
                biomes == other.biomes
            )

    override fun hashCode(): Int {
        var result = blockCount
        result = 31 * result + fluidCount
        result = 31 * result + blockStates.hashCode()
        result = 31 * result + biomes.hashCode()
        return result
    }

    override fun toString(): String = "ChunkSection(blockCount=$blockCount, fluidCount=$fluidCount)"

    companion object {

        fun blockIndex(x: Int, y: Int, z: Int): Int {
            require(x in 0..15 && y in 0..15 && z in 0..15) { "Block coordinates must be in 0..15" }
            return (y shl 8) or (z shl 4) or x
        }

        fun biomeIndex(x: Int, y: Int, z: Int): Int {
            require(x in 0..3 && y in 0..3 && z in 0..3) { "Biome coordinates must be in 0..3" }
            return (y shl 4) or (z shl 2) or x
        }

        fun read(reader: PacketReader, format: ChunkFormat): ChunkSection {
            val blockCount = reader.readShort().toInt()
            val fluidCount = if (format.hasFluidCount) reader.readShort().toInt() else 0
            return ChunkSection(
                blockCount = blockCount,
                fluidCount = fluidCount,
                blockStates = PalettedContainer.read(reader, PaletteKind.BlockStates, format),
                biomes = PalettedContainer.read(reader, PaletteKind.Biomes, format),
            )
        }
    }

    fun write(writer: PacketWriter, format: ChunkFormat) {
        writer.writeShort(blockCount.toShort())
        if (format.hasFluidCount) writer.writeShort(fluidCount.toShort())
        blockStates.write(writer, format)
        biomes.write(writer, format)
    }
}

object ChunkSections {

    fun decode(
        chunkData: ByteArray,
        format: ChunkFormat,
        limits: ProtocolLimits = ProtocolLimits.Default,
    ): List<ChunkSection> {
        val reader = PacketReader(chunkData, limits = limits)
        val sections = List(format.sectionCount) { ChunkSection.read(reader, format) }
        if (reader.remaining > 0) {
            throw KmcProtocolException(
                "Chunk data left ${reader.remaining} trailing bytes after ${format.sectionCount} sections; " +
                    "the section count or chunk format is wrong for this world."
            )
        }
        return sections
    }

    fun encode(sections: List<ChunkSection>, format: ChunkFormat): ByteArray {
        require(sections.size == format.sectionCount) {
            "Expected ${format.sectionCount} sections but got ${sections.size}"
        }
        val writer = PacketWriter(sections.size * 256)
        sections.forEach { it.write(writer, format) }
        return writer.toByteArray()
    }

    fun decodeBiomes(
        biomeData: ByteArray,
        format: ChunkFormat,
        limits: ProtocolLimits = ProtocolLimits.Default,
    ): List<PalettedContainer> {
        val reader = PacketReader(biomeData, limits = limits)
        val containers = List(format.sectionCount) {
            PalettedContainer.read(reader, PaletteKind.Biomes, format)
        }
        if (reader.remaining > 0) {
            throw KmcProtocolException(
                "Chunk biome data left ${reader.remaining} trailing bytes after ${format.sectionCount} sections."
            )
        }
        return containers
    }

    fun encodeBiomes(containers: List<PalettedContainer>, format: ChunkFormat): ByteArray {
        require(containers.size == format.sectionCount) {
            "Expected ${format.sectionCount} biome containers but got ${containers.size}"
        }
        val writer = PacketWriter(containers.size * 32)
        containers.forEach { it.write(writer, format) }
        return writer.toByteArray()
    }
}
