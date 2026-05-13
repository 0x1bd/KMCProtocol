package org.kvxd.kmcprotocol.core.encoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ProtocolValueSerializer::class)
class ProtocolValue(
    val value: Any? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtocolValue) return false
        return valuesEqual(value, other.value)
    }

    override fun hashCode(): Int = valueHash(value)

    override fun toString(): String = "ProtocolValue(value=${renderValue(value)})"

    companion object {
        fun obj(values: Map<String, ProtocolValue>) = ProtocolValue(values)
        fun list(values: List<ProtocolValue>) = ProtocolValue(values)
        fun bytes(values: ByteArray) = ProtocolValue(values)

        private fun valuesEqual(left: Any?, right: Any?): Boolean =
            when {
                left is ByteArray && right is ByteArray -> left.contentEquals(right)
                left is Number && right is Number -> left.toDouble() == right.toDouble()
                left is List<*> && right is List<*> -> left.size == right.size && left.zip(right).all { (a, b) -> valuesEqual(a, b) }
                left is Map<*, *> && right is Map<*, *> -> left.keys == right.keys && left.keys.all { valuesEqual(left[it], right[it]) }
                else -> left == right
            }

        private fun valueHash(value: Any?): Int =
            when (value) {
                is ByteArray -> value.contentHashCode()
                is List<*> -> value.fold(1) { acc, item -> 31 * acc + valueHash(item) }
                is Map<*, *> -> value.entries.fold(1) { acc, entry ->
                    31 * acc + (entry.key?.hashCode() ?: 0) xor valueHash(entry.value)
                }
                else -> value?.hashCode() ?: 0
            }

        private fun renderValue(value: Any?): String =
            when (value) {
                is ByteArray -> value.contentToString()
                else -> value.toString()
            }
    }
}

object ProtocolValueSerializer : KSerializer<ProtocolValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ProtocolValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ProtocolValue) {
        throw SerializationException("ProtocolValue must be encoded by MinecraftEncoder with @ProtocolType metadata.")
    }

    override fun deserialize(decoder: Decoder): ProtocolValue {
        throw SerializationException("ProtocolValue must be decoded by MinecraftDecoder with @ProtocolType metadata.")
    }
}
