package org.kvxd.kmcprotocol.extensions

import org.kvxd.kmcprotocol.core.KmcProtocolException
import org.kvxd.kmcprotocol.core.encoding.PacketWriter
import org.kvxd.kmcprotocol.extensions.chunk.ChunkFormat
import org.kvxd.kmcprotocol.extensions.chunk.ChunkSection
import org.kvxd.kmcprotocol.extensions.chunk.ChunkSections
import org.kvxd.kmcprotocol.extensions.chunk.Palette
import org.kvxd.kmcprotocol.extensions.chunk.PaletteKind
import org.kvxd.kmcprotocol.extensions.chunk.PalettedContainer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChunkSectionTest {

    private fun packed(bits: Int, entryCount: Int, value: (Int) -> Int): LongArray {
        val valuesPerLong = 64 / bits
        val longs = LongArray(PalettedContainer.longArraySize(bits, entryCount))
        for (index in 0 until entryCount) {
            val cell = index / valuesPerLong
            val shift = (index - cell * valuesPerLong) * bits
            longs[cell] = longs[cell] or ((value(index).toLong() and ((1L shl bits) - 1)) shl shift)
        }
        return longs
    }

    private fun singleValueSection() = ChunkSection(
        blockCount = 0,
        fluidCount = 0,
        blockStates = PalettedContainer.ofSingleValue(PaletteKind.BlockStates, 0),
        biomes = PalettedContainer.ofSingleValue(PaletteKind.Biomes, 4),
    )

    @Test
    fun `single value sections round trip`() {
        val format = ChunkFormat.v1_18(sectionCount = 3)
        val sections = List(3) { singleValueSection() }

        val bytes = ChunkSections.encode(sections, format)
        assertEquals(sections, ChunkSections.decode(bytes, format))
    }

    @Test
    fun `indirect palettes round trip and resolve through the palette`() {
        val format = ChunkFormat.v1_18(sectionCount = 1)
        val palette = intArrayOf(0, 9, 1, 80)
        val states = PalettedContainer(
            PaletteKind.BlockStates,
            bitsPerEntry = 4,
            palette = Palette.Indirect(palette),
            data = packed(4, 4096) { it % 4 },
        )
        val section = ChunkSection(4096, 0, states, PalettedContainer.ofSingleValue(PaletteKind.Biomes, 1))

        val decoded = ChunkSections.decode(ChunkSections.encode(listOf(section), format), format).single()
        assertEquals(section, decoded)

        assertEquals(0, decoded.blockStates[0])
        assertEquals(9, decoded.blockStates[1])
        assertEquals(1, decoded.blockStates[2])
        assertEquals(80, decoded.blockStates[3])
        assertEquals(9, decoded.blockStateAt(1, 0, 0))
    }

    @Test
    fun `direct palettes round trip`() {
        val format = ChunkFormat.v1_18(sectionCount = 1)
        val states = PalettedContainer(
            PaletteKind.BlockStates,
            bitsPerEntry = 15,
            palette = Palette.Direct,
            data = packed(15, 4096) { it % 20000 },
        )
        val section = ChunkSection(4096, 0, states, PalettedContainer.ofSingleValue(PaletteKind.Biomes, 0))

        val decoded = ChunkSections.decode(ChunkSections.encode(listOf(section), format), format).single()
        assertEquals(section, decoded)
        assertIs<Palette.Direct>(decoded.blockStates.palette)
        assertEquals(19, decoded.blockStates[19])
        assertEquals(4095 % 20000, decoded.blockStates[4095])
    }

    @Test
    fun `biome containers use the narrower indirect range`() {
        val format = ChunkFormat.v1_18(sectionCount = 1)

        val indirectBiomes = PalettedContainer(
            PaletteKind.Biomes,
            bitsPerEntry = 3,
            palette = Palette.Indirect(intArrayOf(1, 2, 3, 4, 5)),
            data = packed(3, 64) { it % 5 },
        )
        val directBiomes = PalettedContainer(
            PaletteKind.Biomes,
            bitsPerEntry = 4,
            palette = Palette.Direct,
            data = packed(4, 64) { it % 9 },
        )

        for (biomes in listOf(indirectBiomes, directBiomes)) {
            val section = ChunkSection(1, 0, PalettedContainer.ofSingleValue(PaletteKind.BlockStates, 1), biomes)
            val decoded = ChunkSections.decode(ChunkSections.encode(listOf(section), format), format).single()
            assertEquals(section, decoded)
        }

        assertIs<Palette.Indirect>(indirectBiomes.palette)
        assertIs<Palette.Direct>(directBiomes.palette)
    }

    @Test
    fun `every legal block bit width round trips`() {
        val format = ChunkFormat.v1_18(sectionCount = 1)
        for (bits in 1..16) {
            val palette = if (bits <= PaletteKind.BlockStates.maxIndirectBits) {
                Palette.Indirect(IntArray(1 shl bits) { it * 3 })
            } else {
                Palette.Direct
            }
            val states = PalettedContainer(
                PaletteKind.BlockStates,
                bits,
                palette,
                packed(bits, 4096) { it % (1 shl bits) },
            )
            val section = ChunkSection(1, 0, states, PalettedContainer.ofSingleValue(PaletteKind.Biomes, 0))
            val decoded = ChunkSections.decode(ChunkSections.encode(listOf(section), format), format).single()
            assertEquals(section, decoded, "bits=$bits")
        }
    }

    @Test
    fun `the 26 1 fluid count field round trips`() {
        val v26 = ChunkFormat.v26_1(sectionCount = 1)
        val section = ChunkSection(
            blockCount = 12,
            fluidCount = 7,
            blockStates = PalettedContainer.ofSingleValue(PaletteKind.BlockStates, 1),
            biomes = PalettedContainer.ofSingleValue(PaletteKind.Biomes, 2),
        )

        val decoded = ChunkSections.decode(ChunkSections.encode(listOf(section), v26), v26).single()
        assertEquals(7, decoded.fluidCount)
        assertEquals(section, decoded)
    }

    @Test
    fun `the long array length prefix differs between format generations`() {
        val prefixed = ChunkFormat.v1_18(sectionCount = 1)
        val bare = ChunkFormat.v1_21_5(sectionCount = 1)
        val states = PalettedContainer(
            PaletteKind.BlockStates,
            4,
            Palette.Indirect(intArrayOf(0, 1)),
            packed(4, 4096) { it % 2 },
        )
        val section = ChunkSection(1, 0, states, PalettedContainer.ofSingleValue(PaletteKind.Biomes, 0))

        val withPrefix = ChunkSections.encode(listOf(section), prefixed)
        val withoutPrefix = ChunkSections.encode(listOf(section), bare)

        assertEquals(withPrefix.size - 3, withoutPrefix.size)
        assertEquals(section, ChunkSections.decode(withoutPrefix, bare).single())
        assertFailsWith<KmcProtocolException> { ChunkSections.decode(withoutPrefix, prefixed) }
    }

    @Test
    fun `a wrong section count is reported instead of silently truncating`() {
        val format = ChunkFormat.v1_18(sectionCount = 2)
        val bytes = ChunkSections.encode(List(2) { singleValueSection() }, format)

        val failure = assertFailsWith<KmcProtocolException> {
            ChunkSections.decode(bytes, ChunkFormat.v1_18(sectionCount = 1))
        }
        assertTrue(failure.message!!.contains("trailing bytes"), failure.message!!)
    }

    @Test
    fun `a truncated container is rejected rather than allocating`() {
        val writer = PacketWriter()
        writer.writeShort(0)
        writer.writeByte(8)
        writer.writeVarInt(2)
        writer.writeVarInt(0)
        writer.writeVarInt(1)
        writer.writeVarInt(512)

        assertFailsWith<KmcProtocolException> {
            ChunkSections.decode(writer.toByteArray(), ChunkFormat.v1_18(sectionCount = 1))
        }
    }

    @Test
    fun `chunk biome sections round trip`() {
        val format = ChunkFormat.v1_18(sectionCount = 4)
        val containers = List(4) { index ->
            PalettedContainer.ofSingleValue(PaletteKind.Biomes, index)
        }

        val bytes = ChunkSections.encodeBiomes(containers, format)
        assertEquals(containers, ChunkSections.decodeBiomes(bytes, format))
    }

    @Test
    fun `toIntArray expands every entry through the palette`() {
        val states = PalettedContainer(
            PaletteKind.Biomes,
            2,
            Palette.Indirect(intArrayOf(7, 8, 9, 10)),
            packed(2, 64) { it % 4 },
        )
        assertContentEquals(IntArray(64) { 7 + it % 4 }, states.toIntArray())
    }
}
