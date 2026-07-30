package org.kvxd.kmcprotocol.core

class ProtocolLimits(
    val maxFrameLength: Int = DefaultMaxFrameLength,
    val maxUncompressedLength: Int = DefaultMaxUncompressedLength,
    val maxNbtDepth: Int = DefaultMaxNbtDepth,
    val maxCompressedNbtLength: Int = DefaultMaxCompressedNbtLength,
    val validateDecompressedSize: Boolean = false,
) {
    init {
        require(maxFrameLength in 1..MaxEncodableFrameLength) {
            "maxFrameLength must be in 1..$MaxEncodableFrameLength but was $maxFrameLength"
        }
        require(maxUncompressedLength >= 1) {
            "maxUncompressedLength must be positive but was $maxUncompressedLength"
        }
        require(maxNbtDepth >= 1) {
            "maxNbtDepth must be positive but was $maxNbtDepth"
        }
        require(maxCompressedNbtLength >= 1) {
            "maxCompressedNbtLength must be positive but was $maxCompressedNbtLength"
        }
    }

    companion object {
        const val MaxEncodableFrameLength: Int = 0x1FFFFF

        const val DefaultMaxFrameLength: Int = MaxEncodableFrameLength

        const val DefaultMaxUncompressedLength: Int = 8 * 1024 * 1024

        const val DefaultMaxNbtDepth: Int = 512

        const val DefaultMaxCompressedNbtLength: Int = 8 * 1024 * 1024

        val Default: ProtocolLimits = ProtocolLimits()
    }
}
