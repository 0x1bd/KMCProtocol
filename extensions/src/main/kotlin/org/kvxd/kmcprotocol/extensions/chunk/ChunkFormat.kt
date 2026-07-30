package org.kvxd.kmcprotocol.extensions.chunk

class ChunkFormat(
    val sectionCount: Int,
    val hasFluidCount: Boolean = false,
    val longArrayLengthPrefixed: Boolean = true,
) {
    init {
        require(sectionCount >= 0) { "sectionCount must not be negative but was $sectionCount" }
    }

    companion object {
        const val OverworldSectionCount: Int = 24

        const val NetherSectionCount: Int = 16

        fun v1_18(sectionCount: Int = OverworldSectionCount): ChunkFormat =
            ChunkFormat(sectionCount, hasFluidCount = false, longArrayLengthPrefixed = true)

        fun v1_21_5(sectionCount: Int = OverworldSectionCount): ChunkFormat =
            ChunkFormat(sectionCount, hasFluidCount = false, longArrayLengthPrefixed = false)

        fun v26_1(sectionCount: Int = OverworldSectionCount): ChunkFormat =
            ChunkFormat(sectionCount, hasFluidCount = true, longArrayLengthPrefixed = false)
    }
}

enum class PaletteKind(
    val entryCount: Int,
    val maxIndirectBits: Int,
) {
    BlockStates(4096, 8),
    Biomes(64, 3),
}
