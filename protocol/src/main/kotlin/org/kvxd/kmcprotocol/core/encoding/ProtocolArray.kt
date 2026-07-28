package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class ProtocolArray(
    val countTypes: Array<NumberEncoding>,
    val fixedCounts: IntArray,
    val countFields: Array<String>,
    val elementEncoding: NumberEncoding = NumberEncoding.VarInt,
)

internal class ArrayFrame(private val spec: ProtocolArray, val binary: ProtocolBinary? = null) {

    val elementEncoding: NumberEncoding get() = spec.elementEncoding
    private var depth = 0

    fun enter(): ArrayLevel? {
        val level = spec.countTypes.getOrNull(depth)?.let {
            ArrayLevel(it, spec.fixedCounts.getOrElse(depth) { -1 }, spec.countFields.getOrElse(depth) { "" })
        }
        depth += 1
        return level
    }

    fun exit() {
        if (depth > 0) depth -= 1
    }
}

internal class ArrayLevel(val countType: NumberEncoding, val fixedCount: Int, val countField: String) {
    val writesCount: Boolean
        get() = fixedCount < 0 && countField.isEmpty()
}
