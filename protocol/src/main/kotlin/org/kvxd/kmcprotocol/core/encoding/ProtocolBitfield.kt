package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS)
annotation class ProtocolBitfield(val sizes: IntArray, val signed: BooleanArray)

internal class BitfieldLayout(val sizes: IntArray, val signed: BooleanArray) {

    val totalBits: Int = sizes.sum()

    fun pack(values: LongArray): Long {
        var packed = 0L
        var offset = totalBits
        for (index in sizes.indices) {
            val size = sizes[index]
            offset -= size
            packed = packed or ((values[index] and mask(size)) shl offset)
        }
        return packed
    }

    fun unpack(packed: Long): LongArray {
        var offset = totalBits
        return LongArray(sizes.size) { index ->
            val size = sizes[index]
            offset -= size
            val raw = (packed ushr offset) and mask(size)
            if (signed[index] && size < 64 && raw and (1L shl (size - 1)) != 0L) raw or (-1L shl size) else raw
        }
    }

    private fun mask(size: Int): Long = if (size == 64) -1L else (1L shl size) - 1
}

internal fun kotlinx.serialization.descriptors.SerialDescriptor.bitfieldLayout(): BitfieldLayout? =
    getAnnotation<ProtocolBitfield>()?.let { BitfieldLayout(it.sizes, it.signed) }
